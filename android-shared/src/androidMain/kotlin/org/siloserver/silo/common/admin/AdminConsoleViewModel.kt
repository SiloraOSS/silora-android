package org.siloserver.silo.common.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.siloserver.silo.admin.AdminBoundOperation
import org.siloserver.silo.admin.AdminCatalog
import org.siloserver.silo.admin.AdminCategory
import org.siloserver.silo.admin.AdminDisplay
import org.siloserver.silo.admin.AdminForm
import org.siloserver.silo.admin.AdminOperation
import org.siloserver.silo.admin.AdminSpec
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.apiv2.AdminResponse
import org.siloserver.silo.network.apiv2.AdminV2Api
import org.siloserver.silo.repository.AuthRepository
import org.siloserver.silo.repository.ProfileRepository

/**
 * Fork: loads the bundled admin spec once per process. It is about 700 KB of
 * JSON, so it is read off the main thread and kept.
 */
class AdminSpecLoader(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var catalog: AdminCatalog? = null

    suspend fun catalog(): AdminCatalog = catalog ?: mutex.withLock {
        catalog ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            AdminCatalog(AdminSpec.parse(text))
        }.also { catalog = it }
    }

    companion object {
        const val ASSET = "admin/openapi-admin.json"
    }
}

/** Whether the signed-in account can use the admin console here. */
enum class AdminAvailability { Unknown, NotAdmin, NeedsPrimaryProfile, Available }

/**
 * Fork: the admin role belongs to the account, and the server takes admin
 * calls only on the account's primary profile.
 */
class AdminAccess(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
) {
    suspend fun check(): AdminAvailability {
        val user = (auth.getCurrentUser() as? ApiResult.Success)?.data ?: return AdminAvailability.Unknown
        if (!user.role.equals("admin", ignoreCase = true)) return AdminAvailability.NotAdmin
        val profile = profiles.getActiveProfile()
        return if (profile == null || profile.isPrimary) AdminAvailability.Available else AdminAvailability.NeedsPrimaryProfile
    }
}

/** One screen of the console's own back stack. */
sealed interface AdminEntry {
    val title: String

    data object Home : AdminEntry { override val title = "Admin" }

    data class Category(val category: AdminCategory) : AdminEntry { override val title = category.title }

    data class Resource(val key: String, override val title: String) : AdminEntry

    /** One operation: its form, then its result. */
    data class Run(
        val id: Long,
        val bound: AdminBoundOperation,
        val form: AdminForm,
        val values: Map<String, String>,
        val phase: AdminRunPhase,
        val error: String? = null,
        val response: AdminResponse? = null,
        val items: List<JsonElement> = emptyList(),
        val nextCursor: String? = null,
        val loadingMore: Boolean = false,
        val etag: String? = null,
        /** Values the form was opened with, kept so a retry starts from them. */
        val presetQuery: Map<String, String> = emptyMap(),
    ) : AdminEntry {
        override val title: String get() = bound.operation.title
        val operation: AdminOperation get() = bound.operation
        val isList: Boolean get() = response != null && AdminDisplay.page(response.body) != null
    }

    /** A record shown from a list or a nested value; [actions] apply to it. */
    data class Record(
        override val title: String,
        val value: JsonElement,
        val actions: List<AdminBoundOperation>,
    ) : AdminEntry
}

enum class AdminRunPhase {
    /** Loading the current record to prefill an edit and get its version tag. */
    Preparing,
    /** The form is shown; nothing sent yet. */
    Editing,
    /** A destructive call waits for the user to confirm. */
    Confirming,
    Sending,
    Done,
}

data class AdminConsoleState(
    val availability: AdminAvailability = AdminAvailability.Unknown,
    val catalog: AdminCatalog? = null,
    val loadError: String? = null,
    val stack: List<AdminEntry> = listOf(AdminEntry.Home),
) {
    val current: AdminEntry get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
}

/**
 * Fork: the admin console, shared by the phone and TV apps. It keeps its own
 * back stack so one navigation route hosts every admin screen.
 */
