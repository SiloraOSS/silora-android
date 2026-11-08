package org.siloserver.silo.tv.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.admin.AdminPlaybackSession
import org.siloserver.silo.admin.humanizeKey
import org.siloserver.silo.common.admin.AdminDashboardViewModel
import org.siloserver.silo.common.admin.AdminLogLevel
import org.siloserver.silo.common.admin.AdminLogStream
import org.siloserver.silo.common.admin.AdminLogsViewModel
import org.siloserver.silo.common.admin.AdminNotice
import org.siloserver.silo.common.admin.AdminRequestView
import org.siloserver.silo.common.admin.AdminRequestsViewModel
import org.siloserver.silo.common.admin.AdminTasksViewModel
import org.siloserver.silo.common.admin.AdminText
import org.siloserver.silo.common.ui.components.rememberProfileServerUrl
import org.siloserver.silo.model.request.MediaRequest
import org.siloserver.silo.model.request.requestPosterUrl
import org.siloserver.silo.network.resolveArtworkUrl
import org.siloserver.silo.tv.ui.components.TvDialog
import org.siloserver.silo.tv.ui.components.TvDialogAction
import org.siloserver.silo.tv.ui.components.TvDialogActionStyle
import org.siloserver.silo.tv.ui.components.TvDialogOption
import org.siloserver.silo.tv.ui.components.TvOptionDialog
import org.siloserver.silo.tv.ui.components.TvTextInputDialog
import org.siloserver.silo.tv.ui.theme.SiloOnSurface
import org.siloserver.silo.tv.ui.theme.SiloSecondaryText
import org.siloserver.silo.tv.ui.theme.SuccessGreen

/*
 * Fork: the hand-built admin screens on TV — dashboard and now playing,
 * request moderation, tasks and scans, and logs. Every action is a focusable
 * row; choices and confirmations open the TV dialogs.
 */

private val Warning = Color(0xFFFFC107)

@Composable
private fun NoticeToast(notice: AdminNotice?) {
    val context = LocalContext.current
    LaunchedEffect(notice?.id) {
        notice?.let { Toast.makeText(context, it.text, Toast.LENGTH_SHORT).show() }
    }
}

@Composable
private fun WhileVisible(setVisible: (Boolean) -> Unit) {
    DisposableEffect(Unit) {
        setVisible(true)
        onDispose { setVisible(false) }
    }
}

/** A choice between a few options (tabs, filters): one row that opens a picker. */
private fun <T> LazyListScope.pickerRow(
    label: String,
    current: String,
    options: List<Pair<T, String>>,
    onPick: (T) -> Unit,
    open: (TvPicker) -> Unit,
) {
    item {
        TvRow("$label: $current", "Press to change", chevron = false) {
            open(TvPicker(label, options.map { (value, title) -> title to { onPick(value) } }, current))
        }
    }
}

internal data class TvPicker(val title: String, val options: List<Pair<String, () -> Unit>>, val current: String)

@Composable
private fun PickerDialog(picker: TvPicker?, onDismiss: () -> Unit) {
    picker ?: return
    TvOptionDialog(
        title = picker.title,
        options = picker.options.map { (title, pick) ->
            TvDialogOption(key = title, title = title, selected = title == picker.current) { onDismiss(); pick() }
        },
        onDismiss = onDismiss,
    )
}

/** One session or request's action menu. */
@Composable
private fun ActionsDialog(title: String?, actions: List<TvDialogOption>, onDismiss: () -> Unit) {
    if (title == null) return
    TvOptionDialog(title = title, options = actions, onDismiss = onDismiss)
}

// ---------------------------------------------------------------- Dashboard

