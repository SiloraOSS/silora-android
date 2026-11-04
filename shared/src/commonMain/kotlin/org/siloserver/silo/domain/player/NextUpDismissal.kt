package org.siloserver.silo.domain.player

/** Tracks only a dismissed automatic pre-end prompt, separately from manual actions. */
class NextUpDismissal {
    private var pendingEnd = false
    fun reset() { pendingEnd = false }
    fun dismiss(automaticHandled: Boolean, videoEnded: Boolean) {
        pendingEnd = automaticHandled && !videoEnded
    }
    fun consumeAtEnd(videoEnded: Boolean): Boolean {
        if (!videoEnded || !pendingEnd) return false
        pendingEnd = false
        return true
    }
}
