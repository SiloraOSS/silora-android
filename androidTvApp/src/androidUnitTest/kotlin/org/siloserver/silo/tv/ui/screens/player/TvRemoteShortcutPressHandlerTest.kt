package org.siloserver.silo.tv.ui.screens.player

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.siloserver.silo.tv.data.preferences.TvRemoteButton
import org.siloserver.silo.tv.data.preferences.TvRemoteShortcutAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TvRemoteShortcutPressHandlerTest {
    private class Fixture(scope: CoroutineScope) {
        var eligible = true
        var mapping = TvRemoteShortcutAction.ToggleSleepTimer
        val taps = mutableListOf<Int>()
        val holds = mutableListOf<TvRemoteShortcutAction>()
        val handler = TvRemoteShortcutPressHandler(
            scope = scope,
            holdTimeoutMs = 500,
            isEligible = { eligible },
            actionFor = { mapping },
            onTap = taps::add,
            onHold = { holds.add(it) },
        )

        fun down(key: Int = KeyEvent.KEYCODE_DPAD_LEFT, time: Long = 0, repeats: Int = 0) =
            handler.dispatch(key, KeyEvent.ACTION_DOWN, repeats, time, time)

        fun up(key: Int = KeyEvent.KEYCODE_DPAD_LEFT, start: Long = 0, end: Long = 100, canceled: Boolean = false) =
            handler.dispatch(key, KeyEvent.ACTION_UP, 0, start, end, canceled)
    }

    @Test
    fun `short press runs the normal tap only on release`() = runTest {
        val f = Fixture(this)
        assertTrue(f.down())
        runCurrent()
        advanceTimeBy(200)
        assertTrue(f.taps.isEmpty())
        assertTrue(f.up(end = 200))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT), f.taps)
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `hold fires without repeat events and consumes repeats and release after opening a menu`() = runTest {
        val f = Fixture(this)
        f.down()
        runCurrent()
        advanceTimeBy(500)
        runCurrent()
        f.eligible = false // The action opened a focus-owning popup.
        assertTrue(f.down(repeats = 1))
        assertTrue(f.down(repeats = 2))
        assertTrue(f.up(end = 900))
        assertEquals(listOf(TvRemoteShortcutAction.ToggleSleepTimer), f.holds)
        assertTrue(f.taps.isEmpty())
    }

    @Test
    fun `event duration detects a hold when the timer has not run`() = runTest {
        val f = Fixture(this)
        f.down()
        assertTrue(f.up(end = 500))
        runCurrent()
        assertEquals(listOf(TvRemoteShortcutAction.ToggleSleepTimer), f.holds)
        assertTrue(f.taps.isEmpty())
    }

    @Test
    fun `default mapping and visible menus pass events through unchanged`() = runTest {
        val f = Fixture(this)
        f.mapping = TvRemoteShortcutAction.Default
        assertFalse(f.down())
        assertFalse(f.up())
        f.mapping = TvRemoteShortcutAction.ToggleSleepTimer
        f.eligible = false
        assertFalse(f.down())
        assertFalse(f.up())
        assertTrue(f.taps.isEmpty())
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `losing eligibility suppresses both the delayed hold and its tap`() = runTest {
        val f = Fixture(this)
        f.down()
        runCurrent()
        f.eligible = false
        advanceTimeBy(500)
        runCurrent()
        f.eligible = true
        assertTrue(f.up(end = 700))
        assertTrue(f.taps.isEmpty())
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `backgrounding cancels the pending action while keeping ownership of release`() = runTest {
        val f = Fixture(this)
        f.down()
        runCurrent()
        advanceTimeBy(100)
        f.handler.cancelPending()
        advanceTimeBy(1000)
        assertTrue(f.up(end = 1100))
        assertTrue(f.taps.isEmpty())
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `a canceled release never acts`() = runTest {
        val f = Fixture(this)
        f.down()
        assertTrue(f.up(end = 600, canceled = true))
        assertTrue(f.taps.isEmpty())
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `another button cancels the old gesture without leaking its release`() = runTest {
        val f = Fixture(this)
        f.down()
        runCurrent()
        advanceTimeBy(100)
        f.down(key = KeyEvent.KEYCODE_DPAD_RIGHT, time = 100)
        assertTrue(f.up())
        assertTrue(f.up(key = KeyEvent.KEYCODE_DPAD_RIGHT, start = 100, end = 200))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_RIGHT), f.taps)
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `a lost release does not swallow the next physical press`() = runTest {
        val f = Fixture(this)
        f.down()
        f.down(time = 1000)
        assertTrue(f.up(start = 1000, end = 1100))
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT), f.taps)
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `a release from a different physical press cannot fire an old hold`() = runTest {
        val f = Fixture(this)
        f.down()
        assertTrue(f.up(start = 1000, end = 1100))
        assertTrue(f.taps.isEmpty())
        assertTrue(f.holds.isEmpty())
    }

    @Test
    fun `no action consumes a hold but still permits a normal tap`() = runTest {
        val f = Fixture(this)
        f.mapping = TvRemoteShortcutAction.None
        f.down()
        f.up(end = 600)
        f.down(time = 1000)
        f.up(start = 1000, end = 1100)
        assertEquals(listOf(TvRemoteShortcutAction.None), f.holds)
        assertEquals(listOf(KeyEvent.KEYCODE_DPAD_LEFT), f.taps)
    }

    @Test
    fun `only the five remote buttons and select aliases can be assigned`() {
        assertEquals(TvRemoteButton.Left, tvRemoteShortcutButton(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvRemoteButton.Right, tvRemoteShortcutButton(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TvRemoteButton.Up, tvRemoteShortcutButton(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TvRemoteButton.Down, tvRemoteShortcutButton(KeyEvent.KEYCODE_DPAD_DOWN))
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER).forEach {
            assertEquals(TvRemoteButton.Select, tvRemoteShortcutButton(it))
        }
        assertEquals(null, tvRemoteShortcutButton(KeyEvent.KEYCODE_BACK))
        assertEquals(null, tvRemoteShortcutButton(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
