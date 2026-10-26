package org.siloserver.silo.tv.ui.screens.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Never cut a surrogate pair at the shared search limit. */
internal fun limitTvSearchText(value: String): String {
    var end = minOf(value.length, TV_SEARCH_QUERY_MAX_LENGTH)
    if (end > 0 && end < value.length && value[end - 1].isHighSurrogate() && value[end].isLowSurrogate()) end--
    return value.take(end)
}

internal fun limitTvSearchInput(value: TextFieldValue): TextFieldValue {
    val text = limitTvSearchText(value.text)
    return value.copy(
        text = text,
        selection = TextRange(value.selection.start.coerceIn(0, text.length), value.selection.end.coerceIn(0, text.length)),
    )
}

internal fun insertTvSearchText(value: TextFieldValue, inserted: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val room = (TV_SEARCH_QUERY_MAX_LENGTH - value.text.length + end - start).coerceAtLeast(0)
    var count = minOf(inserted.length, room)
    if (count > 0 && count < inserted.length && inserted[count - 1].isHighSurrogate() && inserted[count].isLowSurrogate()) count--
    val text = value.text.replaceRange(start, end, inserted.take(count))
    return TextFieldValue(text, TextRange(start + count))
}

internal fun deleteTvSearchText(value: TextFieldValue): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    if (start != end) return TextFieldValue(value.text.removeRange(start, end), TextRange(start))
    if (start == 0) return value
    val previous = Character.offsetByCodePoints(value.text, start, -1)
    return TextFieldValue(value.text.removeRange(previous, start), TextRange(previous))
}
