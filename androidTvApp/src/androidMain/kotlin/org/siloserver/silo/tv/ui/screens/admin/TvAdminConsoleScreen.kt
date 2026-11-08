package org.siloserver.silo.tv.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.onFocusChanged
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import kotlinx.serialization.json.JsonElement
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.admin.AdminBoundOperation
import org.siloserver.silo.admin.AdminCategory
import org.siloserver.silo.admin.AdminDisplay
import org.siloserver.silo.admin.AdminField
import org.siloserver.silo.admin.AdminFieldKind
import org.siloserver.silo.admin.AdminForm
import org.siloserver.silo.common.admin.AdminAvailability
import org.siloserver.silo.common.admin.AdminConsoleState
import org.siloserver.silo.common.admin.AdminConsoleViewModel
import org.siloserver.silo.common.admin.AdminEntry
import org.siloserver.silo.common.admin.AdminRunPhase
import org.siloserver.silo.tv.ui.components.TvDialog
import org.siloserver.silo.tv.ui.components.TvDialogAction
import org.siloserver.silo.tv.ui.components.TvDialogActionStyle
import org.siloserver.silo.tv.ui.components.TvDialogOption
import org.siloserver.silo.tv.ui.components.TvOptionDialog
import org.siloserver.silo.tv.ui.components.TvTextInputDialog
import org.siloserver.silo.tv.ui.theme.DarkBackground
import org.siloserver.silo.tv.ui.theme.SiloOnSurface
import org.siloserver.silo.tv.ui.theme.SiloSecondaryText
import org.siloserver.silo.tv.ui.theme.SuccessGreen

internal val Destructive = Color(0xFFEF4444)

/** Whether the current page's list holds focus; read by its initial focus claim. */
private var pageHasFocus by mutableStateOf(false)

/**
 * Puts focus back on the page's first row when [key] changes (data arrived,
 * or the focused row went away) and nothing on the page holds it. Skipped
 * while a dialog is open, which holds focus in its own window.
 */
@Composable
internal fun ReclaimPageFocus(focus: FocusRequester, key: Any?, enabled: Boolean) {
    LaunchedEffect(key, enabled) {
        if (!enabled || pageHasFocus) return@LaunchedEffect
        requestFocusUntilObserved(
            maxAttempts = 30,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = focus::requestFocus,
            isFocused = { pageHasFocus },
        )
    }
}

/** What a row does when picked; the screen's one dialog at a time. */
private sealed interface TvAdminDialog {
    data class EditText(val field: AdminField, val value: String) : TvAdminDialog
    data class EditRawBody(val value: String) : TvAdminDialog
    data class Choose(val field: AdminField, val options: List<String>, val value: String) : TvAdminDialog
}

/**
 * Fork: the admin console on TV, built from the same shared engine as the
 * phone's. Rows are D-pad focusable; text values open the TV text dialog.
 */
