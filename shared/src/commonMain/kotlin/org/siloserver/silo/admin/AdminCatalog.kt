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
    /** The operations the console offers: those it can send and show (see [AdminOperation.isSupported]). */
    val operations: List<AdminOperation> = spec.operations.filter { it.isSupported }

    val resources: List<AdminResource> = operations
        .groupBy { it.resourceKey }
        .map { (key, ops) -> AdminResource(key, RESOURCE_TITLES[key] ?: humanizeKey(key), categoryFor(key), ops) }
        .sortedWith(compareBy({ it.category.ordinal }, { it.title.lowercase() }))

    fun resourcesIn(category: AdminCategory): List<AdminResource> = resources.filter { it.category == category }

    fun resource(key: String): AdminResource? = resources.firstOrNull { it.key == key }

    fun operation(method: String, path: String): AdminOperation? =
        operations.firstOrNull { it.method == method && it.path == path }

    private fun op(method: String, relative: String): AdminOperation? =
        operations.firstOrNull { it.method == method && it.relativePath == relative }

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
     * Operations on one item of the list [from] returned: a path below the
     * list's whose next segment is a `{param}`. The list's own path values
     * carry over (by position, so `users/{id}` fills `users/{user_id}/...`),
     * and the item fills every other one: the first from its field of that
     * name, its `id` / `key`, `<name>_name` / `<name>_number`, or its one field
     * ending in `_<name>`; later ones only from a field of that name (or
     * `<name>_name` / `<name>_number`).
     */
    fun itemActions(from: AdminOperation, fromPathValues: Map<String, String>, item: JsonElement): List<AdminBoundOperation> {
        val obj = item as? JsonObject ?: return emptyList()
        val listTemplate = template(from.relativePath)
        return operations.mapNotNull { candidate ->
            if (candidate === from || !template(candidate.relativePath).startsWith("$listTemplate/{}")) return@mapNotNull null
            val carried = carry(from, fromPathValues, candidate)
            val missing = paramNames(candidate.relativePath).filterNot { it in carried }
            if (missing.isEmpty()) return@mapNotNull null
            val filled = missing.mapIndexed { index, name ->
                name to (itemValueFor(obj, name, primary = index == 0) ?: return@mapNotNull null)
            }
            AdminBoundOperation(candidate, carried + filled)
        }.sortedWith(ACTION_ORDER)
    }

    /**
     * Operations on the record a detail read [from] returned, with every path
     * value already known: the other methods on the same path (edit, delete)
     * and the sub-paths below it (`users/{id}/profiles`, `tasks/{key}/run`).
     * Paths are compared with their `{param}` names ignored, so `users/{id}`
     * also offers `users/{user_id}/login-sessions`.
     */
    fun detailActions(from: AdminOperation, pathValues: Map<String, String>): List<AdminBoundOperation> {
        if (from.pathParams.isEmpty()) return emptyList()
        val fromTemplate = template(from.relativePath)
        return operations.mapNotNull { candidate ->
            if (candidate === from) return@mapNotNull null
            val candidateTemplate = template(candidate.relativePath)
            val samePath = candidateTemplate == fromTemplate
            val below = candidateTemplate.startsWith("$fromTemplate/")
            if (!samePath && !below) return@mapNotNull null
            val values = carry(from, pathValues, candidate)
            if (paramNames(candidate.relativePath).any { it !in values }) return@mapNotNull null
            AdminBoundOperation(candidate, values)
        }.sortedWith(ACTION_ORDER)
    }

    companion object {
        private val PARAM = Regex("""\{([^}]+)\}""")

        /** `users/{id}/profiles` → `users/{}/profiles`. */
        internal fun template(path: String): String = path.replace(PARAM, "{}")

        /** The `{param}` names of [path], in order. */
        internal fun paramNames(path: String): List<String> = PARAM.findAll(path).map { it.groupValues[1] }.toList()

        /**
         * [values] for [from]'s path params, renamed to [candidate]'s params
         * at the same positions (the candidate's path starts with [from]'s).
         */
        internal fun carry(from: AdminOperation, values: Map<String, String>, candidate: AdminOperation): Map<String, String> {
            val candidateNames = paramNames(candidate.relativePath)
            val out = LinkedHashMap<String, String>()
            paramNames(from.relativePath).forEachIndexed { index, name ->
                val value = values[name]?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                candidateNames.getOrNull(index)?.let { out[it] = value }
            }
            return out
        }

        /**
         * Which value of an item fills `{name}`. [primary] marks the param
         * right below the list, the one that names the item itself: only it
         * falls back to the item's `id` / `key` or a single `*_<name>` field.
         */
        internal fun itemValueFor(item: JsonObject, name: String, primary: Boolean = true): String? {
            fun field(key: String): String? {
                val p = item[key] as? JsonPrimitive ?: return null
                val text = p.contentOrNull ?: return null
                return text.takeIf { p.isString || text.toDoubleOrNull() != null }?.takeIf { it.isNotBlank() }
            }
            field(name)?.let { return it }
            if (primary && (name == "id" || name == "key" || name.endsWith("_id") || name.endsWith("Id"))) {
                field("id")?.let { return it }
                field("key")?.let { return it }
            }
            field("${name}_name")?.let { return it }
            field("${name}_number")?.let { return it }
            if (primary && '_' in name) {
                // `{item_id}` from a collection member's `media_item_id`.
                item.keys.filter { it.endsWith("_$name") }.singleOrNull()?.let { key -> field(key)?.let { return it } }
            }
            return null
        }

        /** A version tag the record carries itself (`etag`, `config_etag`), for edits with no read of their own. */
        fun versionTagOf(record: JsonElement?): String? {
            val obj = record as? JsonObject ?: return null
            return obj.entries.firstOrNull { (key, value) ->
                (key.equals("etag", ignoreCase = true) || key.endsWith("_etag", ignoreCase = true)) &&
                    value is JsonPrimitive && value.isString && value.content.isNotBlank()
            }?.let { (it.value as JsonPrimitive).content }
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
