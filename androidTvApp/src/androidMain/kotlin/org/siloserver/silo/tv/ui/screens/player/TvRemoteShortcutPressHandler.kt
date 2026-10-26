package org.siloserver.silo.tv.ui.screens.player

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.siloserver.silo.tv.data.preferences.TvRemoteButton
import org.siloserver.silo.tv.data.preferences.TvRemoteShortcutAction

internal fun tvRemoteShortcutButton(keyCode: Int): TvRemoteButton? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> TvRemoteButton.Left
    KeyEvent.KEYCODE_DPAD_RIGHT -> TvRemoteButton.Right
    KeyEvent.KEYCODE_DPAD_UP -> TvRemoteButton.Up
    KeyEvent.KEYCODE_DPAD_DOWN -> TvRemoteButton.Down
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TvRemoteButton.Select
    else -> null
}

/**
 * Owns an assigned press from Down through Up. A timer fires even on remotes
 * without repeats; event timestamps cover releases delivered before the timer
 * gets CPU time. The normal tap is deferred until release, never run before a hold.
 */
internal class TvRemoteShortcutPressHandler(
    private val scope: CoroutineScope,
    private val holdTimeoutMs: Long,
    private val isEligible: () -> Boolean,
    private val actionFor: (TvRemoteButton) -> TvRemoteShortcutAction,
    private val onTap: (Int) -> Unit,
    private val onHold: (TvRemoteShortcutAction) -> Unit,
) {
    private class Press(val downTime: Long, val action: TvRemoteShortcutAction) {
        var job: Job? = null
        var fired = false
        var canceled = false
    }

    private val presses = mutableMapOf<Int, Press>()

    fun dispatch(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
        downTime: Long,
        eventTime: Long,
        canceled: Boolean = false,
    ): Boolean {
        val owned = presses[keyCode]
        if (owned != null) {
            // A new physical press replaces one whose release was lost.
            if (action == KeyEvent.ACTION_DOWN && repeatCount == 0 && downTime != owned.downTime) {
                owned.job?.cancel()
                presses.remove(keyCode)
            } else {
                if (canceled || downTime != owned.downTime || !isEligible()) owned.canceled = true
                if (action == KeyEvent.ACTION_UP) {
                    presses.remove(keyCode)
                    owned.job?.cancel()
                    if (!owned.canceled && !owned.fired) {
                        if (eventTime - owned.downTime >= holdTimeoutMs) fire(owned) else onTap(keyCode)
                    }
                }
                // Keep repeats and the release after an action opens a menu.
                return true
            }
        }
        if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) return false
        // Another button ends a pending gesture without firing a delayed action.
        cancelPending()
        if (canceled || !isEligible()) return false
        val button = tvRemoteShortcutButton(keyCode) ?: return false
        val mapping = actionFor(button)
        if (mapping == TvRemoteShortcutAction.Default) return false
        val press = Press(downTime, mapping)
        presses[keyCode] = press
        press.job = scope.launch {
            delay(holdTimeoutMs)
            if (!isEligible()) press.canceled = true
            fire(press)
        }
        return true
    }

    private fun fire(press: Press) {
        if (press.canceled || press.fired) return
        press.fired = true
        onHold(press.action)
    }

    /** Retain ownership of any owed Up event, but never act after losing focus. */
    fun cancelPending() {
        presses.values.forEach {
            it.canceled = true
            it.job?.cancel()
        }
    }
}
