package org.siloserver.silo.common.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.siloserver.silo.admin.AdminAuditLog
import org.siloserver.silo.admin.AdminBuildInfo
import org.siloserver.silo.admin.AdminCollection
import org.siloserver.silo.admin.AdminDashboardStats
import org.siloserver.silo.admin.AdminLibrary
import org.siloserver.silo.admin.AdminOperation
import org.siloserver.silo.admin.AdminOperationalLog
import org.siloserver.silo.admin.AdminPlaybackSession
import org.siloserver.silo.admin.AdminRequestCounts
import org.siloserver.silo.admin.AdminServerStatus
import org.siloserver.silo.admin.AdminSystemResources
import org.siloserver.silo.admin.AdminTask
import org.siloserver.silo.admin.AdminUserSummary
import org.siloserver.silo.model.request.MediaRequest
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.apiv2.AdminV2Api

/**
 * Fork: the operation ids the hand-built admin screens call. When the bundled
 * spec is regenerated, AdminHandBuiltContractTest fails if any is gone or its
 * fields changed, so the screens are updated with the server.
 */
object AdminOps {
    const val SERVER_STATUS = "getAdminServerStatus"
    const val SERVER_RESTART = "requestAdminServerRestart"
    const val BUILD = "getAdminBuildInfo"
    const val STATS = "getAdminDashboardStats"
    const val RESOURCES = "getAdminSystemResources"
    const val REQUEST_COUNTS = "getAdminRequestCounts"
    const val SESSIONS = "listAdminPlaybackSessions"
    const val SESSION_PAUSE = "pauseAdminPlaybackSession"
    const val SESSION_RESUME = "resumeAdminPlaybackSession"
    const val SESSION_STOP = "stopAdminPlaybackSession"
    const val SESSION_MESSAGE = "messageAdminPlaybackSession"
    const val REQUESTS = "listAdminRequests"
    const val REQUEST_APPROVE = "adminApproveRequest"
    const val REQUEST_DECLINE = "adminDeclineRequest"
    const val REQUEST_RETRY = "adminRetryRequest"
    const val REQUEST_CANCEL = "adminCancelRequest"
    const val USERS = "listAdminUsers"
    const val TASKS = "listAdminTasks"
    const val TASK_RUN = "runAdminTask"
    const val TASK_CANCEL = "cancelAdminTask"
    const val LIBRARIES = "listLibraries"
    const val SCAN = "startLibraryScan"
    const val SCAN_CANCEL = "cancelLibraryScans"
    const val LOGS_APP = "listAdminOperationalLogs"
    const val LOGS_AUDIT = "listAdminAuditLogs"

    val ALL = listOf(
        SERVER_STATUS, SERVER_RESTART, BUILD, STATS, RESOURCES, REQUEST_COUNTS, SESSIONS, SESSION_PAUSE,
        SESSION_RESUME, SESSION_STOP, SESSION_MESSAGE, REQUESTS, REQUEST_APPROVE, REQUEST_DECLINE,
        REQUEST_RETRY, REQUEST_CANCEL, USERS, TASKS, TASK_RUN, TASK_CANCEL, LIBRARIES, SCAN, SCAN_CANCEL,
        LOGS_APP, LOGS_AUDIT,
    )
}

/** Fork: typed calls by operation id, for the hand-built screens. */
class AdminService(private val loader: AdminSpecLoader, private val api: AdminV2Api) {
    private suspend fun op(id: String): AdminOperation? =
        runCatching { loader.catalog().spec.operations.firstOrNull { it.operationId == id } }.getOrNull()

    suspend fun has(id: String): Boolean = op(id) != null

    suspend fun <T> get(
        id: String,
        serializer: KSerializer<T>,
        path: Map<String, String> = emptyMap(),
        query: Map<String, String> = emptyMap(),
        cursor: String? = null,
    ): ApiResult<T> {
        val operation = op(id) ?: return unsupported(id)
        return when (val result = api.call(operation, path, query, cursor = cursor)) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(SiloJson.decodeFromJsonElement(serializer, result.data.body ?: JsonNull))
            }.getOrElse { ApiResult.Error(0, "decode", "The server's reply wasn't understood: ${it.message}") }
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    suspend fun send(id: String, path: Map<String, String> = emptyMap(), body: JsonElement? = null): ApiResult<JsonElement?> {
        val operation = op(id) ?: return unsupported(id)
        return when (val result = api.call(operation, path, body = body)) {
            is ApiResult.Success -> ApiResult.Success(result.data.body)
            is ApiResult.Error -> result
            is ApiResult.NetworkError -> result
        }
    }

