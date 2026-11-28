package org.siloserver.silo.updates

import androidx.media3.common.Player
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class UpdatePolicyTest {
    private val pacific = TimeZone.getTimeZone("America/Los_Angeles")

    @Test
    fun newSettingsDefaultToTwelveHoursAndSavedIntervalsKeepTheirMeaning() {
        assertEquals(UpdateCheckInterval.EVERY_12_HOURS, UpdateSettings().interval)
        assertEquals(UpdateCheckInterval.EVERY_24_HOURS, UpdateCheckInterval.fromSavedHours(24))
        assertEquals(UpdateCheckInterval.OFF, UpdateCheckInterval.fromSavedHours(0))
    }

    @Test
    fun offAndLaunchOnlyKeepDistinctCheckPoliciesWithoutEnablingBackgroundWork() {
        val now = TimeUnit.HOURS.toMillis(24)
        val off = UpdateCheckInterval.fromSavedHours(0)
        val launchOnly = UpdateCheckInterval.fromSavedHours(0, checkOnLaunch = true)

        assertEquals(UpdateCheckInterval.OFF, off)
        assertEquals(UpdateCheckInterval.ON_LAUNCH, launchOnly)
        assertFalse(off.enabled)
        assertFalse(off.shouldCheckOnLaunch(0, now))
        assertFalse(launchOnly.enabled)
        assertTrue(launchOnly.shouldCheckOnLaunch(now - 1, now))
        assertFalse(UpdateSettings(interval = launchOnly, automatic = true).normalized().automatic)
        assertEquals(UpdateCheckInterval.EVERY_12_HOURS, UpdateCheckInterval.fromSavedHours(12, checkOnLaunch = true))
        assertFalse(UpdateCheckInterval.EVERY_12_HOURS.shouldCheckOnLaunch(now - 1, now))
        assertTrue(UpdateCheckInterval.EVERY_12_HOURS.shouldCheckOnLaunch(now - TimeUnit.HOURS.toMillis(12), now))
    }

    @Test
    fun automaticInstallNotificationsDefaultOffAndKeepTheirOptInWhenTheScheduleIsDisabled() {
        assertFalse(UpdateSettings().notifyOnAutomaticInstall)
        val settings = UpdateSettings(
            interval = UpdateCheckInterval.OFF,
            automatic = true,
            notifyOnAutomaticInstall = true,
        )
        assertTrue(settings.normalized().notifyOnAutomaticInstall)
    }

    @Test
    fun turningOffBackgroundChecksClearsAutomaticInstallation() {
        val enabled = UpdateSettings(
            interval = UpdateCheckInterval.CUSTOM,
            automatic = true,
            method = InstallMethod.SHIZUKU,
            checkHour = 7,
        )
        assertEquals(enabled, enabled.normalized())

        val disabled = enabled.copy(interval = UpdateCheckInterval.OFF).normalized()
        assertEquals(enabled.copy(interval = UpdateCheckInterval.OFF, automatic = false), disabled)
        assertEquals(enabled.copy(automatic = false), disabled.copy(interval = enabled.interval).normalized())
    }

    @Test
    fun dailyCheckReturnsToTheChosenLocalHourAfterALateRun() {
        val beforeCheck = Instant.parse("2026-01-10T10:30:00Z").toEpochMilli()
        val lateCheck = Instant.parse("2026-01-10T13:45:00Z").toEpochMilli()

        assertEquals(Instant.parse("2026-01-10T11:00:00Z").toEpochMilli(), nextDailyCheck(3, beforeCheck, pacific))
        assertEquals(Instant.parse("2026-01-11T11:00:00Z").toEpochMilli(), nextDailyCheck(3, lateCheck, pacific))
    }

    @Test
    fun dailyCheckKeepsTheChosenHourAcrossDaylightSavingChanges() {
        val beforeMissingHour = Instant.parse("2026-03-08T09:00:00Z").toEpochMilli()
        val afterMissingHour = Instant.parse("2026-03-08T10:30:00Z").toEpochMilli()
        val afterFirstOccurrence = Instant.parse("2026-11-01T08:30:00Z").toEpochMilli()

        assertEquals(Instant.parse("2026-03-08T10:00:00Z").toEpochMilli(), nextDailyCheck(2, beforeMissingHour, pacific))
        assertEquals(Instant.parse("2026-03-09T09:00:00Z").toEpochMilli(), nextDailyCheck(2, afterMissingHour, pacific))
        assertEquals(Instant.parse("2026-11-01T09:00:00Z").toEpochMilli(), nextDailyCheck(1, afterFirstOccurrence, pacific))
    }

    @Test
    fun dismissingAnOfferHidesOnlyThatBuildUntilIntervalOrLaunch() {
        val policy = UpdateOfferPolicy()
        val now = 100L
        val snooze = TimeUnit.HOURS.toMillis(3)
        policy.dismiss(5L, now)

        assertFalse(policy.visible(5L, 3, now + snooze - 1))
        assertTrue(policy.visible(7L, 3, now))
        assertTrue(policy.visible(5L, 3, now + snooze))
        assertFalse(policy.visible(5L, 0, now + snooze))

        policy.launch()
        assertTrue(policy.visible(5L, 0, now))
    }

    @Test
    fun playbackGuardIncludesBufferingAndAllowsPause() {
        assertEquals(SiloraPlaybackStatus.PLAYING, SiloraMediaSession.status(Player.STATE_READY, true))
        assertEquals(SiloraPlaybackStatus.PLAYING, SiloraMediaSession.status(Player.STATE_BUFFERING, true))
        assertEquals(SiloraPlaybackStatus.IDLE, SiloraMediaSession.status(Player.STATE_READY, false))
        assertEquals(SiloraPlaybackStatus.IDLE, SiloraMediaSession.status(Player.STATE_ENDED, false))
    }

    @Test
    fun automaticInstallsWaitByDefaultAndKeepTheOptOutWhenAutomaticInstallsAreDisabled() {
        assertTrue(UpdateSettings().waitUntilPlaybackStops)
        val optedOut = UpdateSettings(automatic = true, waitUntilPlaybackStops = false)

        assertFalse(optedOut.copy(automatic = false).waitUntilPlaybackStops)
        assertFalse(optedOut.copy(interval = UpdateCheckInterval.OFF).normalized().waitUntilPlaybackStops)
    }

    @Test
    fun bothAutomaticInstallersHonorPlaybackProtection() = runBlocking<Unit> {
        for (method in InstallMethod.entries) {
            val settings = UpdateSettings(automatic = true, method = method)
            for (status in SiloraPlaybackStatus.entries) {
                val playback = SiloraPlaybackReader { status }
                val expected = if (status == SiloraPlaybackStatus.IDLE) null else UpdateStatus.WAITING_FOR_PLAYBACK
                assertEquals(expected, playback.automaticInstallBlock(method, { settings }, { true }))
            }

            val optedOut = settings.copy(waitUntilPlaybackStops = false)
            val playback = SiloraPlaybackReader { error("Playback should not be read after opting out") }
            assertNull(playback.automaticInstallBlock(method, { optedOut }, { true }))
        }
    }

    @Test
    fun playbackOptOutStillRequiresAutomaticInstallationAndStandardPermission() = runBlocking<Unit> {
        val settings = UpdateSettings(automatic = true, waitUntilPlaybackStops = false)
        val playback = SiloraPlaybackReader { error("Playback should not be read after opting out") }

        assertEquals(
            UpdateStatus.NEEDS_INSTALL_PERMISSION,
            playback.automaticInstallBlock(InstallMethod.STANDARD, { settings }, { false }),
        )
        assertEquals(
            UpdateStatus.AVAILABLE,
            playback.automaticInstallBlock(InstallMethod.STANDARD, { settings.copy(automatic = false) }, { true }),
        )
        assertEquals(
            UpdateStatus.AVAILABLE,
            playback.automaticInstallBlock(InstallMethod.SHIZUKU, { settings }, { true }),
        )
    }

    @Test
    fun turningOffPlaybackProtectionWhileCheckingTheSessionLiftsTheBlock() = runBlocking<Unit> {
        var settings = UpdateSettings(automatic = true)
        val playback = SiloraPlaybackReader {
            settings = settings.copy(waitUntilPlaybackStops = false)
            SiloraPlaybackStatus.PLAYING
        }

        assertNull(playback.automaticInstallBlock(InstallMethod.STANDARD, { settings }, { true }))
    }

    @Test
    fun phoneAndTvUseDifferentVersionCodesForTheSameBuild() {
        assertEquals(400L, SiloraReleaseSource.versionFor(200, isTv = false))
        assertEquals(401L, SiloraReleaseSource.versionFor(200, isTv = true))
    }
}
