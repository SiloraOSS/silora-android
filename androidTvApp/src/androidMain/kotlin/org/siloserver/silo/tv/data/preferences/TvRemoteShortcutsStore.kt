package org.siloserver.silo.tv.data.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow
import org.siloserver.silo.model.server.ServerEntry

internal enum class TvRemoteButton(val id: String, val label: String) {
    Left("left", "Long press Left"),
    Right("right", "Long press Right"),
    Select("select", "Long press OK / Select"),
    Down("down", "Long press Down"),
    Up("up", "Long press Up"),
}

internal enum class TvRemoteShortcutAction(val id: String, val label: String, val description: String? = null) {
    Default("default", "Default behavior", "Keep the app's usual controls."),
    None("none", "No action", "Ignore the long press. Short presses still work."),
    ToggleSleepTimer("toggle_sleep_timer", "Toggle sleep timer", "Start with the saved duration, or cancel an active timer."),
    SleepTimerSettings("sleep_timer_settings", "Open sleep timer settings"),
    ToggleSubtitles("toggle_subtitles", "Turn subtitles on / off"),
    SubtitleSettings("subtitle_settings", "Open subtitle settings"),
    AudioSettings("audio_settings", "Open audio settings"),
    ToggleNightMode("toggle_night_mode", "Toggle Night Mode"),
    PlaybackOptions("playback_options", "Open playback options"),
    PlayerStats("player_stats", "Open player stats"),
    PlayPause("play_pause", "Play / pause"),
    SkipBack("skip_back", "Skip backward", "Use your video skip interval."),
    SkipForward("skip_forward", "Skip forward", "Use your video skip interval."),
    Restart("restart", "Restart playback"),
    NextEpisode("next_episode", "Next episode", "Available when there is a next episode, outside a Watch Party."),
    ;

    companion object {
        fun fromId(id: String?): TvRemoteShortcutAction = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** Remote mappings belong to the active server/profile on this TV. */
internal class TvRemoteShortcutsStore(prefs: SharedPreferences, activeServer: StateFlow<ServerEntry?>) {
    private val preferences = TvProfilePreferences(
        prefs, activeServer, legacyKeys = TvRemoteButton.entries.map { it.id }.toSet(),
    )
    private val defaults = TvRemoteButton.entries.associateWith { TvRemoteShortcutAction.Default }
    private val readMappings: (SharedPreferences, String) -> Map<TvRemoteButton, TvRemoteShortcutAction> = { stored, prefix ->
        TvRemoteButton.entries.associateWith { button ->
            TvRemoteShortcutAction.fromId(stored.getString(prefix + button.id, null))
        }
    }
    val currentMappings get() = preferences.read(defaults, readMappings)
    val mappings = preferences.observe(defaults, readMappings)

    fun actionFor(button: TvRemoteButton): TvRemoteShortcutAction = currentMappings.getValue(button)

    fun setAction(button: TvRemoteButton, action: TvRemoteShortcutAction) {
        preferences.edit { prefix -> putString(prefix + button.id, action.id) }
    }

    fun reset() {
        preferences.edit { prefix -> TvRemoteButton.entries.forEach { remove(prefix + it.id) } }
    }

    companion object {
        const val PREFS_NAME = "silo_tv_remote_shortcuts"
    }
}
