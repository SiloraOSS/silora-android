package org.siloserver.silo.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.siloserver.silo.tv.data.preferences.TvRemoteButton
import org.siloserver.silo.tv.data.preferences.TvRemoteShortcutAction
import org.siloserver.silo.tv.data.preferences.TvRemoteShortcutsStore
import org.siloserver.silo.tv.ui.theme.Spacing

@Composable
internal fun TvRemoteShortcutsSettingsPane(firstFocusRequester: FocusRequester) {
    val store: TvRemoteShortcutsStore = koinInject()
    val mappings by store.mappings.collectAsState(initial = store.currentMappings)
    var editingButton by remember { mutableStateOf<TvRemoteButton?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = Spacing.xxxl),
    ) {
        item {
            SettingsGroup("During video playback") {
                TvRemoteButton.entries.forEachIndexed { index, button ->
                    SettingsValueRow(
                        label = button.label,
                        value = mappings.getValue(button).label,
                        onClick = { editingButton = button },
                        focusRequester = firstFocusRequester.takeIf { index == 0 },
                    )
                }
                SettingsFooterText(
                    "Hold a button to use its shortcut while the playback controls are hidden. " +
                        "Short presses keep their usual action. Menus and dialogs keep their normal navigation. " +
                        "These shortcuts are saved for this profile on this TV.",
                )
            }
        }
        item {
            SettingsGroup("Reset") {
                SettingsActionRow(label = "Reset shortcuts", onClick = store::reset)
                SettingsFooterText("Restore every button to Default behavior for this profile on this TV.")
            }
        }
    }

    editingButton?.let { button ->
        TvSettingsPickerSheet(
            title = button.label,
            options = TvRemoteShortcutAction.entries.map { PickerOption(it.id, it.label, it.description) },
            selectedId = mappings.getValue(button).id,
            onSelect = { id ->
                store.setAction(button, TvRemoteShortcutAction.fromId(id))
                editingButton = null
            },
            onDismiss = { editingButton = null },
        )
    }
}
