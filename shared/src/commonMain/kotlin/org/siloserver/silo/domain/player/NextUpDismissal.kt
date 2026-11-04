package org.siloserver.silo.domain.player

/** Records an automatic pre-end dismissal until this episode actually ends. */
class NextUpDismissal {
    private var pendingEnd = false

    fun reset() { pendingEnd = false }

    /** Returns whether the end card must stay visible after cancelling its countdown. */
    fun dismiss(automaticHandled: Boolean, videoEnded: Boolean): Boolean {
        // A manual pre-end card must not erase an earlier automatic dismissal.
        pendingEnd = !videoEnded && (pendingEnd || automaticHandled)
        return videoEnded
    }

    fun consumeAtEnd(videoEnded: Boolean): Boolean {
        if (!videoEnded || !pendingEnd) return false
        pendingEnd = false
        return true
    }
}