class AdminConsoleViewModel(
    private val loader: AdminSpecLoader,
    private val access: AdminAccess,
    private val api: AdminV2Api,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminConsoleState())
    val state: StateFlow<AdminConsoleState> = _state.asStateFlow()

    private var nextId = 1L
    private val jobs = HashMap<Long, Job>()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(loadError = null) }
            val availability = runCatching { access.check() }.getOrDefault(AdminAvailability.Unknown)
            val catalog = runCatching { loader.catalog() }
            _state.update {
                it.copy(
                    availability = availability,
                    catalog = catalog.getOrNull(),
                    loadError = catalog.exceptionOrNull()?.let { e -> "Couldn't load the admin spec: ${e.message}" }
                        ?: if (availability == AdminAvailability.Unknown) "Couldn't reach the server." else null,
                )
            }
        }
    }

    /** Pops one screen; false when the console itself should close. */
    fun back(): Boolean {
        val stack = _state.value.stack
        if (stack.size <= 1) return false
        val leaving = stack.last()
        (leaving as? AdminEntry.Run)?.let { jobs.remove(it.id)?.cancel() }
        _state.update { it.copy(stack = it.stack.dropLast(1)) }
        // A list under a finished change is likely stale: read it again.
        val revealed = _state.value.current
        if (leaving is AdminEntry.Run && leaving.phase == AdminRunPhase.Done && !leaving.operation.isRead &&
            revealed is AdminEntry.Run && revealed.operation.isRead && revealed.phase == AdminRunPhase.Done
        ) {
            send(revealed.id)
        }
        if (leaving is AdminEntry.Record) {
            // An action on a record (approve, delete) changes its list.
            val below = _state.value.stack.lastOrNull()
            if (below is AdminEntry.Run && below.operation.isRead && below.phase == AdminRunPhase.Done && changedSince(leaving)) {
                send(below.id)
            }
        }
        return true
    }

    private val changedRecords = HashSet<AdminEntry.Record>()
    private fun changedSince(record: AdminEntry.Record) = changedRecords.remove(record)

    fun openCategory(category: AdminCategory) = push(AdminEntry.Category(category))

    fun openResource(key: String) {
        val resource = _state.value.catalog?.resource(key) ?: return
        push(AdminEntry.Resource(key, resource.title))
    }

    fun openOperation(operation: AdminOperation, pathValues: Map<String, String> = emptyMap(), query: Map<String, String> = emptyMap()) =
        openBound(AdminBoundOperation(operation, pathValues), query)

    fun openBound(bound: AdminBoundOperation, query: Map<String, String> = emptyMap()) {
        val form = AdminForm(bound.operation, bound.pathValues)
        val values = form.initialValues() + query.mapKeys { "@query.${it.key}" }
        val op = bound.operation
        val prefillable = !op.isRead && bound.missingPathParams.isEmpty() && prefillSource(op) != null
        val phase = when {
            prefillable -> AdminRunPhase.Preparing
            op.isRead && form.fields.none { it.required && values[it.key].isNullOrBlank() } -> AdminRunPhase.Sending
            else -> AdminRunPhase.Editing
        }
        val entry = AdminEntry.Run(nextId++, bound, form, values, phase, presetQuery = query)
        push(entry)
        when (phase) {
            AdminRunPhase.Preparing -> prefill(entry.id)
            AdminRunPhase.Sending -> send(entry.id)
            else -> Unit
        }
    }

    /** Opens one item of a list (or a nested value) as a record with its actions. */
    fun openItem(runId: Long, item: JsonElement) {
        val run = findRun(runId) ?: return
        val catalog = _state.value.catalog ?: return
        val actions = catalog.itemActions(run.operation, run.bound.pathValues, item)
        push(AdminEntry.Record(AdminDisplay.title(item), item, actions))
    }

    /** Actions on the record a read returned: edit, delete and the paths below it. */
    fun detailActions(run: AdminEntry.Run): List<AdminBoundOperation> {
        if (!run.operation.isRead || run.isList) return emptyList()
        val catalog = _state.value.catalog ?: return emptyList()
        val pathValues = run.form.build(run.values).getOrNull()?.pathValues ?: run.bound.pathValues
        return catalog.detailActions(run.operation, pathValues)
    }

    fun openNested(title: String, value: JsonElement) = push(AdminEntry.Record(title, value, emptyList()))

    fun setValue(runId: Long, key: String, value: String) = updateRun(runId) {
        it.copy(values = it.values + (key to value), error = null)
    }

    /** Sends the form; a destructive call asks first. */
    fun submit(runId: Long) {
        val run = findRun(runId) ?: return
        run.form.build(run.values).onFailure { e ->
            updateRun(runId) { it.copy(error = e.message) }
            return
        }
        if (run.operation.isDestructive && run.phase != AdminRunPhase.Confirming) {
            updateRun(runId) { it.copy(phase = AdminRunPhase.Confirming, error = null) }
            return
        }
        send(runId)
    }

    fun cancelConfirm(runId: Long) = updateRun(runId) { it.copy(phase = AdminRunPhase.Editing) }

    /** Back to the form after a result, to change the inputs and send again. */
    fun edit(runId: Long) = updateRun(runId) { it.copy(phase = AdminRunPhase.Editing, error = null) }

    fun refresh(runId: Long) = send(runId)

    fun loadMore(runId: Long) {
        val run = findRun(runId) ?: return
        val cursor = run.nextCursor ?: return
        if (run.loadingMore) return
        updateRun(runId) { it.copy(loadingMore = true) }
        jobs[runId] = viewModelScope.launch {
            val request = run.form.build(run.values).getOrNull() ?: return@launch
            val result = api.call(run.operation, request.pathValues, request.query, null, null, cursor)
            updateRun(runId) { current ->
                when (result) {
                    is ApiResult.Success -> {
                        val page = AdminDisplay.page(result.data.body)
                        current.copy(
                            items = current.items + page?.items.orEmpty(),
                            nextCursor = page?.nextCursor,
                            loadingMore = false,
                        )
                    }
                    else -> current.copy(loadingMore = false, error = describe(result))
                }
            }
        }
    }

    private fun send(runId: Long) {
        val run = findRun(runId) ?: return
        val request = run.form.build(run.values).getOrElse { e ->
            updateRun(runId) { it.copy(phase = AdminRunPhase.Editing, error = e.message) }
            return
        }
        updateRun(runId) { it.copy(phase = AdminRunPhase.Sending, error = null) }
        jobs[runId]?.cancel()
        jobs[runId] = viewModelScope.launch {
            var etag = run.etag
            if (etag == null && run.operation.needsIfMatch) {
                etag = fetchEtag(run.operation, request.pathValues)
            }
            val result = api.call(
                operation = run.operation,
                pathValues = request.pathValues,
                query = request.query,
                body = request.body,
                ifMatch = etag?.takeIf { run.operation.acceptsIfMatch },
            )
            updateRun(runId) { current ->
                when (result) {
                    is ApiResult.Success -> {
                        val page = AdminDisplay.page(result.data.body)
                        current.copy(
                            phase = AdminRunPhase.Done,
                            response = result.data,
                            items = page?.items.orEmpty(),
                            nextCursor = page?.nextCursor,
                            // A change returns the record's new version tag.
                            etag = result.data.etag ?: current.etag,
                        )
                    }
                    else -> current.copy(
                        phase = if (current.operation.isRead && current.response == null && !current.form.hasInputs) AdminRunPhase.Done else AdminRunPhase.Editing,
                        error = describe(result),
                    )
                }
            }
            if (result is ApiResult.Success && !run.operation.isRead) markRecordsBelowChanged(runId)
        }
    }

    private fun markRecordsBelowChanged(runId: Long) {
        val stack = _state.value.stack
        val index = stack.indexOfFirst { it is AdminEntry.Run && it.id == runId }
        stack.take(index.coerceAtLeast(0)).filterIsInstance<AdminEntry.Record>().lastOrNull()?.let { changedRecords += it }
    }

    /** For an edit: the current record fills the form, and its version tag is kept. */
    private fun prefill(runId: Long) {
        val run = findRun(runId) ?: return
        val source = prefillSource(run.operation) ?: return
        jobs[runId] = viewModelScope.launch {
            val result = api.call(source, run.bound.pathValues)
            updateRun(runId) { current ->
                when (result) {
                    is ApiResult.Success -> {
                        val record = result.data.body
                        val fill = if (current.operation.method == "PUT" || current.operation.method == "PATCH") {
                            // Some reads wrap the record (`{user: {...}}`); edit the inner one.
                            unwrap(record, current.form)
                        } else null
                        current.copy(
                            phase = AdminRunPhase.Editing,
                            values = if (fill != null) current.form.initialValues(fill) + current.presetQuery.mapKeys { "@query.${it.key}" } else current.values,
                            etag = result.data.etag,
                        )
                    }
                    else -> current.copy(phase = AdminRunPhase.Editing, error = "Couldn't load the current values: ${describe(result)}")
                }
            }
        }
    }

    private fun unwrap(record: JsonElement?, form: AdminForm): JsonElement? {
        val obj = record as? JsonObject ?: return record
        val bodyFields = form.fields.filterNot { it.isParam }.map { it.path.first() }.toSet()
        if (bodyFields.isEmpty() || bodyFields.any { it in obj }) return obj
        val inner = obj.values.filterIsInstance<JsonObject>().singleOrNull()
        return inner ?: obj
    }

    private suspend fun fetchEtag(operation: AdminOperation, pathValues: Map<String, String>): String? {
        val source = prefillSource(operation) ?: return null
        return (api.call(source, pathValues) as? ApiResult.Success)?.data?.etag
    }

    /** The plain read on the same path, when the server offers one. */
    private fun prefillSource(operation: AdminOperation): AdminOperation? =
        _state.value.catalog?.operation("GET", operation.path)?.takeIf { it !== operation && !operation.isRead }

    private fun describe(result: ApiResult<*>): String = when (result) {
        is ApiResult.Error -> buildString {
            append(result.message.ifBlank { result.error.ifBlank { "The server refused the request." } })
            if (result.code > 0) append(" (").append(result.code).append(')')
        }
        is ApiResult.NetworkError -> "Couldn't reach the server: ${result.exception.message ?: result.exception::class.simpleName}"
        is ApiResult.Success -> ""
    }

    private fun push(entry: AdminEntry) = _state.update { it.copy(stack = it.stack + entry) }

    private fun findRun(id: Long): AdminEntry.Run? =
        _state.value.stack.firstOrNull { it is AdminEntry.Run && it.id == id } as? AdminEntry.Run

    private fun updateRun(id: Long, transform: (AdminEntry.Run) -> AdminEntry.Run) = _state.update { state ->
        state.copy(stack = state.stack.map { if (it is AdminEntry.Run && it.id == id) transform(it) else it })
    }
}