@Composable
internal fun TvAdminDashboardContent(focus: FocusRequester, onOpenRequests: () -> Unit, viewModel: AdminDashboardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    WhileVisible(viewModel::setVisible)
    NoticeToast(state.notice)
    val serverUrl = rememberProfileServerUrl()
    var menuFor by remember { mutableStateOf<AdminPlaybackSession?>(null) }
    var messageFor by remember { mutableStateOf<AdminPlaybackSession?>(null) }
    var stopFor by remember { mutableStateOf<AdminPlaybackSession?>(null) }
    var confirmRestart by remember { mutableStateOf(false) }
    ReclaimPageFocus(
        focus,
        key = state.loading to state.sessions.map { it.sessionId },
        enabled = menuFor == null && messageFor == null && stopFor == null && !confirmRestart,
    )

    if (state.loading && state.status == null) {
        Notice("Loading…", null)
        return
    }
    Page(focus) {
        state.error?.let { item { Banner(it, Destructive) } }
        section("Now playing (${state.sessions.size})")
        if (state.sessions.isEmpty()) item { TvRow("Nobody is watching right now", null, chevron = false) { viewModel.refresh() } }
        items(state.sessions, key = { it.sessionId }) { session ->
            TvSessionRow(session, serverUrl, busy = state.busySessionId == session.sessionId) {
                if (session.hasPlaybackControl) menuFor = session
            }
        }
        state.requestCounts?.takeIf { it.needsApproval > 0 }?.let { counts ->
            item { TvRow("${counts.needsApproval} request${if (counts.needsApproval == 1) "" else "s"} waiting for approval", "Open Requests", onClick = onOpenRequests) }
        }
        state.status?.let { status ->
            val health = status.health
            val healthy = health.postgres?.ok != false && health.redis?.ok != false
            section("Server")
            item {
                StatBlock(
                    title = (if (healthy) "Healthy" else "Needs attention") + (state.build?.display?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    titleColor = if (healthy) SuccessGreen else Destructive,
                    lines = listOfNotNull(
                        listOfNotNull(
                            AdminText.uptime(status.startedAt)?.let { "Up $it" },
                            "Database ${if (health.postgres?.ok != false) "OK" else "down"}",
                            health.redis?.takeIf { it.configured }?.let { "Cache ${if (it.ok) "OK" else "down"}" },
                        ).joinToString(" · "),
                        "${health.errors24h} errors and ${health.warnings24h} warnings in the last 24 hours",
                        if (status.restartRequested) "Restart requested."
                        else if (status.restartRequired) "Restart needed" + (status.restartRequiredReasons.ifEmpty { listOfNotNull(status.restartRequiredReason) }
                            .takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ") ?: ".")
                        else null,
                    ),
                )
            }
            item { TvRow(if (state.restarting) "Restarting…" else "Restart server", null, destructive = true, chevron = false) { if (!state.restarting) confirmRestart = true } }
        }
        state.stats?.let { stats ->
            section("Library")
            item {
                TvTiles(
                    "Streams" to stats.activeStreams.toString(),
                    "Movies" to AdminText.count(stats.totalMovies),
                    "Shows" to AdminText.count(stats.totalShows),
                    "Files" to AdminText.count(stats.totalFiles),
                    "Storage" to AdminText.bytes(stats.totalStorageBytes),
                    "Users" to AdminText.count(stats.totalUsers),
                )
            }
        }
        state.resources?.takeIf { it.available }?.system?.let { system ->
            section("Resources")
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp)) {
                    TvMeter("CPU", system.cpuPct / 100f, "${system.cpuPct}% · load ${"%.2f".format(system.load1)} · ${system.cores} cores")
                    TvMeter(
                        "Memory",
                        if (system.memTotalMb > 0) system.memUsedMb.toFloat() / system.memTotalMb else 0f,
                        "${AdminText.gigabytes(system.memUsedMb / 1024.0)} of ${AdminText.gigabytes(system.memTotalMb / 1024.0)}",
                    )
                    state.resources?.gpu.orEmpty().forEach { gpu ->
                        gpu.totalBusyPct?.let { TvMeter(gpu.vendor?.uppercase() ?: "GPU", it / 100f, "$it% · ${gpu.sessions} sessions") }
                    }
                    system.disks.filterNot { it.unavailable || it.totalGb <= 0 }.forEach { disk ->
                        TvMeter(disk.path, (disk.usedGb / disk.totalGb).toFloat(), "${AdminText.gigabytes(disk.usedGb)} of ${AdminText.gigabytes(disk.totalGb)}")
                    }
                    Text("Network ↓ ${AdminText.bitsPerSecond(system.netRxBps)} ↑ ${AdminText.bitsPerSecond(system.netTxBps)}", color = SiloSecondaryText, fontSize = 15.sp)
                }
            }
        }
        item { TvRow("Refresh", null, chevron = false) { viewModel.refresh() } }
    }

    menuFor?.let { session ->
        ActionsDialog(
            title = session.displayTitle,
            actions = listOf(
                TvDialogOption(key = "pause", title = if (session.isPaused) "Resume" else "Pause") {
                    menuFor = null
                    if (session.isPaused) viewModel.resume(session) else viewModel.pause(session)
                },
                TvDialogOption(key = "message", title = "Send a message") { menuFor = null; messageFor = session },
                TvDialogOption(key = "stop", title = "Stop playback") { menuFor = null; stopFor = session },
            ),
            onDismiss = { menuFor = null },
        )
    }
    messageFor?.let { session ->
        TvTextInputDialog(
            title = "Message ${session.username}",
            label = "Shown on their screen",
            confirmLabel = "Send",
            onConfirm = { text -> messageFor = null; viewModel.message(session, text) },
            onDismiss = { messageFor = null },
        )
    }
    stopFor?.let { session ->
        TvDialog(
            title = "Stop playback?",
            message = "${session.username} stops watching ${session.displayTitle}.",
            onDismissRequest = { stopFor = null },
            actions = listOf(
                TvDialogAction("Cancel", { stopFor = null }),
                TvDialogAction("Stop", { stopFor = null; viewModel.stop(session) }, TvDialogActionStyle.Destructive),
            ),
            initialFocusIndex = 0,
        )
    }
    if (confirmRestart) {
        TvDialog(
            title = "Restart the server?",
            message = "Everyone watching is interrupted while the server restarts.",
            onDismissRequest = { confirmRestart = false },
            actions = listOf(
                TvDialogAction("Cancel", { confirmRestart = false }),
                TvDialogAction("Restart", { confirmRestart = false; viewModel.restartServer() }, TvDialogActionStyle.Destructive),
            ),
            initialFocusIndex = 0,
        )
    }
}

