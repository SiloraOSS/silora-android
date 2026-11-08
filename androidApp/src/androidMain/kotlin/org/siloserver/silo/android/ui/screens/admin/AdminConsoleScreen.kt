package org.siloserver.silo.android.ui.screens.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonElement
import org.koin.compose.viewmodel.koinViewModel
import org.siloserver.silo.admin.AdminBoundOperation
import org.siloserver.silo.admin.AdminCategory
import org.siloserver.silo.admin.AdminDisplay
import org.siloserver.silo.admin.AdminField
import org.siloserver.silo.admin.AdminFieldKind
import org.siloserver.silo.admin.AdminForm
import org.siloserver.silo.android.ui.components.SiloConfirmDialog
import org.siloserver.silo.android.ui.theme.SiloBackground
import org.siloserver.silo.android.ui.theme.SiloDestructive
import org.siloserver.silo.android.ui.theme.SiloGroupedCell
import org.siloserver.silo.android.ui.theme.SiloGroupedSeparator
import org.siloserver.silo.android.ui.theme.SiloOnSurface
import org.siloserver.silo.android.ui.theme.SiloSecondaryText
import org.siloserver.silo.android.ui.theme.SiloSuccess
import org.siloserver.silo.common.admin.AdminAvailability
import org.siloserver.silo.common.admin.AdminConsoleState
import org.siloserver.silo.common.admin.AdminConsoleViewModel
import org.siloserver.silo.common.admin.AdminEntry
import org.siloserver.silo.common.admin.AdminRunPhase

/**
 * Fork: the admin console on the phone. Every admin operation the server
 * documents is reachable from here, built from the bundled spec: the home
 * offers shortcuts and the four groups, each area lists its operations, and
 * each operation opens a form or its result.
 */
@Composable
fun AdminConsoleScreen(
    onClose: () -> Unit,
    onSwitchProfile: () -> Unit,
    viewModel: AdminConsoleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    BackHandler { if (!viewModel.back()) onClose() }
    val entry = state.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SiloBackground)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (!viewModel.back()) onClose() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = SiloOnSurface)
            }
            Text(
                text = entry.title,
                color = SiloOnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (entry is AdminEntry.Run && entry.operation.isRead && entry.phase == AdminRunPhase.Done) {
                IconButton(onClick = { viewModel.refresh(entry.id) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = SiloOnSurface)
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.availability == AdminAvailability.NotAdmin -> Notice("This account isn't an administrator.")
                state.availability == AdminAvailability.NeedsPrimaryProfile -> Notice(
                    "The server takes admin changes only from the account's main profile.",
                    action = "Switch profile" to onSwitchProfile,
                )
                state.catalog == null && state.loadError != null -> Notice(state.loadError.orEmpty(), action = "Try again" to viewModel::reload)
                state.catalog == null -> Loading()
                else -> EntryContent(state, entry, viewModel)
            }
        }
    }
}

@Composable
private fun EntryContent(state: AdminConsoleState, entry: AdminEntry, vm: AdminConsoleViewModel) {
    val catalog = state.catalog ?: return
    when (entry) {
        AdminEntry.Home -> Page {
            if (catalog.quickActions.isNotEmpty()) {
                section("Shortcuts")
                group(catalog.quickActions) { action, _ ->
                    NavRow(action.title, action.subtitle) { vm.openOperation(action.operation, query = action.query) }
                }
            }
            section("Everything")
            group(AdminCategory.entries.toList()) { category, _ ->
                NavRow(category.title, category.blurb) { vm.openCategory(category) }
            }
            item { Footnote("Server API ${catalog.spec.version} · ${catalog.spec.operations.size} admin operations") }
        }
        is AdminEntry.Category -> Page {
            group(catalog.resourcesIn(entry.category)) { resource, _ ->
                NavRow(resource.title, "${resource.operations.size} operations") { vm.openResource(resource.key) }
            }
        }
        is AdminEntry.Resource -> {
            val resource = catalog.resource(entry.key)
            Page {
                if (resource == null) return@Page
                val primary = resource.primaryRead
                val ordered = listOfNotNull(primary) + resource.operations.filter { it !== primary }
                group(ordered) { op, _ ->
                    NavRow(
                        title = op.title,
                        subtitle = op.summary?.takeIf { it.isNotBlank() && !it.equals(op.title, true) } ?: "${op.method} ${op.relativePath}",
                        destructive = op.isDestructive,
                    ) { vm.openOperation(op) }
                }
            }
        }
        is AdminEntry.Run -> RunContent(entry, vm)
        is AdminEntry.Record -> Page {
            recordRows(entry.value) { title, value -> vm.openNested(title, value) }
            if (entry.actions.isNotEmpty()) {
                section("Actions")
                group(entry.actions) { bound, _ -> ActionRow(bound) { vm.openBound(bound) } }
            }
        }
    }
}

