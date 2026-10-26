package org.siloserver.silo.tv.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/** Remote mappings belong to this TV, independently of the server or active profile. */
internal class TvRemoteShortcutsStore(private val prefs: SharedPreferences) {
    private val _mappings = MutableStateFlow(
        TvRemoteButton.entries.associateWith { button ->
            TvRemoteShortcutAction.fromId(prefs.getString(button.id, null))
        },
    )
    val mappings = _mappings.asStateFlow()

    fun setAction(button: TvRemoteButton, action: TvRemoteShortcutAction) {
        prefs.edit { putString(button.id, action.id) }
        _mappings.value = _mappings.value + (button to action)
    }

    fun reset() {
        prefs.edit { clear() }
        _mappings.value = TvRemoteButton.entries.associateWith { TvRemoteShortcutAction.Default }
    }

    companion object {
        const val PREFS_NAME = "silo_tv_remote_shortcuts"
    }
}
