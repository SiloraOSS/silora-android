package org.siloserver.silo.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Fork: the server's admin API, read from the bundled spec
 * (`android-shared/src/androidMain/assets/admin/openapi-admin.json`, produced
 * by `scripts/admin-spec.py` from silo-server's OpenAPI contract).
 *
 * The admin console builds its generic screens (lists, forms, actions) from
 * this, so every admin operation the server documents is reachable without a
 * hand-written screen for it.
 */
class AdminSpec private constructor(
    val version: String,
    /** The silo-server commit and date the bundled contract came from, when the script recorded it. */
    val source: String?,
    val operations: List<AdminOperation>,
    private val schemas: Map<String, JsonObject>,
) {
    private val resolved = HashMap<String, AdminSchema>()

    /** Follows `$ref` and wraps [raw]. Reference cycles resolve lazily. */
    fun schema(raw: JsonElement?): AdminSchema? {
        val obj = raw as? JsonObject ?: return null
        val ref = (obj["\$ref"] as? JsonPrimitive)?.contentOrNull
        if (ref != null) {
            val name = ref.substringAfterLast('/')
            resolved[name]?.let { return it }
            val target = schemas[name] ?: return null
            // Merge a sibling description onto the referenced schema.
            val description = (obj["description"] as? JsonPrimitive)?.contentOrNull
            val node = AdminSchema(this, target, name, description)
            if (description == null) resolved[name] = node
            return node
        }
        return AdminSchema(this, obj, null, null)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): AdminSpec {
            val root = json.parseToJsonElement(text).jsonObject
            val schemas = (root["schemas"] as? JsonObject).orEmpty()
                .mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }.toMap()
            val info = root["info"] as? JsonObject
            val version = (info?.get("version") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val source = (info?.get("source") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            val ops = ArrayList<AdminOperation>()
            lateinit var spec: AdminSpec
            val paths = (root["paths"] as? JsonObject).orEmpty()
            spec = AdminSpec(version, source, ops, schemas)
            for ((path, item) in paths) {
                val methods = item as? JsonObject ?: continue
                for ((method, rawOp) in methods) {
                    val op = rawOp as? JsonObject ?: continue
                    ops += AdminOperation.parse(spec, method.uppercase(), path, op)
                }
            }
            ops.sortWith(compareBy({ it.path }, { METHOD_ORDER.indexOf(it.method) }))
            return spec
        }

        private val METHOD_ORDER = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    }
}

data class AdminParam(
    val name: String,
    /** "path", "query" or "header". */
    val location: String,
    val required: Boolean,
    val description: String?,
    val schema: AdminSchema?,
)

data class AdminRequestBody(
    val required: Boolean,
    val mediaType: String,
    val schema: AdminSchema?,
) {
    val isJson: Boolean get() = mediaType == "application/json"
}

class AdminOperation(
    val method: String,
    /** Full path template, e.g. `/api/v2/admin/users/{id}`. */
    val path: String,
    val operationId: String,
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val parameters: List<AdminParam>,
    val requestBody: AdminRequestBody?,
    /** Success status → response schema (null for empty bodies such as 204). */
    val responses: Map<Int, AdminSchema?>,
    /** Server-declared `x-silo-guarded`: the call requires an `If-Match` version tag. */
    val guarded: Boolean = false,
    /** Server-declared `x-silo-raw-protocol` (a download or WebSocket), else null. */
    val rawProtocol: String? = null,
    /** Server-declared `x-silo-retry-safety` (`non_retryable`, `natural_idempotent`, ...). */
    val retrySafety: String? = null,
    /** Media types of success bodies that aren't JSON (a gzip archive, subtitle bytes). */
    val binaryResponseTypes: List<String> = emptyList(),
) {
    /** Path below `/api/v2/`, without the `admin/` prefix: `users/{id}`. */
    val relativePath: String = path.removePrefix("/api/v2/").removePrefix("admin/")

    /** First path segment: the resource this operation belongs to (`users`). */
    val resourceKey: String = relativePath.substringBefore('/')

    val pathParams: List<AdminParam> get() = parameters.filter { it.location == "path" }
    val queryParams: List<AdminParam> get() = parameters.filter { it.location == "query" }

    /** Requires an `If-Match` version tag (optimistic concurrency). */
    val needsIfMatch: Boolean
        get() = guarded || parameters.any { it.location == "header" && it.name.equals("If-Match", true) && it.required }
    val acceptsIfMatch: Boolean get() = needsIfMatch || parameters.any { it.location == "header" && it.name.equals("If-Match", true) }

    /**
     * The console can send this and show its answer: JSON in and out. Uploads
     * (multipart, raw bytes), downloads and the log WebSocket are left to the
     * web admin.
     */
    val isSupported: Boolean
        get() = rawProtocol == null && binaryResponseTypes.isEmpty() && (requestBody == null || requestBody.isJson)

    val isRead: Boolean get() = method == "GET"
    val isDelete: Boolean get() = method == "DELETE"

    /** Changes the server; the console asks before sending it. */
    val needsConfirmation: Boolean get() = !isRead

    /**
     * Removes, overwrites or interrupts something: delete, plus changes the
     * name marks as such. Shown in red; every change is confirmed either way.
     */
    val isDestructive: Boolean
        get() = !isRead && (isDelete || DESTRUCTIVE_WORDS.any { operationId.contains(it, ignoreCase = true) })

    val successSchema: AdminSchema? get() = responses.entries.sortedBy { it.key }.firstOrNull { it.value != null }?.value

    /** Response is a page of items (`{items: [...], page: {...}}`). */
    val returnsList: Boolean get() = isRead && successSchema?.property("items")?.isArray == true

    /** Human label from the operation id: `listAdminUsers` → `List users`. */
    val title: String = humanizeOperationId(operationId)

    override fun toString(): String = "$method $path"

    companion object {
        private val DESTRUCTIVE_WORDS = listOf(
            "delete", "remove", "reset", "restart", "purge", "revoke", "wipe", "clear", "disable", "restore",
            "terminate", "stop", "rotate", "drain", "cancel", "decline", "cleanup", "regenerate", "replace",
            "merge", "uninstall", "import",
        )

        internal fun parse(spec: AdminSpec, method: String, path: String, op: JsonObject): AdminOperation {
            fun str(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
            val params = (op["parameters"] as? JsonArray).orEmpty().mapNotNull { element ->
                val p = element as? JsonObject ?: return@mapNotNull null
                AdminParam(
                    name = str(p, "name") ?: return@mapNotNull null,
                    location = str(p, "in") ?: "query",
                    required = (p["required"] as? JsonPrimitive)?.booleanOrNull == true,
                    description = str(p, "description"),
                    schema = spec.schema(p["schema"]),
                )
            }
            val body = (op["requestBody"] as? JsonObject)?.let { b ->
                AdminRequestBody(
                    required = (b["required"] as? JsonPrimitive)?.booleanOrNull == true,
                    mediaType = str(b, "mediaType") ?: "application/json",
                    schema = spec.schema(b["schema"]),
                )
            }
            val responseObjects = (op["responses"] as? JsonObject).orEmpty().mapNotNull { (code, r) ->
                val status = code.toIntOrNull() ?: return@mapNotNull null
                status to (r as? JsonObject)
            }
            val responses = responseObjects.associate { (status, r) -> status to spec.schema(r?.get("schema")) }
            val binaryTypes = responseObjects.mapNotNull { (_, r) -> r?.let { str(it, "mediaType") } }
            return AdminOperation(
                method = method,
                path = path,
                operationId = str(op, "operationId").orEmpty(),
                summary = str(op, "summary"),
                description = str(op, "description"),
                tags = (op["tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                parameters = params,
                requestBody = body,
                responses = responses,
                guarded = (op["x-silo-guarded"] as? JsonPrimitive)?.booleanOrNull == true,
                rawProtocol = str(op, "x-silo-raw-protocol")?.takeIf { it.isNotBlank() },
                retrySafety = str(op, "x-silo-retry-safety"),
                binaryResponseTypes = binaryTypes,
            )
        }
    }
}

/** One schema node, `$ref`s already followed. */
class AdminSchema internal constructor(
    private val spec: AdminSpec,
    val raw: JsonObject,
    /** Component name when this came from a `$ref`. */
    val name: String?,
    private val overrideDescription: String?,
) {
    private fun str(key: String) = (raw[key] as? JsonPrimitive)?.contentOrNull

    val types: Set<String> = when (val t = raw["type"]) {
        is JsonPrimitive -> setOfNotNull(t.contentOrNull)
        is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        else -> if (raw["properties"] != null) setOf("object") else emptySet()
    }
    val nullable: Boolean get() = "null" in types
    val primaryType: String? get() = types.firstOrNull { it != "null" }
    val isObject: Boolean get() = primaryType == "object"
    val isArray: Boolean get() = primaryType == "array"
    val isString: Boolean get() = primaryType == "string"
    val isInteger: Boolean get() = primaryType == "integer"
    val isNumber: Boolean get() = primaryType == "number"
    val isBoolean: Boolean get() = primaryType == "boolean"

    val format: String? get() = str("format")
    val description: String? get() = overrideDescription ?: str("description")
    val title: String? get() = str("title")
    val readOnly: Boolean get() = (raw["readOnly"] as? JsonPrimitive)?.booleanOrNull == true
    val writeOnly: Boolean get() = (raw["writeOnly"] as? JsonPrimitive)?.booleanOrNull == true
    val minimum: Double? get() = (raw["minimum"] as? JsonPrimitive)?.doubleOrNull
    val maximum: Double? get() = (raw["maximum"] as? JsonPrimitive)?.doubleOrNull
    val maxLength: Int? get() = (raw["maxLength"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
    val default: JsonElement? get() = raw["default"]?.takeIf { it !is JsonNull }
    val enumValues: List<String>
        get() = (raw["enum"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val required: Set<String>
        get() = (raw["required"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()

    val properties: Map<String, AdminSchema> by lazy {
        (raw["properties"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> spec.schema(v)?.let { k to it } }.toMap()
    }
    val items: AdminSchema? by lazy { spec.schema(raw["items"]) }

    /** Schema of free-form map values (`additionalProperties: {...}`), if any. */
    val mapValues: AdminSchema? by lazy { spec.schema(raw["additionalProperties"] as? JsonObject) }

    fun property(name: String): AdminSchema? = properties[name]
}

/** `listAdminUsers` → `List users`; `getAdminServerStatus` → `Get server status`. */
fun humanizeOperationId(id: String): String {
    if (id.isBlank()) return "Operation"
    val words = id.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
        .split(' ', '_', '-')
        .filter { it.isNotBlank() }
        .filterNot { it.equals("admin", ignoreCase = true) }
    if (words.isEmpty()) return "Operation"
    return words.mapIndexed { i, w ->
        val lower = if (w.length > 1 && w.all { it.isUpperCase() || it.isDigit() }) w else w.lowercase()
        if (i == 0) lower.replaceFirstChar { it.uppercase() } else lower
    }.joinToString(" ")
}

/** `request_routes` / `requestRoutes` / `request-routes` → `Request routes`. */
fun humanizeKey(key: String): String {
    val words = key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").split('_', '-', ' ', '.').filter { it.isNotBlank() }
    if (words.isEmpty()) return key
    val acronyms = setOf("id", "ids", "url", "api", "ai", "ip", "ips", "tv", "hdr", "fcm", "tmdb", "tvdb", "imdb", "oidc", "ttl", "cpu", "gpu", "ram", "uuid", "smtp", "hls", "dv")
    return words.mapIndexed { i, w ->
        when {
            w.lowercase() in acronyms -> w.uppercase()
            i == 0 -> w.lowercase().replaceFirstChar { it.uppercase() }
            else -> w.lowercase()
        }
    }.joinToString(" ")
}
