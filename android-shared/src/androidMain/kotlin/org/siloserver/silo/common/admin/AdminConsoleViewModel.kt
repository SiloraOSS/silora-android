package org.siloserver.silo.common.admin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.http.Headers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.siloserver.silo.admin.AdminBoundOperation
import org.siloserver.silo.admin.AdminCatalog
import org.siloserver.silo.admin.AdminCategory
import org.siloserver.silo.admin.AdminDisplay
import org.siloserver.silo.admin.AdminFieldKind
import org.siloserver.silo.admin.AdminForm
import org.siloserver.silo.admin.AdminOperation
import org.siloserver.silo.admin.AdminSpec
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.TokenManager
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
 *
 * A definite answer is kept for the active server/account/profile identity,
 * so menus can ask whenever shown without a network call each time. Unknown (the
 * server didn't answer) is never kept, so the caller can ask again.
 */
class AdminAccess(
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val tokens: TokenManager,
) {
    @Volatile private var cached: Pair<AuthScopeSnapshot, AdminAvailability>? = null

    suspend fun check(): AdminAvailability {
        val scope = tokens.snapshotCurrentScope() ?: return AdminAvailability.Unknown
        if (scope.profileId == null) return AdminAvailability.Unknown
        cached?.let { (identity, availability) -> if (identity == scope) return availability }
        val user = (auth.getCurrentUser(scope) as? ApiResult.Success)?.data ?: return AdminAvailability.Unknown
        val availability = if (!user.role.equals("admin", ignoreCase = true)) {
            AdminAvailability.NotAdmin
        } else {
            // A profile that can't be looked up is not assumed to be the primary one.
            val profile = profiles.getActiveProfile() ?: return AdminAvailability.Unknown
            if (profile.isPrimary) AdminAvailability.Available else AdminAvailability.NeedsPrimaryProfile
        }
        // A server/account/profile switch during either read invalidates its answer.
        if (tokens.snapshotCurrentScope() != scope) return AdminAvailability.Unknown
        cached = scope to availability
        return availability
    }

    /**
     * [check], asked again with growing pauses while the answer is Unknown
     * (a slow first reply after a cold start or a VPN reconnect).
     */
    suspend fun checkUntilKnown(publish: (AdminAvailability) -> Unit) {
        var pause = 2_000L
        while (true) {
            val availability = try {
                check()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AdminAvailability.Unknown
            }
            currentCoroutineContext().ensureActive()
            publish(availability)
            if (availability != AdminAvailability.Unknown) return
            delay(pause)
            pause = (pause * 2).coerceAtMost(60_000L)
        }
    }
}

/** One screen of the console's own back stack. */
sealed interface AdminEntry {
    val title: String

    data object Home : AdminEntry { override val title = "Admin" }

    data class Category(val category: AdminCategory) : AdminEntry { override val title = category.title }

    /** The hand-built screens. */
    data object Dashboard : AdminEntry { override val title = "Dashboard" }
    data object Requests : AdminEntry { override val title = "Requests" }
    data object Tasks : AdminEntry { override val title = "Tasks and scans" }
    data object Logs : AdminEntry { override val title = "Logs" }

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
        /** The server offers a read on the same path, which supplies the current values and version tag. */
        val hasTagSource: Boolean = false,
        /** A version tag the record the action was opened from carries (`config_etag`). */
        val sourceTag: String? = null,
        /** The server refused the version tag (412/428): the record changed, or the tag was missing. */
        val stale: Boolean = false,
    ) : AdminEntry {
        override val title: String get() = bound.operation.title
        val operation: AdminOperation get() = bound.operation
        val isList: Boolean get() = response != null && AdminDisplay.page(response.body) != null

        /**
         * The call needs a version tag and nothing can supply one, so it is
         * sent with `If-Match: *`, which the server takes as a deliberate
         * overwrite. The confirmation says so.
         */
        val blindOverwrite: Boolean
            get() = operation.needsIfMatch && !hasTagSource && etag == null && sourceTag == null

        /** What the confirmation says before a change is sent. */
        val confirmMessage: String
            get() = (
                operation.summary?.takeIf { it.isNotBlank() }
                    ?: if (operation.isDestructive) "This changes the server and may not be undoable." else "This changes the server."
                ) + if (blindOverwrite) "\n\nThis replaces the current version, including changes made since you opened it." else ""
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
    /** A change waits for the user to confirm. */
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
    private val moreJobs = HashMap<Long, Job>()

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
        (leaving as? AdminEntry.Run)?.let {
            jobs.remove(it.id)?.cancel()
            moreJobs.remove(it.id)?.cancel()
        }
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

    fun openDashboard() = push(AdminEntry.Dashboard)
    fun openRequests() = push(AdminEntry.Requests)
    fun openTasks() = push(AdminEntry.Tasks)
    fun openLogs() = push(AdminEntry.Logs)

    fun openResource(key: String) {
        val resource = _state.value.catalog?.resource(key) ?: return
        push(AdminEntry.Resource(key, resource.title))
    }

    fun openOperation(operation: AdminOperation, pathValues: Map<String, String> = emptyMap(), query: Map<String, String> = emptyMap()) =
        openBound(AdminBoundOperation(operation, pathValues), query)

    /**
     * Opens one operation. [source] is the record it was opened from, whose
     * version tag (if it carries one) guards an edit that has no read of its
     * own.
     */
    fun openBound(bound: AdminBoundOperation, query: Map<String, String> = emptyMap(), source: JsonElement? = null) {
        val form = AdminForm(bound.operation, bound.pathValues)
        val values = form.initialValues() + query.mapKeys { "@query.${it.key}" }
        val op = bound.operation
        val hasTagSource = prefillSource(op) != null
        val prefillable = !op.isRead && bound.missingPathParams.isEmpty() && hasTagSource
        val phase = when {
            prefillable -> AdminRunPhase.Preparing
            op.isRead && form.fields.none { it.required && values[it.key].isNullOrBlank() } -> AdminRunPhase.Sending
            else -> AdminRunPhase.Editing
        }
        val entry = AdminEntry.Run(
            nextId++, bound, form, values, phase,
            presetQuery = query,
            hasTagSource = hasTagSource,
            sourceTag = if (op.acceptsIfMatch) AdminCatalog.versionTagOf(source) else null,
        )
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

    /** A changed input is a new command, so a held command identity is dropped. */
    fun setValue(runId: Long, key: String, value: String) = updateRun(runId) {
        it.copy(values = it.values - COMMAND_KEYS + (key to value), error = null)
    }

    /** Sends the form; a change (anything but a read) asks first. */
    fun submit(runId: Long) {
        val run = findRun(runId) ?: return
        run.form.build(run.values).onFailure { e ->
            updateRun(runId) { it.copy(error = e.message) }
            return
        }
        if (run.operation.needsConfirmation && run.phase != AdminRunPhase.Confirming) {
            updateRun(runId) { it.copy(phase = AdminRunPhase.Confirming, error = null) }
            return
        }
        send(runId)
    }

    fun cancelConfirm(runId: Long) = updateRun(runId) { it.copy(phase = AdminRunPhase.Editing) }

    /** Back to the form after a result, to change the inputs and send again. */
    fun edit(runId: Long) = updateRun(runId) { it.copy(phase = AdminRunPhase.Editing, error = null) }

    fun refresh(runId: Long) = send(runId)

    /**
     * After a 412/428: reads the record again, so the form shows its current
     * values and the new version tag. Typed values are replaced.
     */
    fun reloadCurrent(runId: Long) {
        val run = findRun(runId) ?: return
        if (!run.hasTagSource || run.bound.missingPathParams.isNotEmpty()) return
        jobs.remove(runId)?.cancel()
        updateRun(runId) { it.copy(phase = AdminRunPhase.Preparing, error = null, stale = false, etag = null) }
        prefill(runId)
    }

    fun loadMore(runId: Long) {
        val run = findRun(runId) ?: return
        val cursor = run.nextCursor ?: return
        if (run.loadingMore) return
        val request = run.form.build(run.values).getOrNull() ?: return
        updateRun(runId) { it.copy(loadingMore = true) }
        moreJobs[runId]?.cancel()
        moreJobs[runId] = viewModelScope.launch {
            val result = api.call(run.operation, request.pathValues, request.query, null, null, cursor)
            updateRun(runId) { current ->
                // A refresh since replaced the list; this page belongs to the old one.
                if (current.nextCursor != cursor) return@updateRun current.copy(loadingMore = false)
                when (result) {
                    is ApiResult.Success -> {
                        val page = AdminDisplay.page(result.data.body)
                        current.copy(
                            items = appendNew(current.items, page?.items.orEmpty()),
                            nextCursor = page?.nextCursor,
                            loadingMore = false,
                        )
                    }
                    // The cursor stays, so "Load more" can be pressed again.
                    else -> current.copy(loadingMore = false, error = describe(result))
                }
            }
        }
    }

    /** [page] after [items], minus rows already listed (same `id`/`key`), which a shifting list can repeat. */
    private fun appendNew(items: List<JsonElement>, page: List<JsonElement>): List<JsonElement> {
        fun idOf(item: JsonElement): String? {
            val obj = item as? JsonObject ?: return null
            return ((obj["id"] ?: obj["key"]) as? JsonPrimitive)?.contentOrNull
        }
        val seen = items.mapNotNullTo(HashSet()) { idOf(it) }
        return items + page.filter { item -> idOf(item)?.let { seen.add(it) } ?: true }
    }

    private fun send(runId: Long) {
        var run = findRun(runId) ?: return
        if (run.form.sendsCommandIdentity && run.values[AdminForm.COMMAND_ID_KEY] == null) {
            // One identity per intended command, kept until it succeeds or
            // its inputs change, so a resend after a lost reply applies once.
            updateRun(runId) { it.copy(values = it.values + AdminForm.newCommandIdentity()) }
            run = findRun(runId) ?: return
        }
        val request = run.form.build(run.values).getOrElse { e ->
            updateRun(runId) { it.copy(phase = AdminRunPhase.Editing, error = e.message) }
            return
        }
        updateRun(runId) { it.copy(phase = AdminRunPhase.Sending, error = null, loadingMore = false) }
        jobs[runId]?.cancel()
        moreJobs.remove(runId)?.cancel()
        val sending = run
        jobs[runId] = viewModelScope.launch {
            var etag = sending.etag
            if (etag == null && sending.operation.needsIfMatch) {
                etag = fetchEtag(sending.operation, request.pathValues) ?: sending.sourceTag
                    ?: "*".takeIf { sending.blindOverwrite }
            }
            var body = request.body
            var latestSequence: Long? = null
            val refusal: (Int, Headers) -> Unit = { status, headers ->
                latestSequence = headers[LATEST_SEQUENCE_HEADER]?.trim()?.toLongOrNull()?.takeIf { status == 409 }
            }
            var result = api.call(
                operation = sending.operation,
                pathValues = request.pathValues,
                query = request.query,
                body = body,
                ifMatch = etag?.takeIf { sending.operation.acceptsIfMatch },
                onRefusal = refusal,
            )
            var retryIdentity: Map<String, String>? = null
            val latest = latestSequence
            if (latest != null && sending.form.sendsCommandIdentity) {
                // Stale: another admin client (or a clock ahead of this one)
                // applied a later command. Reissue once above it.
                AdminForm.raiseCommandSequenceFloor(latest)
                val identity = AdminForm.newCommandIdentity()
                body = sending.form.build(sending.values + identity).getOrNull()?.body
                if (body != null) {
                    retryIdentity = identity
                    result = api.call(sending.operation, request.pathValues, request.query, body, etag?.takeIf { sending.operation.acceptsIfMatch })
                }
            }
            val outcome = result
            val heldIdentity = retryIdentity
            updateRun(runId) { current ->
                when (outcome) {
                    is ApiResult.Success -> {
                        val page = AdminDisplay.page(outcome.data.body)
                        current.copy(
                            phase = AdminRunPhase.Done,
                            response = outcome.data,
                            items = page?.items.orEmpty(),
                            nextCursor = page?.nextCursor,
                            loadingMore = false,
                            // A change returns the record's new version tag. Without
                            // one the old tag is spent: the next send fetches again.
                            etag = if (current.operation.isRead) outcome.data.etag ?: current.etag else outcome.data.etag,
                            sourceTag = if (current.operation.isRead) current.sourceTag else null,
                            stale = false,
                            values = current.values - COMMAND_KEYS,
                        )
                    }
                    else -> {
                        val code = (outcome as? ApiResult.Error)?.code
                        val stale = code == 412 || code == 428
                        current.copy(
                            phase = if (current.operation.isRead && current.response == null && !current.form.hasInputs) AdminRunPhase.Done else AdminRunPhase.Editing,
                            loadingMore = false,
                            error = if (stale) staleMessage(current, code) else describe(outcome),
                            // The tag the server refused is useless now; a resend fetches a fresh one.
                            etag = if (stale) null else current.etag,
                            sourceTag = if (stale) null else current.sourceTag,
                            stale = stale,
                            values = heldIdentity?.let { current.values + it } ?: current.values,
                        )
                    }
                }
            }
            if (outcome is ApiResult.Success && !sending.operation.isRead) markRecordsBelowChanged(runId)
        }
    }

    private fun staleMessage(run: AdminEntry.Run, code: Int?): String =
        (if (code == 412) "Someone changed this record since it was loaded" else "The server needs the record's current version") +
            if (run.hasTagSource) ". Reload to see the current values, or send again to overwrite them. ($code)"
            else ". Send again to overwrite it. ($code)"

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
                            stale = false,
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
        // The read returns the bare map the edit wraps (`GET /settings` →
        // `{...}`, `PUT /settings` takes `{values: {...}}`).
        val only = form.fields.singleOrNull { !it.isParam }
        if (only != null && only.kind == AdminFieldKind.KeyValues && only.path.size == 1) {
            return JsonObject(mapOf(only.path.single() to obj))
        }
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

    private companion object {
        /** On a stale command refusal, the session's latest applied sequence. */
        const val LATEST_SEQUENCE_HEADER = "X-Silo-Latest-Sequence"
        val COMMAND_KEYS = setOf(AdminForm.COMMAND_ID_KEY, AdminForm.COMMAND_SEQUENCE_KEY)
    }

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