@Composable
private fun TvSessionRow(session: AdminPlaybackSession, serverUrl: String, busy: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val poster = session.posterUrl?.takeIf { it.isNotBlank() }?.let { resolveArtworkUrl(it, serverUrl) }
        Box(Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
            if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f)) {
            TvRow(
                title = session.displayTitle + (session.episodeLine?.let { " · $it" } ?: "") + if (busy) " · working…" else "",
                subtitle = listOf(
                    AdminText.sessionDetail(session),
                    AdminText.sessionStream(session),
                    AdminText.sessionProgress(session) + session.progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty(),
                ).filter { it.isNotBlank() }.joinToString("\n"),
                chevron = session.hasPlaybackControl,
                onClick = onClick,
            )
        }
    }
}

// ---------------------------------------------------------------- Requests

@Composable
internal fun TvAdminRequestsContent(focus: FocusRequester, viewModel: AdminRequestsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    NoticeToast(state.notice)
    var picker by remember { mutableStateOf<TvPicker?>(null) }
    var menuFor by remember { mutableStateOf<MediaRequest?>(null) }
    var declineFor by remember { mutableStateOf<MediaRequest?>(null) }
    var cancelFor by remember { mutableStateOf<MediaRequest?>(null) }
    ReclaimPageFocus(
        focus,
        key = state.loading to state.items.map { it.id },
        enabled = picker == null && menuFor == null && declineFor == null && cancelFor == null,
    )

    Page(focus) {
        pickerRow(
            "Showing",
            state.view.title + (state.count(state.view)?.let { " ($it)" } ?: ""),
            AdminRequestView.entries.map { it to it.title + (state.count(it)?.let { c -> " ($c)" } ?: "") },
            viewModel::select,
        ) { picker = it }
        state.error?.let { item { Banner(it, Destructive) } }
        if (state.loading && state.items.isEmpty()) item { Footnote("Loading…") }
        else if (state.items.isEmpty()) item { Footnote("No requests here.") }
        items(state.items, key = { it.id }) { request ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 60.dp, height = 90.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
                    requestPosterUrl(request.posterPath)?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    TvRow(
                        title = request.title + if (state.busyId == request.id) " · working…" else "",
                        subtitle = AdminText.requestLine(request, state.usernames) + "\n" + AdminText.requestStatus(request),
                    ) { menuFor = request }
                }
            }
        }
        if (state.nextCursor != null) item { TvRow(if (state.loadingMore) "Loading…" else "Load more", null, chevron = false) { viewModel.loadMore() } }
    }

    PickerDialog(picker) { picker = null }
    menuFor?.let { request ->
        val actions = buildList {
            if (AdminText.canApprove(request)) add(TvDialogOption(key = "approve", title = "Approve") { menuFor = null; viewModel.approve(request) })
            if (AdminText.canDecline(request)) add(TvDialogOption(key = "decline", title = "Decline…") { menuFor = null; declineFor = request })
            if (AdminText.canRetry(request)) add(TvDialogOption(key = "retry", title = "Retry") { menuFor = null; viewModel.retry(request) })
            if (AdminText.canCancel(request)) add(TvDialogOption(key = "cancel", title = "Cancel request…") { menuFor = null; cancelFor = request })
        }
        if (actions.isEmpty()) {
            TvDialog(
                title = request.title,
                message = AdminText.requestStatus(request),
                onDismissRequest = { menuFor = null },
                actions = listOf(TvDialogAction("Close", { menuFor = null })),
            )
        } else ActionsDialog(request.title, actions) { menuFor = null }
    }
    declineFor?.let { request ->
        TvTextInputDialog(
            title = "Decline ${request.title}?",
            label = "Reason (optional, shown to the requester)",
            confirmLabel = "Decline",
            allowBlank = true,
            onConfirm = { reason -> declineFor = null; viewModel.decline(request, reason) },
            onDismiss = { declineFor = null },
        )
    }
    cancelFor?.let { request ->
        TvDialog(
            title = "Cancel ${request.title}?",
            message = "The request stops and its downloads are withdrawn.",
            onDismissRequest = { cancelFor = null },
            actions = listOf(
                TvDialogAction("Keep", { cancelFor = null }),
                TvDialogAction("Cancel request", { cancelFor = null; viewModel.cancel(request) }, TvDialogActionStyle.Destructive),
            ),
            initialFocusIndex = 0,
        )
    }
}

