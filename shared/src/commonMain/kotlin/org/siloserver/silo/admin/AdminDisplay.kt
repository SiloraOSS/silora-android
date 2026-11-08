package org.siloserver.silo.admin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A page of items from a list response, and the cursor for the next page. */
data class AdminPage(val items: List<JsonElement>, val nextCursor: String?)

/** One labelled value in a record view. Nested values carry the raw JSON. */
data class AdminRow(
    val key: String,
    val label: String,
    val text: String,
    val nested: JsonElement? = null,
)

/**
 * Fork: turns admin responses into what the console shows — a title and
 * subtitle per item, readable rows per record, pages of list items.
 */
object AdminDisplay {
    private val pretty = Json { prettyPrint = true }

    private val TITLE_FIELDS = listOf(
        "title", "name", "display_name", "username", "label", "summary", "media_title", "key", "email",
        "message", "path", "url", "id",
    )
    private val SUBTITLE_FIELDS = listOf(
        "status", "state", "role", "kind", "type", "media_type", "level", "user", "username", "profile_name",
        "device_name", "client", "library_name", "created_at", "updated_at", "last_run_at", "next_run_at", "timestamp",
    )

    /** The list in [response]: `{items: [...], page: {next_cursor}}`, or a bare array. */
    fun page(response: JsonElement?): AdminPage? = when (response) {
        is JsonArray -> AdminPage(response.toList(), null)
        is JsonObject -> {
            val items = response["items"] as? JsonArray
            if (items == null) {
                // Some reads wrap their list under another name; take the only array.
                val arrays = response.entries.filter { it.value is JsonArray }
                if (arrays.size == 1 && response.size <= 3) AdminPage((arrays.single().value as JsonArray).toList(), null) else null
            } else {
                val page = response["page"] as? JsonObject
                val more = (page?.get("has_more") as? JsonPrimitive)?.booleanOrNull == true
                val cursor = (page?.get("next_cursor") as? JsonPrimitive)?.contentOrNull?.takeIf { more && it.isNotBlank() }
                AdminPage(items.toList(), cursor)
            }
        }
        else -> null
    }

    fun title(item: JsonElement): String {
        val obj = item as? JsonObject ?: return text(item)
        for (field in TITLE_FIELDS) {
            val value = (obj[field] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrBlank()) return value.take(120)
        }
        return obj.entries.firstOrNull { it.value is JsonPrimitive }?.let { "${humanizeKey(it.key)}: ${text(it.value)}" } ?: "Item"
    }

    fun subtitle(item: JsonElement): String? {
        val obj = item as? JsonObject ?: return null
        val title = title(item)
        val parts = SUBTITLE_FIELDS.mapNotNull { field ->
            val value = obj[field] as? JsonPrimitive ?: return@mapNotNull null
            val text = value.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (text == title) null else formatValue(field, text)
        }.distinct().take(3)
        return parts.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    /** Every field of [record] as a row; nested objects and lists summarized. */
    fun rows(record: JsonElement?): List<AdminRow> = when (record) {
        null, JsonNull -> emptyList()
        is JsonObject -> record.entries.map { (key, value) ->
            when (value) {
                is JsonObject -> AdminRow(key, humanizeKey(key), if (value.isEmpty()) "—" else "${value.size} fields", value)
                is JsonArray -> AdminRow(key, humanizeKey(key), when (value.size) { 0 -> "None"; 1 -> "1 item"; else -> "${value.size} items" }, value.takeIf { it.isNotEmpty() })
                else -> AdminRow(key, humanizeKey(key), formatValue(key, text(value)))
            }
        }
        is JsonArray -> record.mapIndexed { i, value -> AdminRow("$i", "Item ${i + 1}", if (value is JsonPrimitive) text(value) else title(value), value.takeUnless { it is JsonPrimitive }) }
        is JsonPrimitive -> listOf(AdminRow("value", "Value", text(record)))
    }

    fun text(value: JsonElement): String = when (value) {
        JsonNull -> "—"
        is JsonPrimitive -> when (value.booleanOrNull) {
            true -> if (value.isString) value.content else "Yes"
            false -> if (value.isString) value.content else "No"
            null -> value.contentOrNull?.ifBlank { "—" } ?: "—"
        }
        else -> pretty.encodeToString(JsonElement.serializer(), value)
    }

    fun prettyJson(value: JsonElement): String = pretty.encodeToString(JsonElement.serializer(), value)

    private val ISO_DATE_TIME = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:\d{2})?$""")

    /** Shortens ISO timestamps (`2026-11-08T14:03:22Z` → `2026-11-08 14:03 UTC`) and byte counts. */
    fun formatValue(key: String, text: String): String {
        ISO_DATE_TIME.matchEntire(text)?.let { m ->
            val zone = m.groupValues[5].let { if (it == "Z") " UTC" else if (it.isNotBlank()) " $it" else "" }
            return "${m.groupValues[1]} ${m.groupValues[2]}$zone"
        }
        if ((key.endsWith("_bytes") || key == "bytes" || key.endsWith("size")) && text.toLongOrNull() != null) {
            return humanBytes(text.toLong())
        }
        return text
    }

    fun humanBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        val rounded = kotlin.math.round(value * 10) / 10
        return "$rounded ${units[unit]}"
    }
}
