package org.siloserver.silo.android.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
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
import org.siloserver.silo.updates.UpdateCheckInterval
import org.siloserver.silo.updates.UpdatePrompt
import org.siloserver.silo.updates.UpdateState
import org.siloserver.silo.updates.UpdateStatus
import org.siloserver.silo.updates.formatUpdateCheckHour
import org.siloserver.silo.updates.openInstallPermissionSettings
import org.siloserver.silo.updates.R as UpdatesR

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
        var showInstallPermission by rememberSaveable { mutableStateOf(false) }
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) updates.setNotifyOnAutomaticInstall(true)
            else Toast.makeText(context, UpdatesR.string.updates_notification_permission_help, Toast.LENGTH_LONG).show()
        }

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
                        value = intervalLabel(state.settings.interval),
                        onClick = { picker = UpdatePicker.Interval },
                    )
                    if (state.settings.interval == UpdateCheckInterval.CUSTOM) {
                        SettingsNavigationRow(
                            label = stringResource(UpdatesR.string.updates_daily_check_time),
                            value = formatUpdateCheckHour(context, state.settings.checkHour),
                            onClick = { picker = UpdatePicker.Hour },
                        )
                    }
                    SettingsSwitchRow(
                        label = stringResource(R.string.updates_automatic),
                        description = stringResource(R.string.updates_automatic_description),
                        checked = state.settings.automatic && state.settings.interval.enabled,
                        enabled = state.settings.interval.enabled,
                        onCheckedChange = { automatic ->
                            val settings = state.settings
                            updates.setSettings(settings.copy(automatic = automatic))
                            showInstallPermission = automatic && settings.method == InstallMethod.STANDARD &&
                                !updates.canUseStandard()
                        },
                    )
                    SettingsSwitchRow(
                        label = stringResource(UpdatesR.string.updates_notify_on_automatic_install),
                        checked = state.settings.notifyOnAutomaticInstall,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                updates.setNotifyOnAutomaticInstall(enabled)
                            }
                        },
                    )
                    SettingsNavigationRow(
                        label = stringResource(R.string.updates_install_method),
                        value = methodLabel(state.settings.method),
                        onClick = { picker = UpdatePicker.Method },
                    )
                    SettingsSwitchRow(
                        label = stringResource(UpdatesR.string.updates_show_what_changed),
                        checked = state.settings.showWhatChanged,
                        onCheckedChange = updates::setShowWhatChanged,
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
                options = UpdateCheckInterval.entries,
                selection = state.settings.interval,
                label = { intervalLabel(it) },
                onSelect = {
                    updates.setSettings(state.settings.copy(interval = it))
                    picker = null
                },
                onDismiss = { picker = null },
            )
            UpdatePicker.Hour -> UpdatePickerDialog(
                title = stringResource(UpdatesR.string.updates_daily_check_time),
                options = (0..23).toList(),
                selection = state.settings.checkHour,
                label = { formatUpdateCheckHour(context, it) },
                onSelect = {
                    updates.setSettings(state.settings.copy(checkHour = it))
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

        if (showInstallPermission) {
            AlertDialog(
                onDismissRequest = { showInstallPermission = false },
                title = { Text(stringResource(UpdatesR.string.updates_install_permission_title)) },
                text = { Text(stringResource(UpdatesR.string.updates_install_permission_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (openInstallPermissionSettings(context)) showInstallPermission = false
                        else Toast.makeText(context, UpdatesR.string.updates_install_permission_help, Toast.LENGTH_LONG).show()
                    }) {
                        Text(stringResource(UpdatesR.string.updates_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showInstallPermission = false }) {
                        Text(stringResource(R.string.updates_later))
                    }
                },
            )
        }
    }

    @Composable
    fun Dialogs(ready: Boolean, launch: Int) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val updates = remember(context) { SiloraUpdates.get(context) }
        val state by updates.state.collectAsStateWithLifecycle()
        val release = state.release
        val noticePending = state.standaloneUpdaterNoticePending
        val whatChanged = state.whatChanged
        var prompt by remember { mutableStateOf<UpdatePrompt?>(null) }

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
        LaunchedEffect(updates, lifecycle, ready, launch, release, noticePending, whatChanged,
            state.canShowForegroundPrompt, state.settings.interval.hours) {
            prompt = null
            if (!ready || !state.canShowForegroundPrompt ||
                (release == null && !noticePending && whatChanged == null)
            ) return@LaunchedEffect
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    while (true) {
                        prompt = updates.foregroundPrompt(prompt)
                        delay(5_000)
                    }
                } finally {
                    prompt = null
                }
            }
        }
        if (ready && prompt == UpdatePrompt.UPDATE_AVAILABLE && release != null && !state.busy) {
            val later = {
                updates.later()
                prompt = null
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
        } else if (ready && prompt == UpdatePrompt.STANDALONE_UPDATER && noticePending && !state.busy) {
            val dismiss = {
                updates.dismissStandaloneUpdaterNotice()
                prompt = null
            }
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(UpdatesR.string.updates_standalone_notice_title)) },
                text = { Text(stringResource(UpdatesR.string.updates_standalone_notice_message)) },
                confirmButton = {
                    TextButton(onClick = dismiss) {
                        Text(stringResource(UpdatesR.string.updates_got_it))
                    }
                },
            )
        } else if (ready && prompt == UpdatePrompt.WHAT_CHANGED && whatChanged != null && state.canShowForegroundPrompt) {
            val dismiss = {
                updates.dismissWhatChanged()
                prompt = null
            }
            AlertDialog(
                onDismissRequest = dismiss,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(UpdatesR.string.updates_what_changed_title))
                        if (state.currentVersion.isNotBlank()) {
                            Text(
                                text = stringResource(UpdatesR.string.updates_what_changed_version, state.currentVersion),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                text = { Text(whatChanged, Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = {
                    TextButton(onClick = dismiss) { Text(stringResource(UpdatesR.string.updates_got_it)) }
                },
            )
        }
    }
}

private enum class UpdatePicker { Interval, Hour, Method }

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
        InstallMethod.STANDARD -> if (!updates.canUseStandard()) {
            if (!openInstallPermissionSettings(context)) {
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
private fun intervalLabel(interval: UpdateCheckInterval): String = when (interval) {
    UpdateCheckInterval.OFF -> stringResource(R.string.updates_interval_off)
    UpdateCheckInterval.CUSTOM -> stringResource(UpdatesR.string.updates_interval_custom)
    else -> pluralStringResource(R.plurals.updates_interval_hours, interval.hours, interval.hours)
}

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