    private fun unsupported(id: String) = ApiResult.Error(0, "unsupported", "This server doesn't offer $id.")
}

internal fun ApiResult<*>.message(): String? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Error -> message.ifBlank { error.ifBlank { "The server refused the request." } } + if (code > 0) " ($code)" else ""
    is ApiResult.NetworkError -> "Couldn't reach the server."
}

private fun <T> ApiResult<T>.orNull(): T? = (this as? ApiResult.Success)?.data

/** A short message to show once, e.g. "Approved". */
data class AdminNotice(val text: String, val isError: Boolean = false, val id: Long = System.nanoTime())

// ---------------------------------------------------------------- Dashboard

data class AdminDashboardState(
    val loading: Boolean = true,
    val status: AdminServerStatus? = null,
    val build: AdminBuildInfo? = null,
    val stats: AdminDashboardStats? = null,
    val resources: AdminSystemResources? = null,
    val requestCounts: AdminRequestCounts? = null,
    val sessions: List<AdminPlaybackSession> = emptyList(),
    val error: String? = null,
    val busySessionId: String? = null,
    val restarting: Boolean = false,
    val notice: AdminNotice? = null,
)

/** Fork: server health, numbers, and who is watching what, refreshed every 10 seconds. */
class AdminDashboardViewModel(private val service: AdminService) : ViewModel() {
    private val _state = MutableStateFlow(AdminDashboardState())
    val state: StateFlow<AdminDashboardState> = _state.asStateFlow()
    private var poller: Job? = null
    private val lastSequence = HashMap<String, Long>()

    /** Refreshes while the screen is shown. */
    fun setVisible(visible: Boolean) {
        poller?.cancel()
        if (!visible) return
        poller = viewModelScope.launch {
            while (isActive) {
                load()
                delay(10_000)
            }
        }
    }

    fun refresh() { viewModelScope.launch { load() } }

    private suspend fun load() = coroutineScope {
        val status = async { service.get(AdminOps.SERVER_STATUS, AdminServerStatus.serializer()) }
        val build = async { service.get(AdminOps.BUILD, AdminBuildInfo.serializer()) }
        val stats = async { service.get(AdminOps.STATS, AdminDashboardStats.serializer()) }
        val resources = async { service.get(AdminOps.RESOURCES, AdminSystemResources.serializer()) }
        val counts = async { service.get(AdminOps.REQUEST_COUNTS, AdminRequestCounts.serializer()) }
        val sessions = async {
            service.get(AdminOps.SESSIONS, AdminCollection.serializer(AdminPlaybackSession.serializer()), query = mapOf("limit" to "50"))
        }
        val s = status.await()
        val sessionList = sessions.await()
        _state.update { current ->
            current.copy(
                loading = false,
                status = s.orNull() ?: current.status,
                build = build.await().orNull() ?: current.build,
                stats = stats.await().orNull() ?: current.stats,
                resources = resources.await().orNull() ?: current.resources,
                requestCounts = counts.await().orNull() ?: current.requestCounts,
                sessions = sessionList.orNull()?.items ?: current.sessions,
                error = s.message() ?: sessionList.message(),
            )
        }
    }

    fun pause(session: AdminPlaybackSession) = command(session, AdminOps.SESSION_PAUSE, "Paused")
    fun resume(session: AdminPlaybackSession) = command(session, AdminOps.SESSION_RESUME, "Resumed")
    fun stop(session: AdminPlaybackSession) = command(session, AdminOps.SESSION_STOP, "Stopped")

    fun message(session: AdminPlaybackSession, text: String) {
        if (text.isBlank()) return
        command(session, AdminOps.SESSION_MESSAGE, "Message sent") { put("message", text.trim()); put("title", "Message from your server") }
    }

