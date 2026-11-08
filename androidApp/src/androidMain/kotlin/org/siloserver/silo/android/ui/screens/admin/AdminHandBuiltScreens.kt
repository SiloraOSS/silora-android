package org.siloserver.silo.android.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.admin.AdminPlaybackSession
import org.siloserver.silo.android.ui.components.SiloConfirmDialog
import org.siloserver.silo.android.ui.theme.SiloDestructive
import org.siloserver.silo.android.ui.theme.SiloGroupedCell
import org.siloserver.silo.android.ui.theme.SiloIconTile
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSuccess
import org.siloserver.silo.android.ui.theme.SiloWarning
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

/*
 * Fork: the hand-built admin screens on the phone — dashboard and now
 * playing, request moderation, tasks and scans, and logs. Everything else in
 * the console is generated from the server's spec.
 */

@Composable
private fun NoticeToast(notice: AdminNotice?) {
    val context = LocalContext.current
    LaunchedEffect(notice?.id) {
        notice?.let { Toast.makeText(context, it.text, Toast.LENGTH_SHORT).show() }
    }
}

/** Refreshes while shown; stops when the screen leaves. */
@Composable
private fun WhileVisible(setVisible: (Boolean) -> Unit) {
    DisposableEffect(Unit) {
        setVisible(true)
        onDispose { setVisible(false) }
    }
}

// ---------------------------------------------------------------- Dashboard

@Composable
fun AdminDashboardContent(onOpenRequests: () -> Unit, viewModel: AdminDashboardViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    WhileVisible(viewModel::setVisible)
    NoticeToast(state.notice)
    var confirmRestart by remember { mutableStateOf(false) }
    var stopTarget by remember { mutableStateOf<AdminPlaybackSession?>(null) }
    var messageTarget by remember { mutableStateOf<AdminPlaybackSession?>(null) }
    val serverUrl = rememberProfileServerUrl()

    if (state.loading && state.status == null) {
        Loading()
        return
    }
    Page {
        state.error?.let { item { ErrorBanner(it) } }
        val status = state.status
        if (status != null) {
            section("Server")
            item {
                Card {
                    val health = status.health
                    val healthy = health.postgres?.ok != false && health.redis?.ok != false
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (healthy) SiloSuccess else SiloDestructive)
                        Spacer(Modifier.width(8.dp))
                        Text(if (healthy) "Healthy" else "Needs attention", color = SiloOnSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        state.build?.display?.takeIf { it.isNotBlank() }?.let { Text(it, color = SiloSecondaryText, fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            AdminText.uptime(status.startedAt)?.let { "Up $it" },
                            "Database ${if (health.postgres?.ok != false) "OK" else "down"}",
                            health.redis?.takeIf { it.configured }?.let { "Cache ${if (it.ok) "OK" else "down"}" },
                        ).joinToString(" · "),
                        color = SiloSecondaryText,
                        fontSize = 14.sp,
                    )
                    Text("${health.errors24h} errors and ${health.warnings24h} warnings in the last 24 hours", color = SiloSecondaryText, fontSize = 14.sp)
                    if (status.restartRequired || status.restartRequested) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (status.restartRequested) "Restart requested."
                            else "Restart needed" + (status.restartRequiredReasons.ifEmpty { listOfNotNull(status.restartRequiredReason) }
                                .takeIf { it.isNotEmpty() }?.joinToString(prefix = ": ") ?: "."),
                            color = SiloWarning,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { confirmRestart = true }, enabled = !state.restarting) {
                        Text(if (state.restarting) "Restarting…" else "Restart server")
                    }
                }
            }
        }
        state.requestCounts?.takeIf { it.needsApproval > 0 }?.let { counts ->
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(SiloWarning.copy(alpha = 0.15f)).clickable(onClick = onOpenRequests).fillMaxWidth().padding(14.dp)) {
                    Text("${counts.needsApproval} request${if (counts.needsApproval == 1) "" else "s"} waiting for approval ›", color = SiloOnSurface, fontSize = 15.sp)
                }
            }
        }
        state.stats?.let { stats ->
            section("Library")
            item {
                TileRow(
                    "Streams" to stats.activeStreams.toString(),
                    "Movies" to AdminText.count(stats.totalMovies),
                    "Shows" to AdminText.count(stats.totalShows),
                )
            }
            item {
                TileRow(
                    "Files" to AdminText.count(stats.totalFiles),
                    "Storage" to AdminText.bytes(stats.totalStorageBytes),
                    "Users" to AdminText.count(stats.totalUsers),
                )
            }
        }
        state.resources?.takeIf { it.available }?.system?.let { system ->
            section("Resources" + if (state.resources?.stale == true) " (stale)" else "")
            item {
                Card {
                    Meter("CPU", system.cpuPct / 100f, "${system.cpuPct}% · load ${"%.2f".format(system.load1)} · ${system.cores} cores")
                    val memFraction = if (system.memTotalMb > 0) system.memUsedMb.toFloat() / system.memTotalMb else 0f
                    Meter("Memory", memFraction, "${AdminText.gigabytes(system.memUsedMb / 1024.0)} of ${AdminText.gigabytes(system.memTotalMb / 1024.0)}")
                    Text(
                        "Network ↓ ${AdminText.bitsPerSecond(system.netRxBps)} ↑ ${AdminText.bitsPerSecond(system.netTxBps)}",
                        color = SiloSecondaryText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    state.resources?.gpu.orEmpty().forEach { gpu ->
                        gpu.totalBusyPct?.let { busy ->
                            Meter(gpu.vendor?.uppercase() ?: "GPU", busy / 100f, "$busy% · ${gpu.sessions} sessions")
                        }
                    }
                    system.disks.filterNot { it.unavailable || it.totalGb <= 0 }.forEach { disk ->
                        Meter(disk.path, (disk.usedGb / disk.totalGb).toFloat(), "${AdminText.gigabytes(disk.usedGb)} of ${AdminText.gigabytes(disk.totalGb)}")
                    }
                }
            }
        }
        section("Now playing (${state.sessions.size})")
        if (state.sessions.isEmpty()) item { Footnote("Nobody is watching right now.") }
        items(state.sessions, key = { it.sessionId }) { session ->
            SessionCard(
                session = session,
                serverUrl = serverUrl,
                busy = state.busySessionId == session.sessionId,
                onPauseResume = { if (session.isPaused) viewModel.resume(session) else viewModel.pause(session) },
                onStop = { stopTarget = session },
                onMessage = { messageTarget = session },
            )
        }
    }

    if (confirmRestart) {
        SiloConfirmDialog(
            title = "Restart the server?",
            body = "Everyone watching is interrupted while the server restarts.",
            confirmLabel = "Restart",
            onConfirm = { confirmRestart = false; viewModel.restartServer() },
            onDismiss = { confirmRestart = false },
        )
    }
    stopTarget?.let { session ->
        SiloConfirmDialog(
            title = "Stop playback?",
            body = "${session.username} stops watching ${session.displayTitle}.",
            confirmLabel = "Stop",
            onConfirm = { stopTarget = null; viewModel.stop(session) },
            onDismiss = { stopTarget = null },
        )
    }
    messageTarget?.let { session ->
        TextPrompt(
            title = "Message ${session.username}",
            label = "Shown on their screen",
            confirm = "Send",
            onConfirm = { text -> messageTarget = null; viewModel.message(session, text) },
            onDismiss = { messageTarget = null },
        )
    }
}