@Composable
private fun RunContent(run: AdminEntry.Run, vm: AdminConsoleViewModel) {
    when (run.phase) {
        AdminRunPhase.Preparing, AdminRunPhase.Sending -> if (run.response == null || run.phase == AdminRunPhase.Preparing) {
            Loading()
            return
        }
        else -> Unit
    }
    if (run.phase == AdminRunPhase.Confirming) {
        SiloConfirmDialog(
            title = "${run.operation.title}?",
            body = run.operation.summary?.takeIf { it.isNotBlank() } ?: "This changes the server and may not be undoable.",
            confirmLabel = "Continue",
            onConfirm = { vm.submit(run.id) },
            onDismiss = { vm.cancelConfirm(run.id) },
        )
    }
    val showForm = run.phase == AdminRunPhase.Editing || run.phase == AdminRunPhase.Confirming
    Page {
        run.error?.let { item { ErrorBanner(it) } }
        if (showForm) {
            run.operation.description?.takeIf { it.isNotBlank() }?.let { item { Footnote(it) } }
            formFields(run.form, run.values) { key, value -> vm.setValue(run.id, key, value) }
            item {
                Button(
                    onClick = { vm.submit(run.id) },
                    colors = if (run.operation.isDestructive) ButtonDefaults.buttonColors(containerColor = SiloDestructive) else ButtonDefaults.buttonColors(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text(if (run.operation.isRead) "Show" else run.operation.title) }
            }
            return@Page
        }
        val response = run.response
        if (run.phase == AdminRunPhase.Sending) item { Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp)) } }
        if (!run.operation.isRead && response != null) {
            item { SuccessBanner("Done (${response.status}).") }
        }
        if (run.form.hasInputs && response != null) {
            item {
                OutlinedButton(onClick = { vm.edit(run.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(if (run.operation.isRead) "Change filters" else "Edit and send again")
                }
            }
        }
        if (run.isList) {
            if (run.items.isEmpty()) item { Footnote("Nothing here.") }
            group(run.items) { item, _ ->
                NavRow(AdminDisplay.title(item), AdminDisplay.subtitle(item)) { vm.openItem(run.id, item) }
            }
            if (run.nextCursor != null) {
                item {
                    OutlinedButton(onClick = { vm.loadMore(run.id) }, enabled = !run.loadingMore, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(if (run.loadingMore) "Loading…" else "Load more")
                    }
                }
            }
        } else if (response != null) {
            recordRows(response.body) { title, value -> vm.openNested(title, value) }
            val actions = vm.detailActions(run)
            if (actions.isNotEmpty()) {
                section("Actions")
                group(actions) { bound, _ -> ActionRow(bound) { vm.openBound(bound) } }
            }
        }
    }
}

private fun LazyListScope.recordRows(value: JsonElement?, onNested: (String, JsonElement) -> Unit) {
    val rows = AdminDisplay.rows(value)
    if (rows.isEmpty()) {
        item { Footnote("No content.") }
        return
    }
    group(rows) { row, _ ->
        val nested = row.nested
        if (nested != null) {
            NavRow(row.label, row.text) { onNested(row.label, nested) }
        } else {
            ValueRow(row.label, row.text)
        }
    }
}

