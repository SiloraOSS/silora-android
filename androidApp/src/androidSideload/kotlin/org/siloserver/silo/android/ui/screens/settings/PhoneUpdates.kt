package org.siloserver.silo.android.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.siloserver.silo.android.R
import org.siloserver.silo.updates.InstallMethod
import org.siloserver.silo.updates.SiloraUpdates
import org.siloserver.silo.updates.UpdateState
import org.siloserver.silo.updates.UpdateStatus

internal object PhoneUpdates {
    const val available = true

    fun onLaunch(context: Context) {
        SiloraUpdates.get(context).launch()
    }

    @Composable
    fun Settings(onBackClick: () -> Unit) {
        val context = LocalContext.current
        val updates = remember(context) { SiloraUpdates.get(context) }
        val state by updates.state.collectAsStateWithLifecycle()
        val shizuku by updates.shizukuState.collectAsStateWithLifecycle()
        var picker by rememberSaveable { mutableStateOf<UpdatePicker?>(null) }

        SettingsPageScaffold(title = stringResource(R.string.updates_title), onBackClick = onBackClick) {
            item {
                SettingsSectionCard {
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_installed_version),
                        description = state.currentVersion,
                    )
                    if (state.checkedAt > 0) {
                        SettingsNavigationRow(
                            label = stringResource(R.string.updates_last_check),
                            value = DateUtils.getRelativeTimeSpanString(state.checkedAt).toString(),
                        )
                    }
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_status),
                        description = state.statusText(),
                    )
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_check_now),
                        onClick = updates::checkNow,
                        showChevron = false,
                        enabled = !state.busy,
                    )
                    if (state.release != null) {
                        SettingsNavigationRow(
                            label = stringResource(R.string.updates_install_update),
                            onClick = { requestInstall(context, updates) },
                            showChevron = false,
                            enabled = !state.busy,
                        )
                    }
                }
            }
            item {
                SettingsSectionCard {
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_interval),
                        value = intervalLabel(state.settings.intervalHours),
                        onClick = { picker = UpdatePicker.Interval },
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.updates_automatic),
                        description = stringResource(R.string.updates_automatic_description),
                        checked = state.settings.automatic && state.settings.intervalHours > 0,
                        enabled = state.settings.intervalHours > 0,
                        onCheckedChange = { updates.setSettings(state.settings.copy(automatic = it)) },
                    )
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_install_method),
                        value = methodLabel(state.settings.method),
                        onClick = { picker = UpdatePicker.Method },
                    )
                    if (state.settings.method == InstallMethod.SHIZUKU) {
                        SettingsNavigationRow(
                            label = stringResource(R.string.updates_shizuku),
                            description = shizuku.help,
                        )
                        if (!shizuku.ready) {
                            SettingsNavigationRow(
                                label = stringResource(R.string.updates_authorize_shizuku),
                                onClick = { authorizeShizuku(context, updates) },
                                showChevron = false,
                            )
                        }
                    }
                }
            }
        }

        when (picker) {
            UpdatePicker.Interval -> UpdatePickerDialog(
                title = stringResource(R.string.updates_interval),
                options = SiloraUpdates.INTERVALS,
                selection = state.settings.intervalHours,
                label = { intervalLabel(it) },
                onSelect = {
                    updates.setSettings(state.settings.copy(intervalHours = it))
                    picker = null
                },
                onDismiss = { picker = null },
            )
            UpdatePicker.Method -> UpdatePickerDialog(
                title = stringResource(R.string.updates_install_method),
                options = InstallMethod.entries,
                selection = state.settings.method,
                label = { methodLabel(it) },
                onSelect = {
                    updates.setSettings(state.settings.copy(method = it))
                    picker = null
                },
                onDismiss = { picker = null },
            )
            null -> Unit
        }
    }

    @Composable
    fun Dialogs(ready: Boolean, launch: Int) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val updates = remember(context) { SiloraUpdates.get(context) }
        val state by updates.state.collectAsStateWithLifecycle()
        val release = state.release
        var showOffer by remember { mutableStateOf(false) }

        LaunchedEffect(updates, lifecycle, ready) {
            if (!ready) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updates.confirmation.collect { confirmation ->
                    if (confirmation != null) updates.takeConfirmation()?.let { pending ->
                        if (runCatching { context.startActivity(pending.intent) }.isFailure) {
                            updates.unavailableConfirmation(pending)
                        }
                    }
                }
            }
        }
        LaunchedEffect(updates, lifecycle, ready, launch, release, state.busy, state.settings.intervalHours) {
            showOffer = false
            if (!ready || release == null || state.busy) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    while (true) {
                        val eligible = updates.shouldOffer(System.currentTimeMillis())
                        showOffer = eligible && (showOffer || updates.idleForPrompt())
                        delay(5_000)
                    }
                } finally {
                    showOffer = false
                }
            }
        }
        if (ready && showOffer && release != null && !state.busy) {
            val later = {
                updates.later()
                showOffer = false
            }
            AlertDialog(
                onDismissRequest = later,
                title = { Text(stringResource(R.string.updates_offer_title)) },
                text = { Text(stringResource(R.string.updates_offer_message, release.versionName)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (requestInstall(context, updates)) later()
                    }) {
                        Text(stringResource(R.string.updates_install_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = later) { Text(stringResource(R.string.updates_later)) }
                },
            )
        }
    }
}

