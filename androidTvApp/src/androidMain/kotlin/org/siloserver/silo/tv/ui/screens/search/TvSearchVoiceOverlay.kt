package org.siloserver.silo.tv.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.components.TvDialog
import org.siloserver.silo.tv.ui.components.TvDialogAction
import org.siloserver.silo.tv.ui.components.TvDialogActionStyle

@Composable
internal fun TvSearchVoiceOverlay(
    state: TvVoiceSearchState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TvDialog(
        title = "Voice search",
        onDismissRequest = onCancel,
        // Own the legacy TV key path before Compose treats Back as a focus exit.
        // API 36's canceled Up is already answered by the dialog's BackHandler.
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Back || event.key == Key.Escape) {
                if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) onCancel()
                true
            } else false
        },
        actions = buildList {
            if (state.phase == TvVoicePhase.Error) {
                if (state.openSettings) add(TvDialogAction("Open settings", onOpenSettings, TvDialogActionStyle.Primary))
                else if (state.retryable) add(TvDialogAction("Try again", onRetry, TvDialogActionStyle.Primary))
            }
            add(TvDialogAction("Cancel", onCancel))
        },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(40.dp),
                    tint = if (state.phase == TvVoicePhase.Listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    when (state.phase) {
                        TvVoicePhase.Permission -> "Allow microphone access to speak your search."
                        TvVoicePhase.Preparing -> "Getting the microphone ready…"
                        TvVoicePhase.Listening -> "Listening… Speak your search."
                        TvVoicePhase.Processing -> "Finishing your search…"
                        TvVoicePhase.Error -> state.message
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.transcript.isNotBlank()) Text(
                    state.transcript, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
