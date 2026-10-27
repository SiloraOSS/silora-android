package org.siloserver.silo.tv.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class TvSearchKeyboardMode(val id: String, val label: String) {
    App("app", "App keyboard"),
    System("system", "System keyboard"),
}

/** Search input is a device preference, shared by profiles on this TV. */
internal class TvSearchKeyboardStore(private val prefs: SharedPreferences) {
    private val _mode = MutableStateFlow(
        TvSearchKeyboardMode.entries.firstOrNull { it.id == prefs.getString("mode", null) }
            ?: TvSearchKeyboardMode.System,
    )
    val mode = _mode.asStateFlow()

    fun setMode(value: TvSearchKeyboardMode) {
        prefs.edit { putString("mode", value.id) }
        _mode.value = value
    }

    companion object {
        const val PREFS_NAME = "silo_tv_search_keyboard"
    }
}
