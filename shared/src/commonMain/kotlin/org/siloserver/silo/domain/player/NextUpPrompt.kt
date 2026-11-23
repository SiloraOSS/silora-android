package org.siloserver.silo.domain.player

/** Shared timing and crossing rules for automatic pre-end Up Next prompts. */
object NextUpPrompt {
    /** Zero (At end), unsaved timing, or an invalid runtime waits for playback to end. */
    fun startPosition(duration: Double, promptSeconds: Int?): Double? {
        if (!duration.isFinite() || promptSeconds == null || promptSeconds <= 0) return null
        return (duration - promptSeconds).takeIf { it > 0 }
    }

    /** Only a crossing counts. The caller excludes seeks before checking. */
    fun crossesPromptPoint(previousPosition: Double, position: Double, promptStart: Double?): Boolean =
        promptStart != null && previousPosition < promptStart && position >= promptStart
}