@Composable
private fun SessionCard(
    session: AdminPlaybackSession,
    serverUrl: String,
    busy: Boolean,
    onPauseResume: () -> Unit,
    onStop: () -> Unit,
    onMessage: () -> Unit,
) {
    Card {
        Row {
            val poster = session.posterUrl?.takeIf { it.isNotBlank() }?.let { resolveArtworkUrl(it, serverUrl) }
            Box(Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(6.dp)).background(SiloIconTile)) {
                if (poster != null) AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSizeCompat())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.displayTitle, color = SiloOnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                session.episodeLine?.let { Text(it, color = SiloSecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(AdminText.sessionDetail(session), color = SiloSecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    AdminText.sessionStream(session),
                    color = if (session.isTranscoding) SiloWarning else SiloSecondaryText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        session.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(3.dp)) }
        Text(AdminText.sessionProgress(session), color = SiloSecondaryText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (session.hasPlaybackControl) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPauseResume, enabled = !busy) { Text(if (session.isPaused) "Resume" else "Pause") }
                TextButton(onClick = onMessage, enabled = !busy) { Text("Message") }
                TextButton(onClick = onStop, enabled = !busy) { Text("Stop", color = SiloDestructive) }
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }
    }
}

// ---------------------------------------------------------------- Requests

@Composable
fun AdminRequestsContent(viewModel: AdminRequestsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    NoticeToast(state.notice)
    var declineTarget by remember { mutableStateOf<MediaRequest?>(null) }
    var cancelTarget by remember { mutableStateOf<MediaRequest?>(null) }

    Column {
        ChipRow(
            options = AdminRequestView.entries.map { view -> view to (view.title + (state.count(view)?.let { " ($it)" } ?: "")) },
            selected = state.view,
            onSelect = viewModel::select,
        )
        Page {
            state.error?.let { item { ErrorBanner(it) } }
            if (state.loading && state.items.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            else if (state.items.isEmpty()) item { Footnote("No requests here.") }
            items(state.items, key = { it.id }) { request ->
                RequestCard(
                    request = request,
                    usernames = state.usernames,
                    busy = state.busyId == request.id,
                    onApprove = { viewModel.approve(request) },
                    onDecline = { declineTarget = request },
                    onRetry = { viewModel.retry(request) },
                    onCancel = { cancelTarget = request },
                )
            }
            if (state.nextCursor != null) item {
                OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (state.loadingMore) "Loading…" else "Load more")
                }
            }
        }
    }
    declineTarget?.let { request ->
        TextPrompt(
            title = "Decline ${request.title}?",
            label = "Reason (optional, shown to the requester)",
            confirm = "Decline",
            allowBlank = true,
            onConfirm = { reason -> declineTarget = null; viewModel.decline(request, reason) },
            onDismiss = { declineTarget = null },
        )
    }
    cancelTarget?.let { request ->
        SiloConfirmDialog(
            title = "Cancel ${request.title}?",
            body = "The request stops and its downloads are withdrawn.",
            confirmLabel = "Cancel request",
            dismissLabel = "Keep",
            onConfirm = { cancelTarget = null; viewModel.cancel(request) },
            onDismiss = { cancelTarget = null },
        )
    }
}

