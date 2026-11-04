package org.siloserver.silo.domain.player

/** Silora's device-local choice; it does not change the server's markers. */
enum class NextUpPromptMode(val wireValue: String, val label: String, val description: String) {
    CreditsMarkers(
        "credits", "Credits markers",
        "Show Up Next when credits begin. Use Time remaining only if this episode has no eligible credits markers.",
    ),
    TimeRemaining(
        "time_remaining", "Time remaining",
        "Ignore credits markers and show Up Next using the time below.",
    ),
    Disabled(
        "off", "Disabled",
        "Stop automatic pop-ups during playback. The end screen and skip buttons still work.",
    );

    /** Null means wait for playback to end instead of opening an automatic pre-end prompt. */
    fun promptStartPosition(creditsStart: Double?, duration: Double, secondsBeforeEnd: Int?): Double? {
        if (this == Disabled || !duration.isFinite() || duration <= 0) return null
        if (this == CreditsMarkers && creditsStart != null && creditsStart.isFinite() &&
            creditsStart >= duration * (1.0 - MarkerTimeline.UP_NEXT_END_WINDOW_FRACTION) && creditsStart < duration) return creditsStart
        if (secondsBeforeEnd == null || secondsBeforeEnd <= 0 || !duration.isFinite() || duration <= 0) return null
        // Do not open on startup when the configured lead time exceeds the runtime.
        return (duration - secondsBeforeEnd).takeIf { it > 0 }
    }

    companion object {
        val Default = CreditsMarkers

        val settingsDescription: String = entries.joinToString("\n") { "${it.label}: ${it.description}" }

        fun timingDescription(mode: NextUpPromptMode): String = when (mode) {
            CreditsMarkers -> "Fallback only: used when this episode has no eligible credits markers. " +
                "Credits markers take priority. At end waits until playback finishes."
            TimeRemaining -> "Show Up Next this long before the end, even when credits markers are present. " +
                "At end waits until playback finishes."
            Disabled -> "Not used while automatic Up Next pop-ups are disabled."
        }

        fun fromWire(value: String?): NextUpPromptMode = when (value) {
            "off" -> Disabled
            "time_remaining" -> TimeRemaining
            else -> Default
        }
    }
}
