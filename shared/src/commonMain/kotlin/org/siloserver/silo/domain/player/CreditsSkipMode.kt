package org.siloserver.silo.domain.player

/** Android's credits behavior, stored per profile on this device. */
enum class CreditsSkipMode(val wireValue: String, val label: String, val description: String) {
    Ask("ask", "Ask", "Show a Skip Credits button and let you choose."),
    Auto("auto", "Auto", "Automatically skip to the end of each credits segment."),
    Off("off", "Off", "Play credits without a skip button.");

    // Watch Party keeps automatic transport changes disabled; offer a gated
    // manual action instead. Off still hides the credits action in a room.
    fun offersManualSkip(inWatchParty: Boolean): Boolean = this == Ask || (this == Auto && inWatchParty)

    companion object {
        val Default = Ask

        fun fromWire(value: String?): CreditsSkipMode? = entries.firstOrNull { it.wireValue == value }

        fun fromLegacyBoolean(value: Boolean): CreditsSkipMode = if (value) Auto else Ask
    }
}
