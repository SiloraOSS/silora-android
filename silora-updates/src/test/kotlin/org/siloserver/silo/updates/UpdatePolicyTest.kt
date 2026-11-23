package org.siloserver.silo.updates

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UpdatePolicyTest {
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
