package org.siloserver.silo.playback

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.siloserver.silo.network.PlaybackRealtimeEvent

/**
 * Fork: live AI subtitles.
 *
 * When an AI translation or transcription job is attached to a playback
 * session, the server streams its output over the session's realtime socket
 * as it is produced, starting at the playhead: `subtitle_translation_started`,
 * then `subtitle_translation_cues` batches, then `subtitle_translation_completed`
 * or `subtitle_translation_failed`. Cue times are source (file) seconds.
 */
data class LiveSubtitleCue(val startSeconds: Double, val endSeconds: Double, val text: String)

sealed interface PlaybackLiveSubtitleEvent {
    val sessionId: String?
    val fileId: Int?
    val jobId: Long
    val trackKey: String

    data class Started(
        override val sessionId: String?,
        override val fileId: Int?,
        override val jobId: Long,
        override val trackKey: String,
        val language: String,
        val label: String?,
        val totalCues: Int,
    ) : PlaybackLiveSubtitleEvent

    data class Cues(
        override val sessionId: String?,
        override val fileId: Int?,
        override val jobId: Long,
        override val trackKey: String,
        val cues: List<LiveSubtitleCue>,
        val done: Int,
        val total: Int,
    ) : PlaybackLiveSubtitleEvent

    data class Completed(
        override val sessionId: String?,
        override val fileId: Int?,
        override val jobId: Long,
        override val trackKey: String,
        val subtitleId: Int?,
    ) : PlaybackLiveSubtitleEvent

    data class Failed(
        override val sessionId: String?,
        override val fileId: Int?,
        override val jobId: Long,
        override val trackKey: String,
        val message: String?,
    ) : PlaybackLiveSubtitleEvent
}

/** The live track one job is filling, kept sorted by start time. */
data class LiveSubtitleTrack(
    val sessionId: String?,
    val jobId: Long,
    val trackKey: String,
    val language: String = "",
    val label: String? = null,
    val cues: List<LiveSubtitleCue> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val completed: Boolean = false,
    /** A plain transcription (no translation): the waiting text says so. */
    val transcribing: Boolean = false,
) {
    /** Adds a batch, dropping exact repeats (a resent batch after a reconnect). */
    fun plus(batch: List<LiveSubtitleCue>, done: Int, total: Int): LiveSubtitleTrack {
        if (batch.isEmpty()) return copy(done = maxOf(this.done, done), total = maxOf(this.total, total))
        val merged = (cues + batch).distinct().sortedWith(compareBy({ it.startSeconds }, { it.endSeconds }))
        return copy(cues = merged, done = maxOf(this.done, done), total = maxOf(this.total, total))
    }

    /**
     * Whether the live lines own the subtitle slot at [seconds]: a live cue
     * shows there or one ends or starts within [gapSeconds]. The short pause
     * between two live cues still counts, so the player's own subtitles do
     * not flash up between lines; a region the job has not reached yet does not.
     */
    fun covers(seconds: Double, gapSeconds: Double = LIVE_SUBTITLE_COVER_GAP_SECONDS): Boolean {
        if (cues.isEmpty()) return false
        // First cue that starts after `seconds + gap`; only cues before it can
        // be near enough.
        val limit = seconds + gapSeconds
        var lo = 0
        var hi = cues.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startSeconds <= limit) lo = mid + 1 else hi = mid
        }
        var i = lo - 1
        var scanned = 0
        while (i >= 0 && scanned < 8) {
            if (cues[i].endSeconds + gapSeconds > seconds) return true
            i--
            scanned++
        }
        return false
    }

    /** Text of every cue showing at [seconds] (source time), in start order. */
    fun textAt(seconds: Double): List<String> {
        if (cues.isEmpty()) return emptyList()
        // Binary search for the first cue that starts after `seconds`; only
        // cues before it can be showing.
        var lo = 0
        var hi = cues.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startSeconds <= seconds) lo = mid + 1 else hi = mid
        }
        val showing = ArrayList<String>(2)
        var i = lo - 1
        // Overlapping cues are rare; look back a few for long ones.
        var scanned = 0
        while (i >= 0 && scanned < 8) {
            val cue = cues[i]
            if (cue.endSeconds > seconds && cue.text.isNotBlank()) showing.add(0, cue.text)
            i--
            scanned++
        }
        return showing
    }
}

/** How long a pause between live cues still keeps the player's own subtitles hidden. */
const val LIVE_SUBTITLE_COVER_GAP_SECONDS = 4.0

