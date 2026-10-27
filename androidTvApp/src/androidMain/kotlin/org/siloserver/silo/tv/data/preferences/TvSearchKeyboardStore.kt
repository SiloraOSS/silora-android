package org.siloserver.silo.tv.data.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow
import org.siloserver.silo.model.server.ServerEntry

internal enum class TvSearchKeyboardMode(val id: String, val label: String) {
    App("app", "App keyboard"),
    System("system", "System keyboard"),
}

/** Search input belongs to the active server/profile on this TV. */
internal class TvSearchKeyboardStore(prefs: SharedPreferences, activeServer: StateFlow<ServerEntry?>) {
    private val preferences = TvProfilePreferences(
        prefs, activeServer, legacyKeys = setOf("mode", "auto_open_keyboard"),
    )
    private val readMode: (SharedPreferences, String) -> TvSearchKeyboardMode = { stored, prefix ->
        TvSearchKeyboardMode.entries.firstOrNull { it.id == stored.getString(prefix + "mode", null) }
            ?: TvSearchKeyboardMode.System
    }
    private val readAutoOpenKeyboard: (SharedPreferences, String) -> Boolean = { stored, prefix ->
        stored.getBoolean(prefix + "auto_open_keyboard", false)
    }
    val currentMode get() = preferences.read(TvSearchKeyboardMode.System, readMode)
    val mode = preferences.observe(TvSearchKeyboardMode.System, readMode)

    val currentAutoOpenKeyboard get() = preferences.read(false, readAutoOpenKeyboard)
    val autoOpenKeyboard = preferences.observe(false, readAutoOpenKeyboard)

    fun setMode(value: TvSearchKeyboardMode) {
        preferences.edit { prefix -> putString(prefix + "mode", value.id) }
    }

    fun setAutoOpenKeyboard(value: Boolean) {
        preferences.edit { prefix -> putBoolean(prefix + "auto_open_keyboard", value) }
    }

    companion object {
        const val PREFS_NAME = "silo_tv_search_keyboard"
    }
}
