package org.siloserver.silo.android.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import org.siloserver.silo.android.ui.components.SiloConfirmDialog
import org.siloserver.silo.android.ui.theme.SettingsDimens
import org.siloserver.silo.android.ui.theme.SettingsTextStyles
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSwitchOff
import org.siloserver.silo.common.player.audio.NightModeControl
import org.siloserver.silo.common.player.audio.NightModeParameters
import kotlin.math.roundToInt

/** The Night Mode row's value on the Playback page. */
internal fun nightModeParametersLabel(params: NightModeParameters) = if (params.isDefault) "Default" else "Custom"

/**
 * Playback → Night Mode: the compressor and limiter behind night mode, one
 * slider per value, then Restore Defaults.
 */
@Composable
fun SettingsNightModeScreen(viewModel: SettingsViewModel, onBackClick: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val params = state.nightModeParameters
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    SettingsPageScaffold(title = "Night Mode", onBackClick = onBackClick) {
        item(key = "compressor") {
            SettingsSection(
                title = NightModeControl.Section.Compressor.title,
                footer = "The compressor turns down audio louder than the threshold by the ratio, " +
                    "then makeup gain raises everything, so quiet dialogue gets louder. Knee softens " +
                    "the change at the threshold; attack and release set how fast it reacts.",
            ) {
                NightModeControl.inSection(NightModeControl.Section.Compressor).forEach { control ->
                    NightModeSliderRow(
                        control = control,
                        params = params,
                        onParamsChanged = viewModel::setNightModeParameters,
                    )
                }
            }
        }
        item(key = "limiter") {
            SettingsSection(
                title = NightModeControl.Section.Limiter.title,
                footer = "The limiter keeps peaks below its threshold after the compressor's makeup gain.",
            ) {
                NightModeControl.inSection(NightModeControl.Section.Limiter).forEach { control ->
                    NightModeSliderRow(
                        control = control,
                        params = params,
                        onParamsChanged = viewModel::setNightModeParameters,
                    )
                }
            }
        }
        item(key = "reset") {
            SettingsSection(
                title = null,
                footer = "Changes apply while you listen. They need Android 9 or later; older devices " +
                    "use a fixed boost instead. Saved for this profile on this device.",
            ) {
                SettingsDestructiveRow(
                    label = "Restore Defaults",
                    enabled = !params.isDefault,
                    onClick = { confirmingReset = true },
                )
            }
        }
    }

    if (confirmingReset) {
        SiloConfirmDialog(
            title = "Restore Defaults?",
            body = "Night mode's compressor and limiter go back to their default values.",
            confirmLabel = "Restore",
            onConfirm = {
                confirmingReset = false
                viewModel.resetNightModeParameters()
            },
            onDismiss = { confirmingReset = false },
        )
    }
}

/**
 * A label and the current value over a slider whose stops are the
 * control's choices. Dragging previews locally; the value is saved when the
 * drag ends, so a drag is one write rather than one per frame.
 */
@Composable
private fun NightModeSliderRow(
    control: NightModeControl,
    params: NightModeParameters,
    onParamsChanged: (NightModeParameters) -> Unit,
) {
    val savedIndex = control.indexOf(control.get(params))
    var dragIndex by remember(control) { mutableStateOf<Int?>(null) }
    val index = dragIndex ?: savedIndex
    val valueLabel = control.format(control.choices[index])
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowDivider(show = true)
            .padding(
                start = SettingsDimens.rowHorizontalPadding,
                end = SettingsDimens.rowHorizontalPadding,
                top = SettingsDimens.rowVerticalPadding,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = control.label,
                style = SettingsTextStyles.rowLabel,
                color = SiloOnSurface,
                modifier = Modifier.weight(1f),
            )
            Text(text = valueLabel, style = SettingsTextStyles.rowValue, color = SiloSecondaryText)
        }
        Slider(
            value = index.toFloat(),
            onValueChange = { dragIndex = it.roundToInt().coerceIn(control.choices.indices) },
            onValueChangeFinished = {
                dragIndex?.let { onParamsChanged(control.set(params, control.choices[it])) }
                dragIndex = null
            },
            valueRange = 0f..control.choices.lastIndex.toFloat(),
            // Stops between the two ends, so every choice is one.
            steps = (control.choices.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = SiloOnSurface,
                activeTickColor = Color.Transparent,
                inactiveTrackColor = SiloSwitchOff,
                inactiveTickColor = Color.Transparent,
            ),
            modifier = Modifier.semantics {
                contentDescription = "${control.section.title} ${control.label}"
                stateDescription = valueLabel
            },
        )
    }
}
