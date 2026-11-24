package org.siloserver.silo.updates

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun laterSuppressesOnlyTheOfferedBuildUntilIntervalOrLaunch() {
        val policy = UpdateOfferPolicy()
        val now = 100L
        val snooze = TimeUnit.HOURS.toMillis(3)
        policy.later(5L, now)

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
    fun phoneAndTvUseDifferentVersionCodesForTheSameBuild() {
        assertEquals(400L, SiloraReleaseSource.versionFor(200, isTv = false))
        assertEquals(401L, SiloraReleaseSource.versionFor(200, isTv = true))
    }
}