@Composable
fun TvAdminConsoleScreen(
    onClose: () -> Unit,
    onSwitchProfile: () -> Unit,
    viewModel: AdminConsoleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var dialog by remember { mutableStateOf<TvAdminDialog?>(null) }
    BackHandler(enabled = dialog == null) { if (!viewModel.back()) onClose() }
    val entry = state.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 96.dp, vertical = 40.dp),
    ) {
        Text(
            text = if (state.stack.size > 1) "Admin · ${entry.title}" else entry.title,
            color = SiloOnSurface,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.availability == AdminAvailability.NotAdmin -> Notice("This account isn't an administrator.", "Close" to onClose)
                state.availability == AdminAvailability.NeedsPrimaryProfile -> Notice(
                    "The server takes admin changes only from the account's main profile.",
                    "Switch profile" to onSwitchProfile,
                )
                state.catalog == null && state.loadError != null -> Notice(state.loadError.orEmpty(), "Try again" to viewModel::reload)
                state.catalog == null -> Notice("Loading…", null)
                else -> EntryContent(state, entry, viewModel) { dialog = it }
            }
        }
    }

    val run = entry as? AdminEntry.Run
    when (val d = dialog) {
        is TvAdminDialog.EditText -> TvTextInputDialog(
            title = d.field.label,
            label = d.field.help?.take(160) ?: when (d.field.kind) {
                AdminFieldKind.Lines -> "Separate values with commas"
                AdminFieldKind.JsonText -> "JSON"
                else -> d.field.label
            },
            confirmLabel = "Done",
            initialValue = if (d.field.kind == AdminFieldKind.Lines) d.value.lines().joinToString(", ") else d.value,
            allowBlank = true,
            keyboardType = when (d.field.kind) {
                AdminFieldKind.Integer -> KeyboardType.Number
                AdminFieldKind.Decimal -> KeyboardType.Decimal
                AdminFieldKind.Secret -> KeyboardType.Password
                else -> KeyboardType.Text
            },
            onConfirm = { text ->
                val value = if (d.field.kind == AdminFieldKind.Lines) {
                    text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                } else text
                run?.let { viewModel.setValue(it.id, d.field.key, value) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is TvAdminDialog.EditRawBody -> TvTextInputDialog(
            title = "Request body",
            label = "JSON",
            confirmLabel = "Done",
            initialValue = d.value,
            allowBlank = true,
            onConfirm = { text ->
                run?.let { viewModel.setValue(it.id, AdminForm.RAW_BODY_KEY, text) }
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is TvAdminDialog.Choose -> TvOptionDialog(
            title = d.field.label,
            options = buildList {
                if (!d.field.required) add(TvDialogOption(key = "", title = "Not set", selected = d.value.isBlank()) {
                    run?.let { viewModel.setValue(it.id, d.field.key, "") }
                    dialog = null
                })
                d.options.forEach { option ->
                    add(TvDialogOption(key = option, title = option, selected = option == d.value) {
                        run?.let { viewModel.setValue(it.id, d.field.key, option) }
                        dialog = null
                    })
                }
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    if (run != null && run.phase == AdminRunPhase.Confirming) {
        TvDialog(
            title = "${run.operation.title}?",
            message = run.operation.summary?.takeIf { it.isNotBlank() } ?: "This changes the server and may not be undoable.",
            onDismissRequest = { viewModel.cancelConfirm(run.id) },
            actions = listOf(
                TvDialogAction("Cancel", { viewModel.cancelConfirm(run.id) }),
                TvDialogAction("Continue", { viewModel.submit(run.id) }, TvDialogActionStyle.Destructive),
            ),
            initialFocusIndex = 0,
        )
    }
}

@Composable
private fun EntryContent(
    state: AdminConsoleState,
    entry: AdminEntry,
    vm: AdminConsoleViewModel,
    openDialog: (TvAdminDialog) -> Unit,
) {
    val catalog = state.catalog ?: return
    // A new screen (or a new phase of one) takes focus on its first row.
    val focusKey = state.stack.size to ((entry as? AdminEntry.Run)?.let { it.phase to it.response?.status } ?: Unit)
    val firstRow = remember(focusKey) { FocusRequester() }
    LaunchedEffect(focusKey) {
        pageHasFocus = false
        requestFocusUntilObserved(
            maxAttempts = 30,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = firstRow::requestFocus,
            isFocused = { pageHasFocus },
        )
    }

    when (entry) {
        AdminEntry.Home -> Page(firstRow) {
            section("Daily")
            item { TvRow("Dashboard", "Server health, numbers and now playing") { vm.openDashboard() } }
            item { TvRow("Requests", "Approve, decline and retry") { vm.openRequests() } }
            item { TvRow("Tasks and scans", "Run tasks, scan libraries") { vm.openTasks() } }
            item { TvRow("Logs", "Server log and admin audit trail") { vm.openLogs() } }
            val shortcuts = catalog.quickActions.filter {
                it.operation.resourceKey !in setOf("sessions", "requests", "tasks", "scan", "stats", "server", "logs")
            }
            if (shortcuts.isNotEmpty()) {
                section("Shortcuts")
                itemsIndexed(shortcuts) { i, action ->
                    TvRow(action.title, action.subtitle) { vm.openOperation(action.operation, query = action.query) }
                }
            }
            section("Everything")
            itemsIndexed(AdminCategory.entries.toList()) { i, category ->
                TvRow(category.title, category.blurb) { vm.openCategory(category) }
            }
            item { Footnote("Server API ${catalog.spec.version} · ${catalog.spec.operations.size} admin operations") }
        }
        is AdminEntry.Category -> Page(firstRow) {
            val resources = catalog.resourcesIn(entry.category)
            itemsIndexed(resources) { i, resource ->
                TvRow(resource.title, "${resource.operations.size} operations") { vm.openResource(resource.key) }
            }
        }
        is AdminEntry.Resource -> Page(firstRow) {
            val resource = catalog.resource(entry.key) ?: return@Page
            val primary = resource.primaryRead
            val ordered = listOfNotNull(primary) + resource.operations.filter { it !== primary }
            itemsIndexed(ordered) { i, op ->
                TvRow(
                    title = op.title,
                    subtitle = op.summary?.takeIf { it.isNotBlank() && !it.equals(op.title, true) } ?: "${op.method} ${op.relativePath}",
                    destructive = op.isDestructive,
                ) { vm.openOperation(op) }
            }
        }
        is AdminEntry.Record -> Page(firstRow) {
            val rows = AdminDisplay.rows(entry.value)
            if (entry.actions.isNotEmpty()) {
                section("Actions")
                itemsIndexed(entry.actions) { i, bound -> ActionRow(bound, Modifier) { vm.openBound(bound) } }
            }
            section("Details")
            itemsIndexed(rows) { i, row ->
                val nested = row.nested
                TvRow(row.label, row.text, chevron = nested != null) {
                    if (nested != null) vm.openNested(row.label, nested)
                }
            }
        }
        is AdminEntry.Run -> RunContent(entry, vm, openDialog, firstRow)
        AdminEntry.Dashboard -> TvAdminDashboardContent(firstRow, onOpenRequests = vm::openRequests)
        AdminEntry.Requests -> TvAdminRequestsContent(firstRow)
        AdminEntry.Tasks -> TvAdminTasksContent(firstRow)
        AdminEntry.Logs -> TvAdminLogsContent(firstRow)
    }
}

@Composable
private fun RunContent(
    run: AdminEntry.Run,
    vm: AdminConsoleViewModel,
    openDialog: (TvAdminDialog) -> Unit,
    firstRow: FocusRequester,
) {
    if (run.phase == AdminRunPhase.Preparing || (run.phase == AdminRunPhase.Sending && run.response == null)) {
        Notice("Working…", null)
        return
    }
    val showForm = run.phase == AdminRunPhase.Editing || run.phase == AdminRunPhase.Confirming
    if (showForm) {
        val fields = run.form.fields
        Page(firstRow) {
            run.error?.let { item { Banner(it, Destructive) } }
            item {
                TvRow(
                    title = if (run.operation.isRead) "Show results" else run.operation.title,
                    subtitle = "${run.operation.method} ${run.operation.relativePath}",
                    destructive = run.operation.isDestructive,
                    chevron = false,
                ) { vm.submit(run.id) }
            }
            run.operation.description?.takeIf { it.isNotBlank() }?.let { item { Footnote(it) } }
            if (run.form.bodyIsRawJson) {
                item {
                    val raw = run.values[AdminForm.RAW_BODY_KEY].orEmpty()
                    TvRow("Request body (JSON)", raw.ifBlank { "Not set" }) { openDialog(TvAdminDialog.EditRawBody(raw)) }
                }
            }
            itemsIndexed(fields) { i, field ->
                val value = run.values[field.key].orEmpty()
                val indent = (field.depth * 24).dp
                if (field.kind == AdminFieldKind.Group) {
                    Text(
                        field.label,
                        color = SiloOnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = indent + 8.dp, top = 18.dp, bottom = 6.dp),
                    )
                    return@itemsIndexed
                }
                val shown = when (field.kind) {
                    AdminFieldKind.Toggle -> when (value) { "true" -> "On"; "false" -> "Off"; else -> "Not set" }
                    AdminFieldKind.Secret -> if (value.isBlank()) "Not set" else "••••••"
                    AdminFieldKind.Lines -> value.lines().filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Not set" }
                    else -> value.ifBlank { "Not set" }
                }
                Box(Modifier.padding(start = indent)) {
                    TvRow(
                        title = field.label + if (field.required) " *" else "",
                        subtitle = listOfNotNull(shown, field.help?.take(140)).joinToString("\n"),
                        chevron = false,
                    ) {
                        when (val kind = field.kind) {
                            AdminFieldKind.Toggle -> vm.setValue(
                                run.id,
                                field.key,
                                when (value) { "true" -> "false"; "false" -> if (field.required) "true" else ""; else -> "true" },
                            )
                            is AdminFieldKind.Choice -> openDialog(TvAdminDialog.Choose(field, kind.options, value))
                            else -> openDialog(TvAdminDialog.EditText(field, value))
                        }
                    }
                }
            }
        }
        return
    }

    val response = run.response
    val actions = vm.detailActions(run)
    Page(firstRow) {
        run.error?.let { item { Banner(it, Destructive) } }
        if (!run.operation.isRead && response != null) item { Banner("Done (${response.status}).", SuccessGreen) }
        if (run.operation.isRead) {
            item { TvRow("Refresh", null, chevron = false) { vm.refresh(run.id) } }
        }
        if (run.form.hasInputs && response != null) {
            item {
                TvRow(if (run.operation.isRead) "Change filters" else "Edit and send again", null, chevron = false) { vm.edit(run.id) }
            }
        }
        if (run.isList) {
            if (run.items.isEmpty()) item { Footnote("Nothing here.") }
            itemsIndexed(run.items) { _, item ->
                TvRow(AdminDisplay.title(item), AdminDisplay.subtitle(item)) { vm.openItem(run.id, item) }
            }
            if (run.nextCursor != null) {
                item { TvRow(if (run.loadingMore) "Loading…" else "Load more", null, chevron = false) { vm.loadMore(run.id) } }
            }
        } else if (response != null) {
            if (actions.isNotEmpty()) {
                section("Actions")
                itemsIndexed(actions) { _, bound -> ActionRow(bound, Modifier) { vm.openBound(bound) } }
            }
            recordRows(response.body) { title, value -> vm.openNested(title, value) }
        }
    }
}

private fun LazyListScope.recordRows(value: JsonElement?, onNested: (String, JsonElement) -> Unit) {
    val rows = AdminDisplay.rows(value)
    if (rows.isEmpty()) {
        item { Footnote("No content.") }
        return
    }
    section("Details")
    itemsIndexed(rows) { _, row ->
        val nested = row.nested
        TvRow(row.label, row.text, chevron = nested != null) { if (nested != null) onNested(row.label, nested) }
    }
}

@Composable
internal fun Page(focus: FocusRequester, content: LazyListScope.() -> Unit) {
    LazyColumn(
        // Requesting focus here lands on the first focusable row.
        modifier = Modifier.fillMaxSize().focusRequester(focus).onFocusChanged { pageHasFocus = it.hasFocus },
        contentPadding = PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

internal fun LazyListScope.section(title: String) {
    item {
        Text(
            title.uppercase(),
            color = SiloSecondaryText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ActionRow(bound: AdminBoundOperation, modifier: Modifier, onClick: () -> Unit) {
    val op = bound.operation
    TvRow(op.title, "${op.method} ${op.relativePath}", destructive = op.isDestructive, modifier = modifier, onClick = onClick)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            contentColor = SiloOnSurface,
            focusedContainerColor = SiloOnSurface,
            focusedContentColor = DarkBackground,
            pressedContainerColor = SiloOnSurface,
            pressedContentColor = DarkBackground,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
            focusedBorder = Border(BorderStroke(0.dp, Color.Transparent), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = when {
                        focused -> DarkBackground
                        destructive -> Destructive
                        else -> SiloOnSurface
                    },
                    fontSize = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = if (focused) DarkBackground.copy(alpha = 0.7f) else SiloSecondaryText,
                        fontSize = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chevron) Text("›", color = if (focused) DarkBackground else SiloSecondaryText, fontSize = 26.sp)
        }
    }
}

@Composable
internal fun Footnote(text: String) {
    Text(text, color = SiloSecondaryText, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
}

@Composable
internal fun Banner(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f)).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Text(text, color = SiloOnSurface, fontSize = 17.sp)
    }
}

@Composable
internal fun Notice(text: String, action: Pair<String, () -> Unit>?) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(action != null) {
        if (action != null) requestFocusUntilObserved(
            maxAttempts = 30,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = focus::requestFocus,
            isFocused = { focused },
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = SiloOnSurface, fontSize = 20.sp)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(360.dp)) {
                TvRow(action.first, null, chevron = false, modifier = Modifier.focusRequester(focus).onFocusChanged { focused = it.hasFocus }, onClick = action.second)
            }
        }
    }
}
