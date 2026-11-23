package org.siloserver.silo.tv.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.siloserver.silo.tv.R
import org.siloserver.silo.tv.ui.components.TvDialog
import org.siloserver.silo.tv.ui.components.TvDialogAction
import org.siloserver.silo.tv.ui.components.TvDialogActionStyle
import org.siloserver.silo.updates.InstallMethod
import org.siloserver.silo.updates.SiloraUpdates
import org.siloserver.silo.updates.UpdateState
import org.siloserver.silo.updates.UpdateStatus

@Composable
internal fun TvUpdatesSettingsPane(firstFocusRequester: FocusRequester) {
    val context = LocalContext.current
    val updates = remember(context) { SiloraUpdates.get(context) }
    val state by updates.state.collectAsState()
    val shizuku by updates.shizukuState.collectAsState()
    var picker by remember { mutableStateOf<UpdatePicker?>(null) }

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
                onClick = { if (!state.busy) updates.checkNow() },
                focusRequester = firstFocusRequester,
            )
        }
        item { SettingsInfoRow(stringResource(R.string.updates_status), state.statusText(), singleLine = false) }
        if (state.release != null) {
            item {
                SettingsActionRow(
                    label = stringResource(R.string.updates_install_update),
                    onClick = { if (!state.busy) requestInstall(context, updates) },
                )
            }
        }
        item {
            SettingsValueRow(
                label = stringResource(R.string.updates_interval),
                value = intervalLabel(state.settings.intervalHours),
                onClick = { picker = UpdatePicker.Interval },
            )
        }
        item {
            SettingsToggleRow(
                label = stringResource(R.string.updates_automatic),
                checked = state.settings.automatic,
                enabled = state.settings.intervalHours > 0,
                onCheckedChange = { updates.setSettings(state.settings.copy(automatic = it)) },
            )
        }
        item {
            SettingsValueRow(
                label = stringResource(R.string.updates_install_method),
                value = methodLabel(state.settings.method),
                onClick = { picker = UpdatePicker.Method },
            )
        }
        if (state.settings.method == InstallMethod.SHIZUKU) {
            item { SettingsInfoRow("Shizuku", shizuku.help, singleLine = false) }
            if (!shizuku.ready) {
                item {
                    SettingsActionRow(stringResource(R.string.updates_authorize_shizuku), onClick = {
                        updates.requestShizukuPermission()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    })
                }
            }
        }
    }

    when (picker) {
        UpdatePicker.Interval -> TvSettingsPickerSheet(
            title = stringResource(R.string.updates_interval),
            options = SiloraUpdates.INTERVALS.map { PickerOption(it.toString(), intervalLabel(it)) },
            selectedId = state.settings.intervalHours.toString(),
            onSelect = { id ->
                updates.setSettings(state.settings.copy(intervalHours = id.toInt()))
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
}

private enum class UpdatePicker { Interval, Method }

@Composable
internal fun TvUpdateDialogs(updates: SiloraUpdates, ready: Boolean, launch: Int) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by updates.state.collectAsState()
    var offer by remember { mutableStateOf(false) }

    LaunchedEffect(updates, lifecycle) {
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
    LaunchedEffect(updates, lifecycle, ready, launch, state.release, state.busy, state.settings.intervalHours) {
        offer = false
        if (!ready || state.release == null || state.busy) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (true) {
                    val eligible = updates.shouldOffer(System.currentTimeMillis())
                    offer = eligible && (offer || updates.idleForPrompt())
                    delay(if (offer) 1_000L else 5_000L)
                }
            } finally {
                offer = false
            }
        }
    }

    val release = state.release
    if (offer && release != null && !state.busy) {
        val later = { updates.later(); offer = false }
        TvDialog(
            title = stringResource(R.string.updates_offer_title),
            message = stringResource(R.string.updates_offer_message, release.versionName),
            actions = listOf(
                TvDialogAction(stringResource(R.string.updates_later), later),
                TvDialogAction(stringResource(R.string.updates_install_now), onClick = {
                    offer = false
                    updates.later()
                    requestInstall(context, updates)
                }, style = TvDialogActionStyle.Primary),
            ),
            onDismissRequest = later,
        )
    }
}

private fun requestInstall(context: Context, updates: SiloraUpdates) {
    when (updates.state.value.settings.method) {
        InstallMethod.STANDARD -> {
            if (updates.canUseStandard()) updates.installNow()
            else if (runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            }.isFailure) {
                Toast.makeText(context, R.string.updates_permission, Toast.LENGTH_LONG).show()
            }
        }
        InstallMethod.SHIZUKU -> {
            if (updates.shizukuState.value.ready) updates.installNow()
            else updates.requestShizukuPermission()?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }
    }
}

@Composable
private fun intervalLabel(hours: Int): String =
    if (hours == 0) stringResource(R.string.updates_interval_off) else stringResource(R.string.updates_interval_hours, hours)

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
