package org.siloserver.silo.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

/** How a form field is edited. */
sealed interface AdminFieldKind {
    data object Text : AdminFieldKind
    data object Secret : AdminFieldKind
    data object Integer : AdminFieldKind
    data object Decimal : AdminFieldKind
    data object Toggle : AdminFieldKind
    data class Choice(val options: List<String>) : AdminFieldKind
    /** A list of plain values, one per line. */
    data object Lines : AdminFieldKind
    /** Anything nested deeper (lists of objects, maps): edited as JSON text. */
    data object JsonText : AdminFieldKind
    /** Section heading for a nested object; holds no value. */
    data object Group : AdminFieldKind
}

/**
 * One editable field. [path] is its place in the request body
 * (`["policy", "max_streams"]`); [key] joins it with dots and keys the value
 * map the screen holds. Params use the `@path.` / `@query.` prefixes.
 */
data class AdminField(
    val key: String,
    val path: List<String>,
    val label: String,
    val help: String?,
    val kind: AdminFieldKind,
    val required: Boolean,
    val nullable: Boolean,
    val depth: Int,
    val placeholder: String? = null,
) {
    val isParam: Boolean get() = key.startsWith(PATH_PREFIX) || key.startsWith(QUERY_PREFIX)

    companion object {
        const val PATH_PREFIX = "@path."
        const val QUERY_PREFIX = "@query."
    }
}

/** The values a request is sent with, once a form validates. */
data class AdminRequestValues(
    val pathValues: Map<String, String>,
    val query: Map<String, String>,
    val body: JsonElement?,
)

/**
 * Fork: the form for one operation — its unfilled path params, query params
 * and request body fields — and the conversions between the strings the
 * screen edits and the JSON the server takes.
 */
