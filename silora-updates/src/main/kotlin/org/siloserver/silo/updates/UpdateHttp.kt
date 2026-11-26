package org.siloserver.silo.updates

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

internal class GitHubRetryDeferred(retryAt: Long) : IOException(
    "GitHub asked Silora to wait. Try again after ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(retryAt))}.",
)

internal class UpdateHttp(
    val retryAt: (String) -> Long,
    private val deferUntil: (String, Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()

    fun requireAvailable(host: String) {
        val until = retryAt(host)
        if (until > now()) throw GitHubRetryDeferred(until)
    }

    suspend fun <T> read(url: String, deadlineMillis: Long, consume: suspend (InputStream) -> T): T = coroutineScope {
        val request = Request.Builder().url(url).header("User-Agent", "Silora-Android-Updater").build()
        val host = request.url.host
        requireAvailable(host)
        val call = client.newCall(request)
        call.timeout().timeout(deadlineMillis, TimeUnit.MILLISECONDS)
        // Cancellation must close the socket to interrupt a blocking read.
        val cancelCall = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    val until = response.retryAt(now())
                    if (until != null) deferUntil(host, until)
                    if (!response.isSuccessful) {
                        if (until != null) throw GitHubRetryDeferred(until)
                        throw IOException("GitHub returned HTTP ${response.code}. Try again later.")
                    }
                    consume(checkNotNull(response.body).byteStream())
                }
            }
        } catch (error: IOException) {
            ensureActive()
            if (error is InterruptedIOException) {
                throw IOException("The update transfer timed out. Try again later.", error)
            }
            throw error
        } finally {
            cancelCall.cancel()
        }
    }
}

private fun Response.retryAt(now: Long): Long? {
    val exhausted = header("X-RateLimit-Remaining")?.trim() == "0"
    val reset = header("X-RateLimit-Reset")?.trim()?.toLongOrNull()
        ?.takeIf { it in 1..Long.MAX_VALUE / 1000 }
        ?.let(TimeUnit.SECONDS::toMillis)?.takeIf { exhausted && it > now }
    if (isSuccessful) return reset

    val retryAfter = header("Retry-After")?.trim()?.let { value ->
        value.toLongOrNull()?.takeIf { it in 0..(Long.MAX_VALUE - now) / 1000 }?.let {
            now + TimeUnit.SECONDS.toMillis(it)
        } ?: headers.getDate("Retry-After")?.time
    }?.takeIf { it > now }
    val rateLimited = code == 429 || code == 403 && (
        exhausted || retryAfter != null || peekBody(8192).string().contains("rate limit", ignoreCase = true)
    )
    return when {
        rateLimited -> maxOf(reset ?: 0, retryAfter ?: 0).takeIf { it > now } ?: now + 60_000L
        code == 503 -> retryAfter
        else -> null
    }
}
