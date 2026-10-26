package org.siloserver.silo.tv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.theme.ElevatedSurface

/** A dock rather than a platform input window: the search results resize above it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TvSearchKeyboard(
    firstKeyFocusRequester: FocusRequester,
    fieldFocusRequester: FocusRequester,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
) {
    var uppercase by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val rows = (if (symbols) {
        listOf("1234567890".map(Char::toString), listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"),
            listOf("/", ".", ",", "'", "\"", ":", ";", "?", "!", "Delete"))
    } else {
        listOf("qwertyuiop".map(Char::toString), "asdfghjkl".map(Char::toString),
            listOf("Shift") + "zxcvbnm".map(Char::toString) + "Delete")
    }) + listOf(listOf("Mode", "Clear", "Space", "Search"))
    val requesters = remember { List(4) { row -> List(if (row == 3) 4 else 10) { FocusRequester() } } }
    var firstKeyFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        requestFocusUntilObserved(
            maxAttempts = TvContentInitialFocusMaxAttempts,
            awaitAttempt = { withFrameNanos { } },
            requestFocus = firstKeyFocusRequester::requestFocus,
            isFocused = { firstKeyFocused },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().background(ElevatedSurface).padding(horizontal = 32.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        rows.forEachIndexed { rowIndex, keys ->
            Row(
                modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                keys.forEachIndexed { column, key ->
                    fun requester(row: Int, col: Int): FocusRequester =
                        if (row == 0 && col == 0) firstKeyFocusRequester else requesters[row][col]
                    val own = requester(rowIndex, column)
                    val label = when (key) {
                        "Mode" -> if (symbols) "ABC" else "123 / #+="
                        "Shift" -> if (uppercase) "Shift ↑" else "Shift"
                        "Delete" -> "⌫"
                        else -> if (uppercase && !symbols && key.length == 1) key.uppercase() else key
                    }
                    Button(
                        onClick = {
                            when (key) {
                                "Shift" -> uppercase = !uppercase
                                "Mode" -> symbols = !symbols
                                "Delete" -> onBackspace()
                                "Clear" -> onClear()
                                "Space" -> onInsert(" ")
                                "Search" -> onSearch()
                                else -> onInsert(label)
                            }
                        },
                        modifier = Modifier.weight(if (key == "Space") 2f else 1f).height(34.dp)
                            .semantics { if (key == "Delete") contentDescription = "Backspace" }
                            .focusRequester(own)
                            .onFocusChanged { if (rowIndex == 0 && column == 0) firstKeyFocused = it.isFocused }
                            .focusProperties {
                                left = if (column == 0) FocusRequester.Cancel else requester(rowIndex, column - 1)
                                right = if (column == keys.lastIndex) FocusRequester.Cancel else requester(rowIndex, column + 1)
                                up = if (rowIndex == 0) fieldFocusRequester else requester(rowIndex - 1, column.coerceAtMost(rows[rowIndex - 1].lastIndex))
                                down = if (rowIndex == rows.lastIndex) FocusRequester.Cancel else requester(rowIndex + 1, column.coerceAtMost(rows[rowIndex + 1].lastIndex))
                            },
                        shape = ButtonDefaults.shape(RoundedCornerShape(8.dp)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        scale = ButtonDefaults.scale(focusedScale = 1.04f),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