private enum class UpdatePicker { Interval, Method }

@Composable
private fun <T> UpdatePickerDialog(
    title: String,
    options: List<T>,
    selection: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == selection, role = Role.RadioButton, onClick = { onSelect(option) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selection, onClick = null)
                        Text(label(option), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun requestInstall(context: Context, updates: SiloraUpdates): Boolean {
    when (updates.state.value.settings.method) {
        InstallMethod.STANDARD -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !updates.canUseStandard()) {
            val packageUri = Uri.parse("package:${context.packageName}")
            val permission = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            if (runCatching { context.startActivity(permission) }.isFailure) {
                Toast.makeText(context, R.string.updates_permission, Toast.LENGTH_LONG).show()
            }
            return false
        }
        InstallMethod.SHIZUKU -> if (!updates.shizukuState.value.ready) {
            authorizeShizuku(context, updates)
            return false
        }
    }
    updates.installNow()
    return true
}

private fun authorizeShizuku(context: Context, updates: SiloraUpdates) {
    updates.requestShizukuPermission()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
}

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours == 0) stringResource(R.string.updates_interval_off)
    else pluralStringResource(R.plurals.updates_interval_hours, hours, hours)

@Composable
private fun methodLabel(method: InstallMethod): String =
    stringResource(if (method == InstallMethod.STANDARD) R.string.updates_standard else R.string.updates_shizuku)

@Composable
private fun UpdateState.statusText(): String = detail ?: when (status) {
    UpdateStatus.IDLE -> release?.let { stringResource(R.string.updates_available, it.versionName) }
        ?: stringResource(R.string.updates_idle)
    UpdateStatus.CHECKING -> stringResource(R.string.updates_checking)
    UpdateStatus.UP_TO_DATE -> stringResource(R.string.updates_up_to_date)
    UpdateStatus.AVAILABLE -> stringResource(R.string.updates_available, release?.versionName.orEmpty())
    UpdateStatus.DOWNLOADING -> stringResource(R.string.updates_downloading)
    UpdateStatus.INSTALLING -> stringResource(R.string.updates_installing)
    UpdateStatus.WAITING_FOR_PLAYBACK -> stringResource(R.string.updates_waiting)
    UpdateStatus.NEEDS_CONFIRMATION -> stringResource(R.string.updates_confirmation)
    UpdateStatus.NEEDS_INSTALL_PERMISSION -> stringResource(R.string.updates_permission)
    UpdateStatus.INSTALLED -> stringResource(R.string.updates_installed)
    UpdateStatus.ERROR -> stringResource(R.string.updates_error)
}
