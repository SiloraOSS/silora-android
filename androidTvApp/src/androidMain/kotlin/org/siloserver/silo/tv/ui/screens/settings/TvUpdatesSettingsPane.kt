package org.siloserver.silo.tv.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.R
import org.siloserver.silo.tv.ui.components.TvDialog
import org.siloserver.silo.tv.ui.components.TvDialogAction
import org.siloserver.silo.tv.ui.components.TvDialogActionStyle
import org.siloserver.silo.tv.ui.screens.player.TvPlayerType
import org.siloserver.silo.updates.InstallMethod
import org.siloserver.silo.updates.SiloraUpdates
import org.siloserver.silo.updates.UpdateCheckInterval
import org.siloserver.silo.updates.UpdatePrompt
import org.siloserver.silo.updates.UpdateState
import org.siloserver.silo.updates.UpdateStatus
import org.siloserver.silo.updates.formatUpdateCheckHour
import org.siloserver.silo.updates.openInstallPermissionSettings
import org.siloserver.silo.updates.R as UpdatesR

@Composable
internal fun TvUpdatesSettingsPane(firstFocusRequester: FocusRequester) {
    val context = LocalContext.current
    val updates = remember(context) { SiloraUpdates.get(context) }
    val state by updates.state.collectAsState()
    val manualActionPending by updates.manualActionPending.collectAsState()
    val shizuku by updates.shizukuState.collectAsState()
    var picker by remember { mutableStateOf<UpdatePicker?>(null) }
    var showInstallPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) updates.setNotifyOnAutomaticInstall(true)
        else Toast.makeText(context, UpdatesR.string.updates_notification_permission_help, Toast.LENGTH_LONG).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { SettingsInfoRow(stringResource(R.string.updates_installed_version), state.currentVersion) }
        if (state.checkedAt > 0L) {
            item {
                SettingsInfoRow(
                    stringResource(R.string.updates_last_check),
                    DateUtils.getRelativeTimeSpanString(state.checkedAt).toString(),
                )
            }
        }
        item {
            SettingsActionRow(
                label = stringResource(R.string.updates_check_now),
                onClick = { updates.checkNow() },
                enabled = !state.busy && !manualActionPending,
                focusRequester = firstFocusRequester,
            )
        }
        item {
            SettingsInfoRow(
                stringResource(R.string.updates_status),
                if (manualActionPending && !state.busy) stringResource(UpdatesR.string.updates_action_queued)
                else state.statusText(),
                singleLine = false,
            )
        }
        if (state.release != null) {
            item {
                SettingsActionRow(
                    label = stringResource(R.string.updates_install_update),
                    onClick = { requestInstall(context, updates) },
                    enabled = !state.busy && !manualActionPending,
                )
            }
        }
        item {
            SettingsValueRow(
                label = stringResource(R.string.updates_interval),
                value = intervalLabel(state.settings.interval),
                onClick = { picker = UpdatePicker.Interval },
            )
        }
        if (state.settings.interval == UpdateCheckInterval.CUSTOM) {
            item {
                SettingsValueRow(
                    label = stringResource(UpdatesR.string.updates_daily_check_time),
                    value = formatUpdateCheckHour(context, state.settings.checkHour),
                    onClick = { picker = UpdatePicker.Hour },
                )
            }
        }
        item(key = "installMethod") {
            SettingsValueRow(
                label = stringResource(R.string.updates_install_method),
                value = methodLabel(state.settings.method),
                onClick = { picker = UpdatePicker.Method },
            )
        }
        when (state.settings.method) {
            InstallMethod.STANDARD -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                item(key = "installPermissionSettings") {
                    SettingsActionRow(
                        label = stringResource(R.string.updates_install_permission_settings),
                        onClick = {
                            if (!openInstallPermissionSettings(context)) {
                                Toast.makeText(context, UpdatesR.string.updates_install_permission_help, Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
            }
            InstallMethod.SHIZUKU -> {
                item(key = "shizukuStatus") { SettingsInfoRow("Shizuku", shizuku.help, singleLine = false) }
                if (!shizuku.ready) {
                    item(key = "authorizeShizuku") {
                        SettingsActionRow(stringResource(R.string.updates_authorize_shizuku), onClick = {
                            updates.requestShizukuPermission()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                        })
                    }
                }
            }
        }
        item(key = "automaticInstall") {
            SettingsToggleRow(
                label = stringResource(R.string.updates_automatic),
                checked = state.settings.automatic,
                enabled = state.settings.interval.enabled,
                onCheckedChange = { automatic ->
                    val settings = state.settings
                    updates.setSettings(settings.copy(automatic = automatic))
                    showInstallPermission = automatic && settings.method == InstallMethod.STANDARD &&
                        !updates.canUseStandard()
                },
            )
        }
        item(key = "notifyAfterAutomaticInstall") {
            val automatic = state.settings.automatic
            SettingsToggleRow(
                label = stringResource(UpdatesR.string.updates_notify_on_automatic_install),
                checked = state.settings.notifyOnAutomaticInstall,
                enabled = automatic,
                modifier = Modifier
                    .alpha(if (automatic) 1f else 0.42f)
                    .focusProperties { canFocus = automatic },
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
        }
        item(key = "showWhatChanged") {
            SettingsToggleRow(
                label = stringResource(UpdatesR.string.updates_show_what_changed),
                checked = state.settings.showWhatChanged,
                onCheckedChange = updates::setShowWhatChanged,
            )
        }
    }

    when (picker) {
        UpdatePicker.Interval -> TvSettingsPickerSheet(
            title = stringResource(R.string.updates_interval),
            options = UpdateCheckInterval.entries.map { PickerOption(it.name, intervalLabel(it)) },
            selectedId = state.settings.interval.name,
            onSelect = { id ->
                updates.setSettings(state.settings.copy(interval = UpdateCheckInterval.valueOf(id)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        UpdatePicker.Hour -> TvSettingsPickerSheet(
            title = stringResource(UpdatesR.string.updates_daily_check_time),
            options = (0..23).map { PickerOption(it.toString(), formatUpdateCheckHour(context, it)) },
            selectedId = state.settings.checkHour.toString(),
            onSelect = { id ->
                updates.setSettings(state.settings.copy(checkHour = id.toInt()))
                picker = null
            },
            onDismiss = { picker = null },
        )
        UpdatePicker.Method -> TvSettingsPickerSheet(
            title = stringResource(R.string.updates_install_method),
            options = InstallMethod.entries.map { PickerOption(it.name, methodLabel(it)) },
            selectedId = state.settings.method.name,
            onSelect = { id ->
                updates.setSettings(state.settings.copy(method = InstallMethod.valueOf(id)))
                picker = null
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }

    if (showInstallPermission) {
        TvDialog(
            title = stringResource(UpdatesR.string.updates_install_permission_title),
            message = stringResource(UpdatesR.string.updates_install_permission_message),
            actions = listOf(
                TvDialogAction(stringResource(R.string.updates_later), onClick = { showInstallPermission = false }),
                TvDialogAction(stringResource(UpdatesR.string.updates_open_settings), onClick = {
                    if (openInstallPermissionSettings(context)) showInstallPermission = false
                    else Toast.makeText(context, UpdatesR.string.updates_install_permission_help, Toast.LENGTH_LONG).show()
                }, style = TvDialogActionStyle.Primary),
            ),
            onDismissRequest = { showInstallPermission = false },
        )
    }
}

private enum class UpdatePicker { Interval, Hour, Method }

@Composable
internal fun TvUpdateDialogs(updates: SiloraUpdates, ready: Boolean, launch: Int) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by updates.state.collectAsState()
    val release = state.release
    val noticePending = state.standaloneUpdaterNoticePending
    val whatChanged = state.whatChanged
    var prompt by remember { mutableStateOf<UpdatePrompt?>(null) }

    LaunchedEffect(updates, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            updates.confirmation.collect { confirmation ->
                if (confirmation != null) updates.takeConfirmation()?.let { pending ->
                    runCatching { context.startActivity(pending.intent) }
                        .onSuccess { updates.confirmationOpened(pending) }
                        .onFailure { updates.unavailableConfirmation(pending) }
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
                    delay(if (prompt == UpdatePrompt.UPDATE_AVAILABLE) 1_000L else 5_000L)
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
        TvDialog(
            title = stringResource(R.string.updates_offer_title),
            message = stringResource(R.string.updates_offer_message, release.versionName),
            actions = listOf(
                TvDialogAction(stringResource(R.string.updates_later), later),
                TvDialogAction(stringResource(R.string.updates_install_now), onClick = {
                    if (requestInstall(context, updates)) {
                        updates.dismissUpdateOffer()
                        prompt = null
                    }
                }, style = TvDialogActionStyle.Primary),
            ),
            onDismissRequest = later,
        )
    } else if (ready && prompt == UpdatePrompt.STANDALONE_UPDATER && noticePending && !state.busy) {
        val dismiss = {
            updates.dismissStandaloneUpdaterNotice()
            prompt = null
        }
        TvDialog(
            title = stringResource(UpdatesR.string.updates_standalone_notice_title),
            message = stringResource(UpdatesR.string.updates_standalone_notice_message),
            actions = listOf(TvDialogAction(stringResource(UpdatesR.string.updates_got_it), dismiss)),
            onDismissRequest = dismiss,
        )
    } else if (ready && prompt == UpdatePrompt.WHAT_CHANGED && whatChanged != null && state.canShowForegroundPrompt) {
        val dismiss = {
            updates.dismissWhatChanged()
            prompt = null
        }
        TvDialog(
            title = stringResource(UpdatesR.string.updates_what_changed_title),
            width = 640.dp,
            actions = listOf(TvDialogAction(stringResource(UpdatesR.string.updates_got_it), dismiss)),
            onDismissRequest = dismiss,
        ) {
            if (state.currentVersion.isNotBlank()) {
                Text(
                    text = stringResource(UpdatesR.string.updates_what_changed_version, state.currentVersion),
                    style = TvPlayerType.RowDetail,
                )
            }
            Text(text = whatChanged, style = TvPlayerType.Body)
        }
    }
}

private fun requestInstall(context: Context, updates: SiloraUpdates): Boolean =
    when (updates.state.value.settings.method) {
        InstallMethod.STANDARD -> {
            if (updates.canUseStandard()) updates.installNow()
            else openInstallPermissionSettings(context).also { opened ->
                if (!opened) Toast.makeText(context, R.string.updates_permission, Toast.LENGTH_LONG).show()
            }
        }
        InstallMethod.SHIZUKU -> {
            if (updates.shizukuState.value.ready) updates.installNow()
            else {
                val error = updates.requestShizukuPermission()
                when {
                    error != null -> {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        false
                    }
                    updates.shizukuState.value.ready -> updates.installNow()
                    else -> true
                }
            }
        }
    }

@Composable
private fun intervalLabel(interval: UpdateCheckInterval): String = when (interval) {
    UpdateCheckInterval.OFF -> stringResource(R.string.updates_interval_off)
    UpdateCheckInterval.ON_LAUNCH -> stringResource(UpdatesR.string.updates_interval_on_launch)
    UpdateCheckInterval.CUSTOM -> stringResource(UpdatesR.string.updates_interval_custom)
    else -> stringResource(R.string.updates_interval_hours, interval.hours)
}

@Composable
private fun methodLabel(method: InstallMethod): String =
    if (method == InstallMethod.STANDARD) stringResource(R.string.updates_standard) else "Shizuku"

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