// ---------------------------------------------------------------- Tasks and scans

@Composable
internal fun TvAdminTasksContent(focus: FocusRequester, viewModel: AdminTasksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    WhileVisible(viewModel::setVisible)
    NoticeToast(state.notice)
    var confirmCancelScans by remember { mutableStateOf(false) }
    ReclaimPageFocus(focus, key = state.loading, enabled = !confirmCancelScans)

    if (state.loading && state.tasks.isEmpty()) {
        Notice("Loading…", null)
        return
    }
    Page(focus) {
        state.error?.let { item { Banner(it, Destructive) } }
        section("Library scans")
        item { TvRow("Scan all libraries", "Look for new, changed and removed files", chevron = false) { viewModel.scanAll() } }
        items(state.libraries, key = { "lib:" + it.id }) { library ->
            TvRow(
                title = "Scan ${library.name}",
                subtitle = listOfNotNull(
                    listOfNotNull(
                        humanizeKey(library.type),
                        AdminText.ago(library.lastScannedAt)?.let { "scanned $it" } ?: "never scanned",
                        if (!library.enabled) "disabled" else null,
                    ).joinToString(" · "),
                    library.scanWarningMessage?.takeIf { it.isNotBlank() },
                ).joinToString("\n"),
                chevron = false,
            ) { viewModel.scan(library) }
        }
        item { TvRow("Cancel running scans", null, destructive = true, chevron = false) { confirmCancelScans = true } }
        section("Scheduled tasks")
        items(state.tasks, key = { "task:" + it.key }) { task ->
            TvRow(
                title = (if (task.isRunning) "Cancel " else "Run ") + task.name,
                subtitle = listOf(AdminText.taskLine(task), task.description).filter { it.isNotBlank() }.joinToString("\n"),
                destructive = task.isRunning,
                chevron = false,
            ) {
                if (task.isRunning) { if (task.state != "cancelling") viewModel.cancel(task) } else viewModel.run(task)
            }
        }
    }
    if (confirmCancelScans) {
        TvDialog(
            title = "Cancel running scans?",
            message = "Every library's running scan stops. Files found so far are kept.",
            onDismissRequest = { confirmCancelScans = false },
            actions = listOf(
                TvDialogAction("Keep scanning", { confirmCancelScans = false }),
                TvDialogAction("Cancel scans", { confirmCancelScans = false; viewModel.cancelAllScans() }, TvDialogActionStyle.Destructive),
            ),
            initialFocusIndex = 0,
        )
    }
}

// ---------------------------------------------------------------- Logs

