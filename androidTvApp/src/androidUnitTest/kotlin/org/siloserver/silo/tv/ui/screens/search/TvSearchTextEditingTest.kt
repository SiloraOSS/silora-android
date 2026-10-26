package org.siloserver.silo.tv.ui.screens.search

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class TvSearchTextEditingTest {
    @Test fun insertAtCursorKeepsTheSuffix() {
        val result = insertTvSearchText(TextFieldValue("star wars", TextRange(4)), " trek")
        assertEquals("star trek wars", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test fun insertionReplacesAReversedSelection() {
        val result = insertTvSearchText(TextFieldValue("star wars", TextRange(9, 5)), "trek")
        assertEquals("star trek", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test fun backspaceDeletesSelectionBeforeConsideringTheCursor() {
        val result = deleteTvSearchText(TextFieldValue("star wars", TextRange(4, 9)))
        assertEquals("star", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test fun backspaceDeletesAWholeUnicodeCodePoint() {
        val result = deleteTvSearchText(TextFieldValue("a😀b", TextRange(3)))
        assertEquals("ab", result.text)
        assertEquals(TextRange(1), result.selection)
    }

    @Test fun backspaceAtTheBeginningDoesNothing() {
        val value = TextFieldValue("abc", TextRange.Zero)
        assertEquals(value, deleteTvSearchText(value))
    }

    @Test fun insertionAtTheLimitDoesNotDropAnExistingSuffix() {
        val value = TextFieldValue("a".repeat(200), TextRange(100))
        assertEquals(value, insertTvSearchText(value, "b"))
        val replaced = insertTvSearchText(value.copy(selection = TextRange(100, 102)), "xyz")
        assertEquals("a".repeat(100) + "xy" + "a".repeat(98), replaced.text)
    }

    @Test fun limitDoesNotSplitASurrogatePair() {
        assertEquals("a".repeat(199), limitTvSearchText("a".repeat(199) + "😀"))
        assertEquals("a".repeat(199), insertTvSearchText(TextFieldValue("a".repeat(199), TextRange(199)), "😀").text)
    }

    @Test fun pastedInputIsLimitedAndItsSelectionClamped() {
        val result = limitTvSearchInput(TextFieldValue("a".repeat(220), TextRange(220)))
        assertEquals(200, result.text.length)
        assertEquals(TextRange(200), result.selection)
    }
}
