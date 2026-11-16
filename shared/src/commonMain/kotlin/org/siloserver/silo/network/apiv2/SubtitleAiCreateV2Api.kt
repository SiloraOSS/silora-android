package org.siloserver.silo.network.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.siloserver.silo.model.subtitles.*
import org.siloserver.silo.network.*

@Serializable
private data class AiCreationReceipt(
    val job: AiJobV2,
    @SerialName("live_delivery_attached") val liveDeliveryAttached: Boolean,
)

/**
 * Foreground creation. The request names the playback session it came from, so the server
 * can attach the job to it and stream its live cues there; when the server can't find the
 * session, the job runs as a background job instead. Android polls persisted jobs to their
 * end and (fork) draws the live cues meanwhile.
 *
 * Fork: a request whose outcome is uncertain is fenced for [UNCERTAIN_RETRY_AFTER]. After
 * that, a repeat first looks for the job the lost request may have created ([aiReads]) and
 * adopts it; only when there is none is the request sent again.
 */
class SubtitleAiCreateV2Api(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val gate: ApiV2Gate,
    private val aiReads: SubtitleAiReadsV2Api? = null,
) {
    // Process-local fence, not an offline queue. A changed playhead/dialog must
    // not turn a lost creation receipt into a new request for the same source.
    private data class Intent(val owner: AuthScopeSnapshot, val file: Int, val kind: String,
        val source: Int, val sourceLanguage: String, val targetLanguage: String)
    private val mutex = Mutex()
    // Fork: an uncertain intent (lost reply, timeout, 5xx) used to stay fenced
    // until the app restarted, so one Wi-Fi blip refused that translation for
    // good. The server collapses an identical request onto a pending or
    // running job, but not onto one that already finished (and transcription
    // never reuses a result), so after the cooldown the job list is checked
    // first: a matching job is adopted, and only with none is it resent.
    private val unresolved = mutableMapOf<Intent, TimeSource.Monotonic.ValueTimeMark>()

    suspend fun create(request: SubtitleTranslateRequest, expected: AuthScopeSnapshot? = null): ApiResult<SubtitleAiJobResponse> {
        val owner = expected ?: tokens.snapshotCurrentScope() ?: return identityChanged()
        if (!owner.stillOwns(tokens, OwnerPolicy.PROFILE)) return identityChanged()
        val position = request.startPosition
        if (request.mediaFileId <= 0 || request.kind !in setOf("translate", "transcribe", "transcribe_translate") ||
            request.sourceIndex < -1 || position == null || !position.isFinite() || position < 0) {
            return ApiResult.Error(0, "invalid_subtitle_request", "The subtitle source or playback position is invalid.")
        }
        val intent = Intent(owner, request.mediaFileId, request.kind, request.sourceIndex,
            request.sourceLanguage.orEmpty(), request.targetLanguage.orEmpty())
        var retryOfUncertain = false
        val fenced = mutex.withLock {
            val since = unresolved[intent]
            retryOfUncertain = since != null
            if (since != null && since.elapsedNow() < UNCERTAIN_RETRY_AFTER) {
                true
            } else {
                unresolved[intent] = TimeSource.Monotonic.markNow()
                false
            }
        }
        if (fenced) return ApiResult.Error(0, "subtitle_creation_uncertain",
            "This subtitle request is pending or may have started. Try again in a minute if nothing appears.")
        if (retryOfUncertain && aiReads != null) {
            when (val lost = findLostJob(aiReads, request, owner)) {
                is LostJob.Found -> {
                    withContext(NonCancellable) { mutex.withLock { unresolved.remove(intent) } }
                    return ApiResult.Success(SubtitleAiJobResponse(lost.job, liveDeliveryAttached = false))
                }
                LostJob.Unknown -> return ApiResult.Error(0, "subtitle_creation_uncertain",
                    "This subtitle request may have started. Try again in a minute if nothing appears.")
                LostJob.None -> Unit
            }
        }
        // Whether the attempt in flight may have reached the server. Until one
        // has, no job can exist and the intent has nothing to fence.
        var dispatched = false
        suspend fun send(sessionId: String?) =
            ownedV2Call<AiCreationReceipt, SubtitleAiJobResponse>(gate, tokens, owner, OwnerPolicy.PROFILE, HttpStatusCode.Accepted, { pinned ->
                dispatched = true
                client.post("/api/v2/subtitles/ai/translate") {
                    authScope(pinned!!); requireSiloAuth(); singleAttempt()
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("media_file_id", request.mediaFileId.toString())
                        put("kind", request.kind)
                        put("source_index", request.sourceIndex)
                        put("source_language", request.sourceLanguage.orEmpty())
                        put("target_language", request.targetLanguage.orEmpty())
                        put("start_position", position)
                        if (sessionId != null) put("session_id", sessionId)
                    })
                }
            }) { receipt ->
                val job = receipt.job.project()
                require(job.mediaFileId == request.mediaFileId && job.kind == request.kind && job.sourceIndex == request.sourceIndex) {
                    "The server returned an unsupported subtitle creation receipt."
                }
                SubtitleAiJobResponse(job, receipt.liveDeliveryAttached)
            }
        var settled = false
        try {
            val sessionId = request.sessionId?.takeIf { it.isNotBlank() }
            var result = send(sessionId)
            // The server binds the session before it creates a job, so a 404
            // created nothing. When it can't find the session (it ended, or
            // another API replica holds it), resend without one; the job then
            // runs as a background job.
            if (sessionId != null && result is ApiResult.Error && result.code == 404) {
                dispatched = false
                result = send(null)
            }
            return when (result) {
                is ApiResult.Success -> { settled = true; result }
                is ApiResult.Error -> {
                    // Definite request refusals can be corrected by a new user
                    // decision. Timeout, server errors and malformed replies cannot.
                    settled = result.code in 400..499 && result.code != 408
                    result
                }
                is ApiResult.NetworkError -> ApiResult.Error(0, "subtitle_creation_uncertain", "The request may have started. It has not been sent again.")
            }
        } finally {
            if (settled || !dispatched) withContext(NonCancellable) { mutex.withLock { unresolved.remove(intent) } }
        }
    }

    private sealed interface LostJob {
        data class Found(val job: SubtitleAiJob) : LostJob
        data object None : LostJob
        /** The job list could not be read, so a resend could duplicate the job. */
        data object Unknown : LostJob
    }

    private suspend fun findLostJob(
        reads: SubtitleAiReadsV2Api,
        request: SubtitleTranslateRequest,
        owner: AuthScopeSnapshot,
    ): LostJob {
        val listed = reads.jobs(request.mediaFileId, owner)
        if (listed !is ApiResult.Success) return LostJob.Unknown
        return lostJobFor(request, listed.data.jobs)?.let(LostJob::Found) ?: LostJob.None
    }

    internal companion object {
        val UNCERTAIN_RETRY_AFTER = 60.seconds

        /**
         * The job a lost request for [request] most likely created: the newest
         * job (highest id) of the same kind, source and target, if it is still
         * running or completed with a result. A failed or cancelled newest
         * job means a new request is wanted.
         */
        internal fun lostJobFor(request: SubtitleTranslateRequest, jobs: List<SubtitleAiJob>): SubtitleAiJob? {
            val target = request.targetLanguage.orEmpty().trim()
            val newest = jobs
                .filter { job ->
                    job.mediaFileId == request.mediaFileId &&
                        job.kind == request.kind &&
                        job.sourceIndex == request.sourceIndex &&
                        (target.isEmpty() || job.targetLanguage.trim().equals(target, ignoreCase = true))
                }
                .maxByOrNull { it.id }
                ?: return null
            return when {
                !newest.isTerminal -> newest
                newest.status == SubtitleAiJobStatus.Completed -> newest.takeIf { it.resultSubtitleId != null }
                else -> null
            }
        }
    }
}