@Composable
internal fun TvAdminLogsContent(focus: FocusRequester, viewModel: AdminLogsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    var picker by remember { mutableStateOf<TvPicker?>(null) }
    var searching by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    ReclaimPageFocus(focus, key = state.loading to state.stream, enabled = picker == null && !searching && detail == null)

    Page(focus) {
        pickerRow("Log", state.stream.title, AdminLogStream.entries.map { it to it.title }, viewModel::setStream) { picker = it }
        if (state.stream == AdminLogStream.App) {
            pickerRow("Level", state.level.title, AdminLogLevel.entries.map { it to it.title }, viewModel::setLevel) { picker = it }
        }
        item {
            TvRow(
                if (state.query.isBlank()) (if (state.stream == AdminLogStream.App) "Search messages" else "Filter by path") else "Search: ${state.query}",
                if (state.query.isBlank()) null else "Press to change",
                chevron = false,
            ) { searching = true }
        }
        state.error?.let { item { Banner(it, Destructive) } }
        if (state.loading) item { Footnote("Loading…") }
        when (state.stream) {
            AdminLogStream.App -> {
                if (!state.loading && state.app.isEmpty()) item { Footnote("No log entries match.") }
                items(state.app, key = { "app:" + it.id }) { entry ->
                    TvLogRow(entry.level, listOfNotNull(AdminText.time(entry.timestamp), entry.component.takeIf { it.isNotBlank() }).joinToString(" · "), entry.message) {
                        detail = entry.message to (entry.attrs?.let { org.siloserver.silo.admin.AdminDisplay.prettyJson(it) } ?: "")
                    }
                }
            }
            AdminLogStream.Audit -> {
                if (!state.loading && state.audit.isEmpty()) item { Footnote("No audit entries match.") }
                items(state.audit, key = { "audit:" + it.id }) { entry ->
                    val level = if (entry.statusCode >= 500) "error" else if (entry.statusCode >= 400) "warn" else "info"
                    TvLogRow(
                        level,
                        listOfNotNull(AdminText.time(entry.timestamp), entry.userId?.let { "user $it" }, entry.clientIp.takeIf { it.isNotBlank() }).joinToString(" · "),
                        listOfNotNull(entry.action?.takeIf { it.isNotBlank() }, "${entry.method} ${entry.path} → ${entry.statusCode}").joinToString(" · "),
                    ) {}
                }
            }
        }
        if (state.nextCursor != null && !state.loading) item { TvRow(if (state.loadingMore) "Loading…" else "Older entries", null, chevron = false) { viewModel.loadMore() } }
    }

    PickerDialog(picker) { picker = null }
    if (searching) {
        TvTextInputDialog(
            title = if (state.stream == AdminLogStream.App) "Search messages" else "Path starts with",
            label = "Leave empty to show everything",
            confirmLabel = "Search",
            initialValue = state.query,
            allowBlank = true,
            onConfirm = { text -> searching = false; viewModel.search(text) },
            onDismiss = { searching = false },
        )
    }
    detail?.let { (message, attrs) ->
        TvDialog(
            title = "Log entry",
            message = (message + if (attrs.isNotBlank()) "\n\n$attrs" else "").take(2_000),
            onDismissRequest = { detail = null },
            actions = listOf(TvDialogAction("Close", { detail = null })),
        )
    }
}

@Composable
private fun TvLogRow(level: String, head: String, message: String, onClick: () -> Unit) {
    val color = when (level.lowercase()) {
        "error", "fatal" -> Destructive
        "warn", "warning" -> Warning
        "debug" -> SiloSecondaryText
        else -> SuccessGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            TvRow(message.lineSequence().firstOrNull().orEmpty().take(200), level.uppercase() + "  " + head, chevron = false, onClick = onClick)
        }
    }
}

// ---------------------------------------------------------------- Pieces

@Composable
private fun StatBlock(title: String, titleColor: Color, lines: List<String>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp)) {
        Text(title, color = titleColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        lines.forEach { Text(it, color = if (it.startsWith("Restart")) Warning else SiloSecondaryText, fontSize = 16.sp) }
    }
}

@Composable
private fun TvTiles(vararg tiles: Pair<String, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(16.dp)) {
                Text(value, color = SiloOnSurface, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = SiloSecondaryText, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun TvMeter(label: String, fraction: Float, detail: String) {
    val f = fraction.coerceIn(0f, 1f)
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, color = SiloOnSurface, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = SiloSecondaryText, fontSize = 15.sp)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Box(
                Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(when { f > 0.9f -> Destructive; f > 0.75f -> Warning; else -> SuccessGreen }),
            )
        }
    }
}