private fun LazyListScope.formFields(form: AdminForm, values: Map<String, String>, onChange: (String, String) -> Unit) {
    if (form.bodyIsRawJson) {
        item {
            FieldBox("Request body (JSON)", null) {
                OutlinedTextField(
                    value = values[AdminForm.RAW_BODY_KEY].orEmpty(),
                    onValueChange = { onChange(AdminForm.RAW_BODY_KEY, it) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SiloOnSurface),
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    itemsIndexed(form.fields, key = { _, f -> f.key }) { _, field -> FieldEditor(field, values[field.key].orEmpty(), onChange) }
}

@Composable
private fun FieldEditor(field: AdminField, value: String, onChange: (String, String) -> Unit) {
    val indent = (field.depth * 12).dp
    if (field.kind == AdminFieldKind.Group) {
        Text(
            text = field.label,
            color = SiloOnSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 16.dp + indent, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        return
    }
    val label = field.label + if (field.required) " *" else ""
    Box(Modifier.padding(start = indent)) {
        FieldBox(label, field.help) {
            when (val kind = field.kind) {
                AdminFieldKind.Toggle -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (value) { "true" -> "On"; "false" -> "Off"; else -> "Not set" },
                        color = SiloSecondaryText,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = value == "true", onCheckedChange = { onChange(field.key, it.toString()) })
                }
                is AdminFieldKind.Choice -> ChoiceField(value, kind.options, field.required) { onChange(field.key, it) }
                else -> OutlinedTextField(
                    value = value,
                    onValueChange = { onChange(field.key, it) },
                    singleLine = kind != AdminFieldKind.Lines && kind != AdminFieldKind.JsonText,
                    minLines = if (kind == AdminFieldKind.JsonText) 4 else 1,
                    placeholder = when (kind) {
                        AdminFieldKind.Lines -> ({ Text("One per line", color = SiloSecondaryText) })
                        AdminFieldKind.JsonText -> ({ Text("JSON", color = SiloSecondaryText) })
                        else -> null
                    },
                    visualTransformation = if (kind == AdminFieldKind.Secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (kind) {
                            AdminFieldKind.Integer -> KeyboardType.Number
                            AdminFieldKind.Decimal -> KeyboardType.Decimal
                            AdminFieldKind.Secret -> KeyboardType.Password
                            else -> KeyboardType.Text
                        },
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = SiloOnSurface,
                        fontFamily = if (kind == AdminFieldKind.JsonText) FontFamily.Monospace else FontFamily.Default,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChoiceField(value: String, options: List<String>, required: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SiloGroupedCell)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value.ifBlank { "Not set" }, color = if (value.isBlank()) SiloSecondaryText else SiloOnSurface, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = SiloSecondaryText)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (!required) DropdownMenuItem(text = { Text("Not set") }, onClick = { open = false; onPick("") })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { open = false; onPick(option) })
            }
        }
    }
}

@Composable
private fun FieldBox(label: String, help: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, color = SiloOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (!help.isNullOrBlank()) Text(help, color = SiloSecondaryText, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        verticalArrangement = Arrangement.Top,
        content = {
            content()
            item { Spacer(Modifier.height(32.dp)) }
        },
    )
}

private fun LazyListScope.section(title: String) {
    item(key = "section:$title") {
        Text(
            text = title.uppercase(),
            color = SiloSecondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        )
    }
}

/** Rows in one rounded card, as in Settings. */
private fun <T> LazyListScope.group(items: List<T>, row: @Composable (T, Int) -> Unit) {
    itemsIndexed(items) { index, item ->
        val top = if (index == 0) 12.dp else 0.dp
        val bottom = if (index == items.lastIndex) 12.dp else 0.dp
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                .background(SiloGroupedCell),
        ) {
            row(item, index)
            if (index != items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(SiloGroupedSeparator))
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (destructive) SiloDestructive else SiloOnSurface, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = SiloSecondaryText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = SiloSecondaryText)
    }
}

@Composable
private fun ActionRow(bound: AdminBoundOperation, onClick: () -> Unit) {
    val op = bound.operation
    NavRow(op.title, "${op.method} ${op.relativePath}", destructive = op.isDestructive, onClick = onClick)
}

@Composable
private fun ValueRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label, color = SiloSecondaryText, fontSize = 13.sp)
        Text(value, color = SiloOnSurface, fontSize = 15.sp)
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, color = SiloSecondaryText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
}

@Composable
private fun ErrorBanner(text: String) = Banner(text, SiloDestructive)

@Composable
private fun SuccessBanner(text: String) = Banner(text, SiloSuccess)

@Composable
private fun Banner(text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, color = SiloOnSurface, fontSize = 14.sp)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun Notice(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = SiloOnSurface, fontSize = 16.sp)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = action.second) { Text(action.first) }
        }
    }
}
