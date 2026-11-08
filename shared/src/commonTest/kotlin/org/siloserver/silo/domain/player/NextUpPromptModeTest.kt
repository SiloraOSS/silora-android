package org.siloserver.silo.domain.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextUpPromptModeTest {
    @Test fun savedCreditsChoiceAndDefaultUseCreditsMarkers() {
        assertEquals(NextUpPromptMode.CreditsMarkers, NextUpPromptMode.fromWire("credits"))
        assertEquals(NextUpPromptMode.CreditsMarkers, NextUpPromptMode.fromWire(null))
        assertEquals(NextUpPromptMode.CreditsMarkers, NextUpPromptMode.fromWire("unknown"))
    }

    @Test fun savedChoicesKeepTheirWireValues() {
        assertEquals(NextUpPromptMode.AfterPlaybackEnds, NextUpPromptMode.fromWire("off"))
        assertEquals(NextUpPromptMode.TimeRemaining, NextUpPromptMode.fromWire("time_remaining"))
    }

    @Test fun earlyCreditsUseFallbackAndEligibleCreditsDoNotNeedTiming() {
        assertEquals(1790.0, NextUpPromptMode.CreditsMarkers.promptStartPosition(88.0, 1800.0, 10))
        assertNull(NextUpPromptMode.CreditsMarkers.promptStartPosition(88.0, 1800.0, 0))
        assertEquals(1700.0, NextUpPromptMode.CreditsMarkers.promptStartPosition(1700.0, 1800.0, null))
        assertNull(NextUpPromptMode.CreditsMarkers.promptStartPosition(1700.0, Double.NaN, 10))
    }

    @Test fun timeRemainingIgnoresAnEarlyCreditsMarker() {
        assertEquals(1790.0, NextUpPromptMode.TimeRemaining.promptStartPosition(88.0, 1800.0, 10))
        assertEquals(1770.0, NextUpPromptMode.TimeRemaining.promptStartPosition(88.0, 1800.0, 30))
    }

    @Test fun missingCreditsUseTheSameTimerAsTimeRemaining() {
        assertEquals(1790.0, NextUpPromptMode.CreditsMarkers.promptStartPosition(null, 1800.0, 10))
        assertEquals(1790.0, NextUpPromptMode.TimeRemaining.promptStartPosition(null, 1800.0, 10))
    }

    @Test fun afterPlaybackEndsSuppressesBothKindsOfPreEndTrigger() {
        assertNull(NextUpPromptMode.AfterPlaybackEnds.promptStartPosition(88.0, 1800.0, 10))
        assertNull(NextUpPromptMode.AfterPlaybackEnds.promptStartPosition(null, 1800.0, 10))
    }

    @Test fun timersWaitForSavedTimingAndAKnownDurationAndRespectAtEnd() {
        for (mode in listOf(NextUpPromptMode.CreditsMarkers, NextUpPromptMode.TimeRemaining)) {
            assertNull(mode.promptStartPosition(null, 1800.0, null))
            assertNull(mode.promptStartPosition(null, 1800.0, 0))
            assertNull(mode.promptStartPosition(null, 0.0, 10))
            assertNull(mode.promptStartPosition(null, Double.NaN, 10))
            assertNull(mode.promptStartPosition(null, Double.POSITIVE_INFINITY, 10))
            assertNull(mode.promptStartPosition(null, 5.0, 10))
        }
    }
}
