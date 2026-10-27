package org.siloserver.silo.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardMode
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardStore

@Composable
internal fun TvSearchKeyboardSettings() {
    val store: TvSearchKeyboardStore = koinInject()
    val mode by store.mode.collectAsState(initial = store.currentMode)
    val autoOpenKeyboard by store.autoOpenKeyboard.collectAsState(initial = store.currentAutoOpenKeyboard)
    var showPicker by remember { mutableStateOf(false) }
    SettingsGroup("Search") {
        SettingsValueRow("Search Keyboard", mode.label, onClick = { showPicker = true })
        SettingsFooterText("Choose the built-in QWERTY keyboard or this TV's system keyboard for Search. Saved for this profile on this TV.")
        SettingsToggleRow(
            label = "Open Keyboard Automatically",
            checked = autoOpenKeyboard,
            onCheckedChange = store::setAutoOpenKeyboard,
        )
        SettingsFooterText("Open the selected keyboard when you select Search. When off, press OK on the search field to open it. Saved for this profile on this TV.")
    }
    if (showPicker) {
        TvSettingsPickerSheet(
            title = "Search Keyboard",
            options = TvSearchKeyboardMode.entries.map { PickerOption(it.id, it.label) },
            selectedId = mode.id,
            onSelect = { id ->
                TvSearchKeyboardMode.entries.firstOrNull { it.id == id }?.let(store::setMode)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
