package org.siloserver.silo.admin

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Fork: the four groups the admin console's home shows, in order. */
enum class AdminCategory(val title: String, val blurb: String) {
    Activity("Activity & requests", "What's playing, requests, tasks, scans and logs"),
    People("People & access", "Users, invitations, access groups, policy, devices and API keys"),
    Media("Media & libraries", "Libraries, scans, collections, home rows, metadata and subtitles"),
    Server("Server setup", "Settings, nodes, plugins, notifications, branding and maintenance"),
}

/** One admin area (`users`, `libraries`, ...) and every operation on it. */
data class AdminResource(
    val key: String,
    val title: String,
    val category: AdminCategory,
    val operations: List<AdminOperation>,
) {
    /** The read that best represents the area: its top-level list, else its first plain read. */
    val primaryRead: AdminOperation?
        get() = operations.firstOrNull { it.returnsList && it.pathParams.isEmpty() && it.relativePath == key }
            ?: operations.firstOrNull { it.isRead && it.pathParams.isEmpty() && it.relativePath == key }
            ?: operations.firstOrNull { it.returnsList && it.pathParams.isEmpty() }
}

/** A one-tap shortcut on the console home, with the query it opens with. */
data class AdminQuickAction(
    val title: String,
    val subtitle: String,
    val operation: AdminOperation,
    val query: Map<String, String> = emptyMap(),
)

/**
 * Fork: groups the [AdminSpec] into areas and categories for the console, and
 * finds the operations that apply to one item of a list.
 */
class AdminCatalog(val spec: AdminSpec) {
    val resources: List<AdminResource> = spec.operations
        .groupBy { it.resourceKey }
        .map { (key, ops) -> AdminResource(key, RESOURCE_TITLES[key] ?: humanizeKey(key), categoryFor(key), ops) }
        .sortedWith(compareBy({ it.category.ordinal }, { it.title.lowercase() }))

    fun resourcesIn(category: AdminCategory): List<AdminResource> = resources.filter { it.category == category }

    fun resource(key: String): AdminResource? = resources.firstOrNull { it.key == key }

    fun operation(method: String, path: String): AdminOperation? =
        spec.operations.firstOrNull { it.method == method && it.path == path }

    private fun op(method: String, relative: String): AdminOperation? =
        spec.operations.firstOrNull { it.method == method && it.relativePath == relative }

    /** Shortcuts for the day-to-day jobs, where the server offers them. */
    val quickActions: List<AdminQuickAction> = listOfNotNull(
        op("GET", "sessions")?.let { AdminQuickAction("Now playing", "Who is watching what", it) },
        op("GET", "requests")?.let { AdminQuickAction("Pending requests", "Approve or decline", it, mapOf("status" to "pending")) },
        op("GET", "tasks")?.let { AdminQuickAction("Scheduled tasks", "Run or cancel a task", it) },
        op("POST", "scan")?.let { AdminQuickAction("Scan libraries", "Look for new or changed files", it) },
        op("GET", "libraries")?.let { AdminQuickAction("Libraries", "Edit, refresh or remove", it) },
        op("GET", "stats")?.let { AdminQuickAction("Server stats", "Library and playback numbers", it) },
        op("GET", "server/status")?.let { AdminQuickAction("Server status", "Version, uptime and health", it) },
        op("GET", "logs/app")?.let { AdminQuickAction("Logs", "Recent server log entries", it) },
        op("GET", "users")?.let { AdminQuickAction("Users", "Accounts and their profiles", it) },
    )

    /**
     * Operations on one item of the list [from] returned: same area, a path
     * below the list's with exactly one more `{param}`, which the item fills
     * (its field of that name, else its `id` / `key`). Path values are filled.
     */
    fun itemActions(from: AdminOperation, fromPathValues: Map<String, String>, item: JsonElement): List<AdminBoundOperation> {
        val obj = item as? JsonObject ?: return emptyList()
        val listPath = from.relativePath
        return spec.operations.mapNotNull { candidate ->
            if (candidate === from || !candidate.relativePath.startsWith("$listPath/{")) return@mapNotNull null
            val missing = candidate.pathParams.map { it.name }.filterNot { it in fromPathValues }
            if (missing.size != 1) return@mapNotNull null
            val value = itemValueFor(obj, missing.single()) ?: return@mapNotNull null
            AdminBoundOperation(candidate, fromPathValues + (missing.single() to value))
        }.sortedWith(ACTION_ORDER)
    }

