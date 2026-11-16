package org.siloserver.silo.network.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.siloserver.silo.admin.AdminOperation
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.authScope
import org.siloserver.silo.network.requireSiloAuth

/** A successful admin call: status, parsed body (null when empty) and the record's version tag. */
data class AdminResponse(val status: Int, val body: JsonElement?, val etag: String?)

/**
 * Fork: one generic call for any admin [AdminOperation] in the bundled spec.
 *
 * Bound to the account (not a profile): the server takes admin calls on the
 * account's primary profile only, so the console is offered only there.
 */
class AdminV2Api(
    private val client: HttpClient,
    private val tokens: TokenManager,
    private val gate: ApiV2Gate,
) {
    /**
     * A query value with several lines is sent as one parameter per line
     * (an array parameter such as `content_ids`). [onRefusal] sees the
     * headers of a refused call, e.g. a stale command's latest sequence.
     */
    suspend fun call(
        operation: AdminOperation,
        pathValues: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
        body: JsonElement? = null,
        ifMatch: String? = null,
        cursor: String? = null,
        onRefusal: ((status: Int, headers: Headers) -> Unit)? = null,
    ): ApiResult<AdminResponse> {
        gate.blocked()?.let { return it }
        val owner = tokens.snapshotCurrentScope() ?: return identityChanged()
        val path = fillPath(operation, pathValues)
            ?: return ApiResult.Error(0, "invalid_admin_request", "Some required path values are missing.")
        return try {
            val response = client.request(path) {
                method = HttpMethod.parse(operation.method)
                authScope(owner)
                requireSiloAuth()
                query.forEach { (k, v) ->
                    v.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { parameter(k, it) }
                }
                cursor?.let { parameter("cursor", it) }
                ifMatch?.let { header(HttpHeaders.IfMatch, it) }
                if (body != null) setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
            currentCoroutineContext().ensureActive()
            if (!owner.stillOwns(tokens, OwnerPolicy.IDENTITY)) return identityChanged()
            if (!response.status.isSuccess()) {
                onRefusal?.invoke(response.status.value, response.headers)
                return response.toApiV2Error()
            }
            val type = response.contentType()?.withoutParameters()
            val parsed = if (type != null && !isTextual(type)) {
                // A download the console can't show; don't pull it into memory.
                val size = response.contentLength()?.let { " of $it bytes" }.orEmpty()
                JsonPrimitive("Binary response$size ($type), not shown.")
            } else {
                val text = response.bodyAsText()
                if (text.isBlank()) null else {
                    runCatching { SiloJson.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) }
                }
            }
            ApiResult.Success(AdminResponse(response.status.value, parsed, response.headers[HttpHeaders.ETag]))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    companion object {
        private fun isTextual(type: ContentType): Boolean =
            type.contentType == "text" || type.contentSubtype == "json" || type.contentSubtype.endsWith("+json")

        /** `/api/v2/admin/users/{id}` + `{id=5}` → `/api/v2/admin/users/5`; null if a value is missing. */
        fun fillPath(operation: AdminOperation, values: Map<String, String>): String? {
            var missing = false
            val filled = Regex("""\{([^}]+)\}""").replace(operation.path) { m ->
                val value = values[m.groupValues[1]]
                if (value.isNullOrBlank()) { missing = true; "" } else value.encodeURLPathPart()
            }
            return filled.takeUnless { missing }
        }
    }
}
