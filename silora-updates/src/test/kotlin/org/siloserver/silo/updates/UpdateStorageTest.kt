package org.siloserver.silo.updates

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class UpdateStorageTest {
    @get:Rule
    val directory = TemporaryFolder()

    private val source = SiloraReleaseSource(isTv = true)
    private val apkBytes = byteArrayOf(1, 2, 3)
    private val checksum = MessageDigest.getInstance("SHA-256").digest(apkBytes)
        .joinToString("") { "%02x".format(it) }
    private val release = SiloraRelease("fixture", "Fixture", 403L, "unused", apkBytes.size.toLong(), checksum)

    @Test
    fun removesObsoleteFilesBeforeCheckingDownloadSpace() = runBlocking<Unit> {
        val target = directory.newFile("update.apk").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val partial = directory.newFile("update.apk.part")
        val noSpace = InsufficientUpdateStorage()

        val error = runCatching {
            source.download(release, target) { path, bytes ->
                assertFalse(target.exists())
                assertFalse(partial.exists())
                assertTrue(path == directory.root && bytes == release.bytes)
                throw noSpace
            }
        }.exceptionOrNull()

        assertSame(noSpace, error)
    }

    @Test
    fun removesStalePartialEvenWhenItReusesTheVerifiedApk() = runBlocking<Unit> {
        val target = directory.newFile("update.apk").apply { writeBytes(apkBytes) }
        val partial = directory.newFile("update.apk.part")

        source.download(release, target) { _, _ -> error("A cache hit needs no new download space") }

        assertArrayEquals(apkBytes, target.readBytes())
        assertFalse(partial.exists())
    }

    @Test
    fun cacheEvictionDuringVerificationFallsBackToTheDownloadSpaceCheck() = runBlocking<Unit> {
        val target = directory.newFile("update.apk").apply { writeBytes(apkBytes) }
        val evicted = object : File(target.path) {
            override fun length(): Long = super.length().also { delete() }
        }
        val noSpace = InsufficientUpdateStorage()

        val error = runCatching { source.download(release, evicted) { _, _ -> throw noSpace } }.exceptionOrNull()

        assertSame(noSpace, error)
    }

    @Test
    fun distinguishesDiskFullFromAnInterruptedTransfer() {
        assertTrue(IOException("write failed: No space left on device").isInsufficientUpdateStorage())
        assertTrue(isStorageFailureMessage("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertFalse(IOException("Connection reset").isInsufficientUpdateStorage())
    }
}