    /**
     * Operations on the record a detail read [from] returned, with every path
     * value already known: the other methods on the same path (edit, delete)
     * and the sub-paths below it (`users/{id}/profiles`, `tasks/{key}/run`).
     */
    fun detailActions(from: AdminOperation, pathValues: Map<String, String>): List<AdminBoundOperation> {
        if (from.pathParams.isEmpty()) return emptyList()
        return spec.operations.mapNotNull { candidate ->
            if (candidate === from) return@mapNotNull null
            val samePath = candidate.relativePath == from.relativePath
            val below = candidate.relativePath.startsWith(from.relativePath + "/")
            if (!samePath && !below) return@mapNotNull null
            if (candidate.pathParams.any { it.name !in pathValues }) return@mapNotNull null
            AdminBoundOperation(candidate, pathValues)
        }.sortedWith(ACTION_ORDER)
    }

    companion object {
        /** Which value of an item fills `{name}`. */
        internal fun itemValueFor(item: JsonObject, name: String): String? {
            fun field(key: String): String? {
                val p = item[key] as? JsonPrimitive ?: return null
                val text = p.contentOrNull ?: return null
                return text.takeIf { p.isString || text.toDoubleOrNull() != null }?.takeIf { it.isNotBlank() }
            }
            field(name)?.let { return it }
            if (name == "id" || name == "key" || name.endsWith("_id") || name.endsWith("Id")) {
                field("id")?.let { return it }
                field("key")?.let { return it }
            }
            return null
        }

        /** Reads first, then other actions, then deletes. */
        private val ACTION_ORDER = compareBy<AdminBoundOperation>(
            { it.operation.isDelete },
            { !it.operation.isRead },
            { it.operation.relativePath.length },
            { it.operation.method },
        )

        private val RESOURCE_TITLES = mapOf(
            "sessions" to "Now playing",
            "node-sessions" to "Node sessions",
            "ai" to "AI services",
            "api-keys" to "API keys",
            "ips" to "IP addresses",
            "jellyfin-compat" to "Jellyfin compatibility",
            "scan" to "Library scans",
            "library-jobs" to "Library jobs",
            "request-routes" to "Request routing rules",
            "request-routing" to "Request routing",
            "request-integrations" to "Request integrations",
            "request-settings" to "Request settings",
            "request-users" to "Request limits (users)",
            "request-groups" to "Request limits (groups)",
            "history-imports" to "Watch history import",
            "history-import-sources" to "History import sources",
            "playback-history" to "Playback history",
            "stream-telemetry" to "Stream telemetry",
            "storage-transitions" to "Storage moves",
            "literary-works" to "Book works",
            "unmatched" to "Unmatched items",
        )

        private val ACTIVITY = setOf(
            "dashboard", "stats", "sessions", "node-sessions", "requests", "request-counts", "tasks", "jobs",
            "logs", "server", "system", "playback-history", "stream-telemetry", "downloads",
        )
        private val PEOPLE = setOf(
            "users", "invitations", "invite-codes", "access-groups", "policy", "devices", "api-keys",
            "ips", "rate-limits", "request-users", "request-groups",
        )
        private val MEDIA = setOf(
            "libraries", "library-jobs", "scan", "autoscan", "catalog", "items", "people", "literary-works",
            "collections", "collection-groups", "collection-jobs", "sections", "recommendations", "markers",
            "subtitles", "subtitle-providers", "ai", "rating-sources", "trickplay", "files", "filesystem",
            "storage-transitions", "unmatched", "request-routes", "request-routing", "request-integrations",
            "request-settings",
        )

        fun categoryFor(key: String): AdminCategory = when (key) {
            in ACTIVITY -> AdminCategory.Activity
            in PEOPLE -> AdminCategory.People
            in MEDIA -> AdminCategory.Media
            else -> AdminCategory.Server
        }
    }
}

/** An operation with some or all of its `{path}` values chosen. */
data class AdminBoundOperation(
    val operation: AdminOperation,
    val pathValues: Map<String, String> = emptyMap(),
) {
    val missingPathParams: List<AdminParam>
        get() = operation.pathParams.filter { pathValues[it.name].isNullOrBlank() }
}