private val LIVE_SUBTITLE_EVENTS = setOf(
    "subtitle_translation_started",
    "subtitle_translation_cues",
    "subtitle_translation_completed",
    "subtitle_translation_failed",
)

fun isPlaybackLiveSubtitleEvent(name: String): Boolean = name in LIVE_SUBTITLE_EVENTS

/** Null when the event isn't a live-subtitle event or is malformed. */
fun decodePlaybackLiveSubtitleEvent(event: PlaybackRealtimeEvent.ServerEvent): PlaybackLiveSubtitleEvent? =
    decodePlaybackLiveSubtitleEvent(event.name, event.payload)

fun decodePlaybackLiveSubtitleEvent(name: String, payload: JsonObject): PlaybackLiveSubtitleEvent? {
    fun str(key: String) = (payload[key] as? JsonPrimitive)?.contentOrNull
    fun int(key: String) = (payload[key] as? JsonPrimitive)?.intOrNull
    val sessionId = str("session_id")
    val fileId = int("file_id")
    val jobId = (payload["job_id"] as? JsonPrimitive)?.longOrNull ?: return null
    val trackKey = str("track_key").orEmpty()
    return when (name) {
        "subtitle_translation_started" -> PlaybackLiveSubtitleEvent.Started(
            sessionId, fileId, jobId, trackKey,
            language = str("language").orEmpty(),
            label = str("label")?.takeIf { it.isNotBlank() },
            totalCues = int("total_cues") ?: 0,
        )
        "subtitle_translation_cues" -> PlaybackLiveSubtitleEvent.Cues(
            sessionId, fileId, jobId, trackKey,
            cues = (payload["cues"] as? JsonArray).orEmpty().mapNotNull { element ->
                val cue = element as? JsonObject ?: return@mapNotNull null
                val start = (cue["start"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val end = (cue["end"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val text = (cue["text"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (!start.isFinite() || !end.isFinite() || end <= start) return@mapNotNull null
                LiveSubtitleCue(start, end, liveCueDisplayText(text))
            },
            done = int("done") ?: 0,
            total = int("total") ?: 0,
        )
        "subtitle_translation_completed" -> PlaybackLiveSubtitleEvent.Completed(
            sessionId, fileId, jobId, trackKey,
            subtitleId = int("subtitle_id"),
        )
        "subtitle_translation_failed" -> PlaybackLiveSubtitleEvent.Failed(
            sessionId, fileId, jobId, trackKey,
            message = str("message")?.takeIf { it.isNotBlank() },
        )
        else -> null
    }
}

// Tags such as <i>, </b>, <font color="...">, WebVTT <c.yellow> and <v Name>.
private val LIVE_CUE_TAG = Regex("""</?[A-Za-z][^<>]*>""")
// WebVTT karaoke timestamps such as <00:01:02.500>.
private val LIVE_CUE_TIMESTAMP = Regex("""<\d[\d:.]*>""")
// ASS override blocks such as {\an8} or {\i1}.
private val LIVE_CUE_ASS_OVERRIDE = Regex("""\{\\[^}]*\}""")

/**
 * Fork: the text to draw for a live cue. The server streams the source
 * subtitle's lines verbatim, so SRT/WebVTT tags and ASS overrides would show
 * literally; they are removed here (styling such as italics is dropped), ASS
 * line breaks become newlines and the common HTML entities are decoded.
 */
fun liveCueDisplayText(raw: String): String {
    if (raw.isEmpty()) return raw
    return raw
        .replace(LIVE_CUE_ASS_OVERRIDE, "")
        .replace(LIVE_CUE_TAG, "")
        .replace(LIVE_CUE_TIMESTAMP, "")
        .replace("\\N", "\n")
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .lines()
        .joinToString("\n") { it.trim() }
        .trim('\n')
}

/**
 * Fork: AI jobs whose live lines must not be drawn any more: the viewer
 * cancelled the job, picked another subtitle track or Off while it ran, or
 * its poll already reached a terminal state. Late batches for these jobs are
 * dropped instead of bringing the overlay back. [restore] undoes it when the
 * same job is started again (the server collapses a repeat request onto the
 * running job). Keeps only the most recent [capacity] ids.
 */
class LiveSubtitleDismissals(private val capacity: Int = 16) {
    private val ids = ArrayDeque<Long>()

    fun dismiss(jobId: Long) {
        if (jobId in ids) return
        ids.addLast(jobId)
        while (ids.size > capacity) ids.removeFirst()
    }

    fun isDismissed(jobId: Long): Boolean = jobId in ids

    fun restore(jobId: Long) {
        ids.remove(jobId)
    }
}
