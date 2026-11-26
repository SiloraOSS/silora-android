package org.siloserver.silo.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellPackageInstallerTest {
    private val events = mutableListOf<String>()
    private val installer = ShellPackageInstaller { arguments, input ->
        val command = arguments.first()
        events += command
        when (command) {
            "install-create" -> "Success: created install session [42]"
            "install-write" -> {
                assertEquals(listOf(1, 2, 3), checkNotNull(input).readBytes().map(Byte::toInt))
                "Success"
            }
            "install-commit", "install-abandon" -> "Success"
            else -> error("Unexpected command: $command")
        }
    }

    @Test
    fun checksAfterWritingAndAbandonsWhenPlaybackBlocksTheCommit() {
        val submitted = installer.install(listOf(apk()), SILORA_PACKAGE, 0) {
            events += "check"
            assertEquals("install-write", events[events.lastIndex - 1])
            false
        }

        assertFalse(submitted)
        assertEquals(listOf("install-create", "install-write", "check", "install-abandon"), events)
    }

    @Test
    fun submitsAfterTheCommitCheckApprovesTheStagedSession() {
        val submitted = installer.install(listOf(apk()), SILORA_PACKAGE, 0) {
            events += "check"
            true
        }

        assertTrue(submitted)
        assertEquals(listOf("install-create", "install-write", "check", "install-commit"), events)
    }

    private fun apk() = InstallSource("base.apk", 3L, byteArrayOf(1, 2, 3).inputStream())
}