    /**
     * Session commands carry an id and a sequence that rises per session, as
     * the web admin sends them; the server applies each once.
     */
    private fun command(
        session: AdminPlaybackSession,
        id: String,
        done: String,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit = {},
    ) {
        if (_state.value.busySessionId != null) return
        val sequence = maxOf((lastSequence[session.sessionId] ?: 0L) + 1, System.currentTimeMillis())
        lastSequence[session.sessionId] = sequence
        val body = buildJsonObject {
            put("command_id", UUID.randomUUID().toString())
            put("sequence", sequence)
            extra()
        }
        _state.update { it.copy(busySessionId = session.sessionId) }
        viewModelScope.launch {
            val result = service.send(id, mapOf("session_id" to session.sessionId), body)
            _state.update {
                it.copy(busySessionId = null, notice = AdminNotice(result.message() ?: done, isError = result !is ApiResult.Success))
            }
            load()
        }
    }

    fun restartServer() {
        _state.update { it.copy(restarting = true) }
        viewModelScope.launch {
            val result = service.send(AdminOps.SERVER_RESTART)
            _state.update {
                it.copy(restarting = false, notice = AdminNotice(result.message() ?: "Restart requested", result !is ApiResult.Success))
            }
            load()
        }
    }
}

// ---------------------------------------------------------------- Requests

enum class AdminRequestView(val key: String, val title: String) {
    NeedsApproval("needs_approval", "Needs approval"),
    InProgress("in_progress", "In progress"),
    Failed("failed", "Failed"),
    Done("done", "Done"),
}

data class AdminRequestsState(
    val view: AdminRequestView = AdminRequestView.NeedsApproval,
    val counts: AdminRequestCounts? = null,
    val items: List<MediaRequest> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val busyId: String? = null,
    val usernames: Map<String, String> = emptyMap(),
    val notice: AdminNotice? = null,
) {
    fun count(view: AdminRequestView): Int? = counts?.let {
        when (view) {
            AdminRequestView.NeedsApproval -> it.needsApproval
            AdminRequestView.InProgress -> it.inProgress
            AdminRequestView.Failed -> it.failed
            AdminRequestView.Done -> it.done
        }
    }
}

/** Fork: request moderation — approve, decline, retry and cancel. */
class AdminRequestsViewModel(private val service: AdminService) : ViewModel() {
    private val _state = MutableStateFlow(AdminRequestsState())
    val state: StateFlow<AdminRequestsState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            val users = service.get(
                AdminOps.USERS,
                AdminCollection.serializer(AdminUserSummary.serializer()),
                query = mapOf("limit" to "200"),
            ).orNull()?.items.orEmpty()
            _state.update { it.copy(usernames = users.associate { u -> u.id to u.username }) }
        }
    }

    fun select(view: AdminRequestView) {
        if (view == _state.value.view) return
        _state.update { it.copy(view = view, items = emptyList(), nextCursor = null) }
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            val view = _state.value.view
            val counts = service.get(AdminOps.REQUEST_COUNTS, AdminRequestCounts.serializer()).orNull()
            val page = service.get(
                AdminOps.REQUESTS,
                AdminCollection.serializer(MediaRequest.serializer()),
                query = mapOf("view" to view.key, "limit" to "50"),
            )
            _state.update {
                it.copy(
                    loading = false,
                    counts = counts ?: it.counts,
                    items = page.orNull()?.items ?: it.items,
                    nextCursor = page.orNull()?.page?.cursor,
                    error = page.message(),
                )
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val page = service.get(
                AdminOps.REQUESTS,
                AdminCollection.serializer(MediaRequest.serializer()),
                query = mapOf("view" to current.view.key, "limit" to "50"),
                cursor = cursor,
            )
            _state.update {
                it.copy(
                    loadingMore = false,
                    items = it.items + page.orNull()?.items.orEmpty(),
                    nextCursor = page.orNull()?.page?.cursor,
                    notice = page.message()?.let { m -> AdminNotice(m, true) } ?: it.notice,
                )
            }
        }
    }

    fun approve(request: MediaRequest) = act(request, AdminOps.REQUEST_APPROVE, "Approved", null)
    fun decline(request: MediaRequest, reason: String) = act(request, AdminOps.REQUEST_DECLINE, "Declined", reason)
    fun retry(request: MediaRequest) = act(request, AdminOps.REQUEST_RETRY, "Sent again", null)
    fun cancel(request: MediaRequest) = act(request, AdminOps.REQUEST_CANCEL, "Cancelled", null)

    private fun act(request: MediaRequest, id: String, done: String, reason: String?) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = request.id) }
        viewModelScope.launch {
            val body = buildJsonObject { reason?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reason", it) } }
            val result = service.send(id, mapOf("id" to request.id), body)
            _state.update {
                it.copy(busyId = null, notice = AdminNotice(result.message()?.let { m -> "${request.title}: $m" } ?: "${request.title}: $done", result !is ApiResult.Success))
            }
            if (result is ApiResult.Success) load()
        }
    }
}

