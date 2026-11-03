package org.siloserver.silo.domain.player

/** Silora's device-local choice; it does not change the server's markers. */
enum class NextUpPromptMode(val wireValue: String, val label: String) {
    Enabled("credits", "Enabled"),
    Disabled("off", "Disabled");

    companion object {
        val Default = Enabled

        fun fromWire(value: String?): NextUpPromptMode = when (value) {
            // Keep viewers who opted out of credits triggers opted out when upgrading.
            "off", "time_remaining" -> Disabled
            else -> Default
        }
    }
}