class AdminForm(
    val operation: AdminOperation,
    /** Path values already known (from the item the action was opened on). */
    val fixedPathValues: Map<String, String> = emptyMap(),
) {
    val fields: List<AdminField> = buildList {
        operation.pathParams.filter { fixedPathValues[it.name].isNullOrBlank() }.forEach { p ->
            add(paramField(AdminField.PATH_PREFIX, p, required = true))
        }
        operation.queryParams.filterNot { it.name == "cursor" }.forEach { p ->
            add(paramField(AdminField.QUERY_PREFIX, p, required = p.required))
        }
        val body = operation.requestBody?.takeIf { it.isJson }?.schema
        if (body != null) addSchemaFields(this, body, emptyList(), depth = 0, parentRequired = true)
    }

    /** The body has fields beyond what the form edits field by field (e.g. a bare array). */
    val bodyIsRawJson: Boolean
        get() = operation.requestBody?.isJson == true && fields.none { !it.isParam } &&
            operation.requestBody.schema != null

    val hasInputs: Boolean get() = fields.isNotEmpty() || bodyIsRawJson

    /** Starting values: schema defaults, then whatever [prefill] (the current record) holds. */
    fun initialValues(prefill: JsonElement? = null): Map<String, String> {
        val values = HashMap<String, String>()
        val body = operation.requestBody?.schema
        for (field in fields) {
            if (field.kind == AdminFieldKind.Group) continue
            if (field.isParam) {
                field.placeholder?.let { } // params start empty
                continue
            }
            val schema = body?.let { schemaAt(it, field.path) }
            val current = prefill?.let { valueAt(it, field.path) }
            val source = current ?: schema?.default
            if (source != null && source !is JsonNull) values[field.key] = toText(source, field.kind)
        }
        if (bodyIsRawJson && prefill != null) values[RAW_BODY_KEY] = PRETTY.encodeToString(JsonElement.serializer(), prefill)
        return values
    }

    /** Validates [values]; returns the request, or a message naming the first problem. */
    fun build(values: Map<String, String>): Result<AdminRequestValues> {
        val pathValues = LinkedHashMap(fixedPathValues)
        val query = LinkedHashMap<String, String>()
        for (field in fields.filter { it.isParam }) {
            val raw = values[field.key]?.trim().orEmpty()
            if (raw.isEmpty()) {
                if (field.required) return Result.failure(IllegalArgumentException("${field.label} is required."))
                continue
            }
            if (field.key.startsWith(AdminField.PATH_PREFIX)) pathValues[field.path.single()] = raw
            else query[field.path.single()] = raw
        }
        val bodySchema = operation.requestBody?.takeIf { it.isJson }?.schema
        val body: JsonElement? = when {
            bodySchema == null -> null
            bodyIsRawJson -> {
                val raw = values[RAW_BODY_KEY]?.trim().orEmpty()
                if (raw.isEmpty()) {
                    if (operation.requestBody.required) return Result.failure(IllegalArgumentException("A request body is required."))
                    null
                } else {
                    runCatching { Json.parseToJsonElement(raw) }.getOrElse {
                        return Result.failure(IllegalArgumentException("The request body isn't valid JSON."))
                    }
                }
            }
            else -> buildBody(bodySchema, emptyList(), values).getOrElse { return Result.failure(it) }
                ?: JsonObject(emptyMap())
        }
        return Result.success(AdminRequestValues(pathValues, query, body))
    }

    private fun buildBody(schema: AdminSchema, path: List<String>, values: Map<String, String>): Result<JsonObject?> {
        var any = false
        val obj = buildJsonObject {
            for ((name, prop) in schema.properties) {
                if (prop.readOnly) continue
                val childPath = path + name
                val key = childPath.joinToString(".")
                val required = name in schema.required
                if (prop.isObject && prop.properties.isNotEmpty() && depthOk(childPath)) {
                    val nested = buildBody(prop, childPath, values).getOrElse { return Result.failure(it) }
                    if (nested != null) { put(name, nested); any = true }
                    else if (required && path.isEmpty()) { put(name, JsonObject(emptyMap())); any = true }
                    continue
                }
                val field = fields.firstOrNull { it.key == key } ?: continue
                val raw = values[key]
                val element = parse(field, raw).getOrElse { return Result.failure(it) }
                if (element == null) {
                    if (required && field.kind != AdminFieldKind.Toggle && !field.nullable && path.isEmpty()) {
                        return Result.failure(IllegalArgumentException("${field.label} is required."))
                    }
                    continue
                }
                put(name, element); any = true
            }
        }
        return Result.success(if (any || path.isEmpty()) obj else null)
    }

    private fun parse(field: AdminField, raw: String?): Result<JsonElement?> {
        val text = raw?.trim().orEmpty()
        return when (field.kind) {
            AdminFieldKind.Toggle -> Result.success(
                when (text.lowercase()) { "true" -> JsonPrimitive(true); "false" -> JsonPrimitive(false); else -> null },
            )
            AdminFieldKind.Integer -> if (text.isEmpty()) Result.success(null) else
                text.toLongOrNull()?.let { Result.success(JsonPrimitive(it)) }
                    ?: Result.failure(IllegalArgumentException("${field.label} must be a whole number."))
            AdminFieldKind.Decimal -> if (text.isEmpty()) Result.success(null) else
                text.toDoubleOrNull()?.let { Result.success(JsonPrimitive(it)) }
                    ?: Result.failure(IllegalArgumentException("${field.label} must be a number."))
            AdminFieldKind.Lines -> if (text.isEmpty()) Result.success(null) else
                Result.success(JsonArray(text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { lineValue(field, it) }))
            AdminFieldKind.JsonText -> if (text.isEmpty()) Result.success(null) else
                runCatching { Json.parseToJsonElement(text) }.fold(
                    { Result.success(it) },
                    { Result.failure(IllegalArgumentException("${field.label} isn't valid JSON.")) },
                )
            AdminFieldKind.Group -> Result.success(null)
            else -> if (raw == null || (text.isEmpty() && field.kind !is AdminFieldKind.Text)) Result.success(null)
                else if (text.isEmpty()) Result.success(null) else Result.success(JsonPrimitive(raw))
        }
    }

    private fun lineValue(field: AdminField, line: String): JsonElement {
        val itemSchema = operation.requestBody?.schema?.let { schemaAt(it, field.path) }?.items
        return when {
            itemSchema?.isInteger == true -> line.toLongOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(line)
            itemSchema?.isNumber == true -> line.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(line)
            itemSchema?.isBoolean == true -> line.toBooleanStrictOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(line)
            else -> JsonPrimitive(line)
        }
    }

    private fun paramField(prefix: String, p: AdminParam, required: Boolean): AdminField {
        val schema = p.schema
        return AdminField(
            key = prefix + p.name,
            path = listOf(p.name),
            label = humanizeKey(p.name),
            help = p.description ?: schema?.description,
            kind = schema?.let(::kindFor) ?: AdminFieldKind.Text,
            required = required,
            nullable = !required,
            depth = 0,
        )
    }

    private fun addSchemaFields(out: MutableList<AdminField>, schema: AdminSchema, path: List<String>, depth: Int, parentRequired: Boolean) {
        for ((name, prop) in schema.properties) {
            if (prop.readOnly) continue
            val childPath = path + name
            val required = parentRequired && name in schema.required
            if (prop.isObject && prop.properties.isNotEmpty() && depthOk(childPath)) {
                out += AdminField(childPath.joinToString("."), childPath, humanizeKey(name), prop.description,
                    AdminFieldKind.Group, required, prop.nullable, depth)
                addSchemaFields(out, prop, childPath, depth + 1, required)
                continue
            }
            out += AdminField(
                key = childPath.joinToString("."),
                path = childPath,
                label = humanizeKey(name),
                help = prop.description,
                kind = kindFor(prop),
                required = required,
                nullable = prop.nullable || !required,
                depth = depth,
            )
        }
    }

    private fun depthOk(path: List<String>) = path.size <= MAX_NESTING

    companion object {
        const val RAW_BODY_KEY = "@body"
        private const val MAX_NESTING = 3
        private val PRETTY = Json { prettyPrint = true }

        fun kindFor(schema: AdminSchema): AdminFieldKind = when {
            schema.enumValues.isNotEmpty() -> AdminFieldKind.Choice(schema.enumValues)
            schema.isBoolean -> AdminFieldKind.Toggle
            schema.isInteger -> AdminFieldKind.Integer
            schema.isNumber -> AdminFieldKind.Decimal
            schema.isString && (schema.writeOnly || schema.format == "password") -> AdminFieldKind.Secret
            schema.isString -> AdminFieldKind.Text
            schema.isArray && schema.items?.let { it.isString || it.isInteger || it.isNumber } == true -> AdminFieldKind.Lines
            else -> AdminFieldKind.JsonText
        }

        internal fun schemaAt(root: AdminSchema, path: List<String>): AdminSchema? {
            var node: AdminSchema? = root
            for (segment in path) node = node?.property(segment)
            return node
        }

        internal fun valueAt(root: JsonElement, path: List<String>): JsonElement? {
            var node: JsonElement? = root
            for (segment in path) node = (node as? JsonObject)?.get(segment)
            return node
        }

        internal fun toText(value: JsonElement, kind: AdminFieldKind): String = when {
            value is JsonPrimitive && kind == AdminFieldKind.Toggle -> (value.booleanOrNull ?: false).toString()
            value is JsonPrimitive -> value.contentOrNull.orEmpty()
            value is JsonArray && kind == AdminFieldKind.Lines ->
                value.joinToString("\n") { (it as? JsonPrimitive)?.contentOrNull ?: it.toString() }
            else -> PRETTY.encodeToString(JsonElement.serializer(), value)
        }
    }
}
