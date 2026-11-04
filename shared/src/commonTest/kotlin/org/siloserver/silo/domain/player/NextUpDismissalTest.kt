package org.siloserver.silo.domain.player

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextUpDismissalTest {
    @Test fun preEndDismissalAllowsOneEndCommit() {
        val state = NextUpDismissal()
        state.dismiss(automaticHandled = true, videoEnded = false)
        assertFalse(state.consumeAtEnd(false))
        assertTrue(state.consumeAtEnd(true))
        assertFalse(state.consumeAtEnd(true))
        state.dismiss(automaticHandled = true, videoEnded = true)
        assertFalse(state.consumeAtEnd(true))
    }
    @Test fun manualAndResetNeverArmAutoplay() {
        val state = NextUpDismissal()
        state.dismiss(automaticHandled = false, videoEnded = false)
        assertFalse(state.consumeAtEnd(true))
        state.dismiss(automaticHandled = true, videoEnded = false)
        state.reset()
        assertFalse(state.consumeAtEnd(true))
    }
}