@Composable
private fun RequestCard(
    request: MediaRequest,
    usernames: Map<String, String>,
    busy: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card {
        Row {
            Box(Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(6.dp)).background(SiloIconTile)) {
                requestPosterUrl(request.posterPath)?.let {
                    AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSizeCompat())
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(request.title, color = SiloOnSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(AdminText.requestLine(request, usernames), color = SiloSecondaryText, fontSize = 13.sp)
                Text(
                    AdminText.requestStatus(request),
                    color = if (request.outcome == "failed") SiloDestructive else SiloSecondaryText,
                    fontSize = 13.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (AdminText.canApprove(request)) TextButton(onClick = onApprove, enabled = !busy) { Text("Approve", color = SiloSuccess) }
            if (AdminText.canDecline(request)) TextButton(onClick = onDecline, enabled = !busy) { Text("Decline", color = SiloDestructive) }
            if (AdminText.canRetry(request)) TextButton(onClick = onRetry, enabled = !busy) { Text("Retry") }
            if (AdminText.canCancel(request)) TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel", color = SiloDestructive) }
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

// ---------------------------------------------------------------- Tasks and scans

@Composable
fun AdminTasksContent(viewModel: AdminTasksViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    WhileVisible(viewModel::setVisible)
    NoticeToast(state.notice)
    var cancelScanAll by remember { mutableStateOf(false) }

    if (state.loading && state.tasks.isEmpty()) {
        Loading()
        return
    }
    Page {
        state.error?.let { item { ErrorBanner(it) } }
        section("Library scans")
        item {
            Card {
                Text("Look for new, changed and removed files.", color = SiloSecondaryText, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::scanAll, enabled = state.busyKey == null) { Text("Scan all libraries") }
                    TextButton(onClick = { cancelScanAll = true }, enabled = state.busyKey == null && state.libraries.isNotEmpty()) {
                        Text("Cancel scans", color = SiloDestructive)
                    }
                }
            }
        }
        items(state.libraries, key = { "lib:" + it.id }) { library ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(library.name, color = SiloOnSurface, fontSize = 16.sp)
                        Text(
                            listOfNotNull(
                                org.siloserver.silo.admin.humanizeKey(library.type),
                                AdminText.ago(library.lastScannedAt)?.let { "scanned $it" } ?: "never scanned",
                                if (!library.enabled) "disabled" else null,
                            ).joinToString(" · "),
                            color = SiloSecondaryText,
                            fontSize = 13.sp,
                        )
                        library.scanWarningMessage?.takeIf { it.isNotBlank() }?.let { Text(it, color = SiloWarning, fontSize = 13.sp) }
                    }
                    TextButton(onClick = { viewModel.scan(library) }, enabled = state.busyKey == null) { Text("Scan") }
                }
            }
        }
        section("Scheduled tasks")
        items(state.tasks, key = { "task:" + it.key }) { task ->
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.name, color = SiloOnSurface, fontSize = 16.sp)
                        Text(AdminText.taskLine(task), color = if (task.lastExecution?.status == "failed" && !task.isRunning) SiloDestructive else SiloSecondaryText, fontSize = 13.sp)
                        if (task.description.isNotBlank()) Text(task.description, color = SiloSecondaryText, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (task.isRunning) {
                        TextButton(onClick = { viewModel.cancel(task) }, enabled = state.busyKey == null && task.state != "cancelling") { Text("Cancel", color = SiloDestructive) }
                    } else {
                        TextButton(onClick = { viewModel.run(task) }, enabled = state.busyKey == null) { Text("Run") }
                    }
                }
                if (task.isRunning) LinearProgressIndicator(progress = { AdminText.taskProgress(task) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp))
            }
        }
    }
    if (cancelScanAll) {
        SiloConfirmDialog(
            title = "Cancel running scans?",
            body = "Every library's running scan stops. Files found so far are kept.",
            confirmLabel = "Cancel scans",
            dismissLabel = "Keep scanning",
            onConfirm = {
                cancelScanAll = false
                viewModel.cancelAllScans()
            },
            onDismiss = { cancelScanAll = false },
        )
    }
}

