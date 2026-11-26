package org.siloserver.silo.updates

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

class UpdateHttpTest {
    @get:Rule
    val server = MockWebServer()

    @get:Rule
    val directory = TemporaryFolder()

    private var now = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
    private val deferred = mutableMapOf<String, Long>()
    private val host get() = server.hostName
    private val url get() = server.url("/updates").toString()
    private val http = newTransport()

    private fun newTransport() = UpdateHttp(
        retryAt = { deferred[it] ?: 0L },
        deferUntil = { key, until -> deferred[key] = until },
        now = { now },
    )

    private suspend fun read(transport: UpdateHttp = http, deadline: Long = 5_000L): String =
        transport.read(url, deadline) { it.readBytes().decodeToString() }

    @Test
    fun activeSlowTransferTimesOutAndASecondRequestCanSucceed() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("abcdefghij").throttleBody(1, 200, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody("ok"))

        val failure = runCatching { read(deadline = 450) }.exceptionOrNull()

        assertTrue(failure is IOException && failure.message.orEmpty().contains("timed out"))
        assertEquals("ok", read())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun cancelledApkDownloadReleasesThePartialFileBeforeItCanBeRetried() = runBlocking<Unit> {
        val bytes = ByteArray(128) { it.toByte() }
        val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val release = SiloraRelease("fixture", "Fixture", 403, url, bytes.size.toLong(), checksum)
        val source = SiloraReleaseSource(isTv = true, http = http)
        val target = File(directory.root, "update.apk")
        val partial = File(directory.root, "update.apk.part")
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).throttleBody(1, 200, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))

        val download = launch { source.download(release, target) { _, _ -> } }
        withTimeout(5_000) {
            while (partial.length() == 0L) delay(10)
            download.cancelAndJoin()
        }
        assertFalse(partial.exists())
        assertFalse(target.exists())

        source.download(release, target) { _, _ -> }
        assertArrayEquals(bytes, target.readBytes())
        assertFalse(partial.exists())

        deferred[host] = now + 60_000L
        source.download(release, target) { _, _ -> error("The verified APK should be reused") }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun retryAfterAndPrimaryResetUseTheLaterDeadlineAcrossTransports() = runBlocking<Unit> {
        val until = now + 150_000L
        server.enqueue(MockResponse().setResponseCode(403)
            .setHeader("Retry-After", "150")
            .setHeader("X-RateLimit-Remaining", "0")
            .setHeader("X-RateLimit-Reset", (now + 120_000L) / 1000))
        server.enqueue(MockResponse().setBody("ok"))

        assertTrue(runCatching { read() }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(until, deferred[host])

        now = until - 1
        val recreated = newTransport()
        assertTrue(runCatching { read(recreated) }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(1, server.requestCount)
        now = until
        assertEquals("ok", read(recreated))
    }

    @Test
    fun retryAfterDateIsHonoredForTemporaryUnavailability() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(503)
            .setHeader("Retry-After", "Thu, 01 Jan 2026 00:03:00 GMT"))

        assertTrue(runCatching { read() }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(now + 180_000L, deferred[host])
    }

    @Test
    fun headerlessRateLimitsWaitAtLeastAMinute() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(429))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"You hit a secondary rate limit."}"""))

        assertTrue(runCatching { read() }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(now + 60_000L, deferred[host])
        now += 60_000L
        assertTrue(runCatching { read() }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(now + 60_000L, deferred[host])
    }

    @Test
    fun anOrdinaryForbiddenResponseDoesNotStartACooldown() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val failure = runCatching { read() }.exceptionOrNull()
        assertTrue(failure is IOException && failure.message.orEmpty().contains("HTTP 403"))
        assertTrue(deferred.isEmpty())
    }

    @Test
    fun lastAllowedRequestStillReturnsItsBodyAndDefersTheNextRequest() = runBlocking<Unit> {
        val until = now + 150_000L
        server.enqueue(MockResponse().setBody("ok")
            .setHeader("X-RateLimit-Remaining", "0")
            .setHeader("X-RateLimit-Reset", until / 1000))

        assertEquals("ok", read())
        assertEquals(until, deferred[host])
        assertTrue(runCatching { read() }.exceptionOrNull() is GitHubRetryDeferred)
        assertEquals(1, server.requestCount)
    }
}