// ---------------------------------------------------------------- Tasks and scans

data class AdminTasksState(
    val loading: Boolean = true,
    val tasks: List<AdminTask> = emptyList(),
    val libraries: List<AdminLibrary> = emptyList(),
    val error: String? = null,
    val busyKey: String? = null,
    val notice: AdminNotice? = null,
) {
    val anyRunning: Boolean get() = tasks.any { it.isRunning }
}

/** Fork: scheduled tasks (run, cancel) and library scans. */
class AdminTasksViewModel(private val service: AdminService) : ViewModel() {
    private val _state = MutableStateFlow(AdminTasksState())
    val state: StateFlow<AdminTasksState> = _state.asStateFlow()
    private var poller: Job? = null

    fun setVisible(visible: Boolean) {
        poller?.cancel()
        if (!visible) return
        poller = viewModelScope.launch {
            while (isActive) {
                load()
                // Follow a running task closely; otherwise check now and then.
                delay(if (_state.value.anyRunning) 3_000 else 15_000)
            }
        }
    }

    fun refresh() { viewModelScope.launch { load() } }

    private suspend fun load() = coroutineScope {
        val tasks = async { service.get(AdminOps.TASKS, AdminCollection.serializer(AdminTask.serializer())) }
        val libraries = async {
            service.get(AdminOps.LIBRARIES, AdminCollection.serializer(AdminLibrary.serializer()), query = mapOf("limit" to "200"))
        }
        val t = tasks.await()
        _state.update {
            it.copy(
                loading = false,
                tasks = t.orNull()?.items?.sortedWith(compareBy({ task -> !task.isRunning }, { task -> task.category }, { task -> task.name.lowercase() }))
                    ?: it.tasks,
                libraries = libraries.await().orNull()?.items ?: it.libraries,
                error = t.message(),
            )
        }
    }

    fun run(task: AdminTask) = act(task.key, AdminOps.TASK_RUN, mapOf("key" to task.key), null, "${task.name} started")
    fun cancel(task: AdminTask) = act(task.key, AdminOps.TASK_CANCEL, mapOf("key" to task.key), null, "${task.name} cancelling")

    fun scanAll() = act("scan:all", AdminOps.SCAN, emptyMap(), buildJsonObject { }, "Scanning all libraries")

    fun scan(library: AdminLibrary) =
        act("scan:${library.id}", AdminOps.SCAN, emptyMap(), buildJsonObject { put("library_id", library.id) }, "Scanning ${library.name}")

    fun cancelScan(library: AdminLibrary) =
        act("cancel:${library.id}", AdminOps.SCAN_CANCEL, emptyMap(), buildJsonObject { put("library_id", library.id) }, "Scan of ${library.name} cancelled")

    /** The server cancels scans one library at a time. */
    fun cancelAllScans() {
        if (_state.value.busyKey != null) return
        val libraries = _state.value.libraries
        _state.update { it.copy(busyKey = "cancel:all") }
        viewModelScope.launch {
            var cancelled = 0
            var failure: String? = null
            for (library in libraries) {
                val result = service.send(AdminOps.SCAN_CANCEL, body = buildJsonObject { put("library_id", library.id) })
                if (result is ApiResult.Success) {
                    cancelled += ((result.data as? kotlinx.serialization.json.JsonObject)?.get("cancelled") as? kotlinx.serialization.json.JsonPrimitive)
                        ?.content?.toIntOrNull() ?: 0
                } else failure = failure ?: result.message()
            }
            _state.update {
                it.copy(busyKey = null, notice = failure?.let { m -> AdminNotice(m, true) }
                    ?: AdminNotice(if (cancelled == 0) "No scans were running" else "Cancelled $cancelled scan${if (cancelled == 1) "" else "s"}"))
            }
            load()
        }
    }

