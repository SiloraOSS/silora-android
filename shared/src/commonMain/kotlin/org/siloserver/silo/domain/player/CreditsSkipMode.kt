package org.siloserver.silo.domain.player

/** Android's credits behavior, stored per profile on this device. */
enum class CreditsSkipMode(val wireValue: String, val label: String) {
    Off("off", "Never"),
    Ask("ask", "Ask to skip"),
    Auto("auto", "Skip automatically");

    // Watch Party keeps automatic transport changes disabled; offer a gated
    // manual action instead. Off still hides the credits action in a room.
    fun offersManualSkip(inWatchParty: Boolean): Boolean = this == Ask || (this == Auto && inWatchParty)

    companion object {
        val Default = Ask

        fun fromWire(value: String?): CreditsSkipMode? = entries.firstOrNull { it.wireValue == value }

        fun fromLegacyBoolean(value: Boolean): CreditsSkipMode = if (value) Auto else Ask
    }
}