// ---------------------------------------------------------------- Logs

@Composable
fun AdminLogsContent(viewModel: AdminLogsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf(state.query) }
    var expanded by remember { mutableStateOf<String?>(null) }

    Column {
        ChipRow(AdminLogStream.entries.map { it to it.title }, state.stream, viewModel::setStream)
        if (state.stream == AdminLogStream.App) ChipRow(AdminLogLevel.entries.map { it to it.title }, state.level, viewModel::setLevel)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(if (state.stream == AdminLogStream.App) "Search messages" else "Path starts with", color = SiloSecondaryText) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            textStyle = androidx.compose.ui.text.TextStyle(color = SiloOnSurface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Page {
            state.error?.let { item { ErrorBanner(it) } }
            if (state.loading) item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator() } }
            when (state.stream) {
                AdminLogStream.App -> {
                    if (!state.loading && state.app.isEmpty()) item { Footnote("No log entries match.") }
                    items(state.app, key = { "app:" + it.id }) { entry ->
                        val open = expanded == entry.id
                        LogRow(
                            level = entry.level,
                            head = listOfNotNull(AdminText.time(entry.timestamp), entry.component.takeIf { it.isNotBlank() }).joinToString(" · "),
                            message = entry.message,
                            detail = if (open) entry.attrs?.let { org.siloserver.silo.admin.AdminDisplay.prettyJson(it) } else null,
                            onClick = { expanded = if (open) null else entry.id },
                        )
                    }
                }
                AdminLogStream.Audit -> {
                    if (!state.loading && state.audit.isEmpty()) item { Footnote("No audit entries match.") }
                    items(state.audit, key = { "audit:" + it.id }) { entry ->
                        LogRow(
                            level = if (entry.statusCode >= 500) "error" else if (entry.statusCode >= 400) "warn" else "info",
                            head = listOfNotNull(AdminText.time(entry.timestamp), entry.userId?.let { "user $it" }, entry.clientIp.takeIf { it.isNotBlank() }).joinToString(" · "),
                            message = listOfNotNull(entry.action?.takeIf { it.isNotBlank() }, "${entry.method} ${entry.path} → ${entry.statusCode}").joinToString("\n"),
                            detail = null,
                            onClick = {},
                        )
                    }
                }
            }
            if (state.nextCursor != null && !state.loading) item {
                OutlinedButton(onClick = viewModel::loadMore, enabled = !state.loadingMore, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(if (state.loadingMore) "Loading…" else "Older entries")
                }
            }
        }
    }
}

@Composable
private fun LogRow(level: String, head: String, message: String, detail: String?, onClick: () -> Unit) {
    val color = when (level.lowercase()) {
        "error", "fatal" -> SiloDestructive
        "warn", "warning" -> SiloWarning
        "debug" -> SiloSecondaryText
        else -> SiloSuccess
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(level.uppercase() + "  " + head, color = SiloSecondaryText, fontSize = 12.sp)
            Text(message, color = SiloOnSurface, fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = if (detail != null) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, color = SiloSecondaryText, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

// ---------------------------------------------------------------- Pieces

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(SiloGroupedCell).padding(14.dp),
    ) { content() }
}

@Composable
private fun TileRow(vararg tiles: Pair<String, String>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { (label, value) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(SiloGroupedCell).padding(12.dp)) {
                Text(value, color = SiloOnSurface, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, color = SiloSecondaryText, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Meter(label: String, fraction: Float, detail: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, color = SiloOnSurface, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = SiloSecondaryText, fontSize = 13.sp)
        }
        val f = fraction.coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { f },
            color = when { f > 0.9f -> SiloDestructive; f > 0.75f -> SiloWarning; else -> SiloSuccess },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp),
        )
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = SiloSecondaryText,
                    selectedLabelColor = SiloOnSurface,
                    selectedContainerColor = SiloIconTile,
                ),
            )
        }
    }
}

@Composable
private fun TextPrompt(
    title: String,
    label: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowBlank: Boolean = false,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(label) }, minLines = 2, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = allowBlank || text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(80.dp))