    private fun act(key: String, id: String, path: Map<String, String>, body: JsonElement?, done: String) {
        if (_state.value.busyKey != null) return
        _state.update { it.copy(busyKey = key) }
        viewModelScope.launch {
            val result = service.send(id, path, body)
            _state.update { it.copy(busyKey = null, notice = AdminNotice(result.message() ?: done, result !is ApiResult.Success)) }
            load()
        }
    }
}

// ---------------------------------------------------------------- Logs

enum class AdminLogLevel(val query: String?, val title: String) {
    All(null, "All levels"),
    Problems("error,warn", "Errors and warnings"),
    Errors("error", "Errors"),
    Info("info", "Info"),
    Debug("debug", "Debug"),
}

enum class AdminLogStream(val title: String) { App("Server log"), Audit("Admin audit") }

data class AdminLogsState(
    val stream: AdminLogStream = AdminLogStream.App,
    val level: AdminLogLevel = AdminLogLevel.All,
    val query: String = "",
    val app: List<AdminOperationalLog> = emptyList(),
    val audit: List<AdminAuditLog> = emptyList(),
    val nextCursor: String? = null,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

/** Fork: the server's log and the admin audit trail, newest first. */
class AdminLogsViewModel(private val service: AdminService) : ViewModel() {
    private val _state = MutableStateFlow(AdminLogsState())
    val state: StateFlow<AdminLogsState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun setStream(stream: AdminLogStream) { _state.update { it.copy(stream = stream) }; load() }
    fun setLevel(level: AdminLogLevel) { _state.update { it.copy(level = level) }; load() }
    fun search(query: String) { _state.update { it.copy(query = query.trim()) }; load() }

    private fun query(state: AdminLogsState): Map<String, String> = buildMap {
        put("limit", "100")
        if (state.stream == AdminLogStream.App) {
            state.level.query?.let { put("level", it) }
            if (state.query.isNotBlank()) put("q", state.query)
        } else if (state.query.isNotBlank()) {
            put("path_prefix", state.query)
        }
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val state = _state.value
            when (state.stream) {
                AdminLogStream.App -> {
                    val page = service.get(AdminOps.LOGS_APP, AdminCollection.serializer(AdminOperationalLog.serializer()), query = query(state))
                    _state.update { it.copy(loading = false, app = page.orNull()?.items.orEmpty(), nextCursor = page.orNull()?.page?.cursor, error = page.message()) }
                }
                AdminLogStream.Audit -> {
                    val page = service.get(AdminOps.LOGS_AUDIT, AdminCollection.serializer(AdminAuditLog.serializer()), query = query(state))
                    _state.update { it.copy(loading = false, audit = page.orNull()?.items.orEmpty(), nextCursor = page.orNull()?.page?.cursor, error = page.message()) }
                }
            }
        }
    }

    fun loadMore() {
        val state = _state.value
        val cursor = state.nextCursor ?: return
        if (state.loadingMore || state.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            when (state.stream) {
                AdminLogStream.App -> {
                    val page = service.get(AdminOps.LOGS_APP, AdminCollection.serializer(AdminOperationalLog.serializer()), query = query(state), cursor = cursor)
                    _state.update { it.copy(loadingMore = false, app = it.app + page.orNull()?.items.orEmpty(), nextCursor = page.orNull()?.page?.cursor) }
                }
                AdminLogStream.Audit -> {
                    val page = service.get(AdminOps.LOGS_AUDIT, AdminCollection.serializer(AdminAuditLog.serializer()), query = query(state), cursor = cursor)
                    _state.update { it.copy(loadingMore = false, audit = it.audit + page.orNull()?.items.orEmpty(), nextCursor = page.orNull()?.page?.cursor) }
                }
            }
        }
    }
}
