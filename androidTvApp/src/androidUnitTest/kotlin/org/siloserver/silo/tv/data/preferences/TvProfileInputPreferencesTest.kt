package org.siloserver.silo.tv.data.preferences

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.siloserver.silo.model.server.ServerEntry
import org.siloserver.silo.model.server.AccountEntry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TvProfileInputPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val keyboardPrefs get() = context.getSharedPreferences(TvSearchKeyboardStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val shortcutPrefs get() = context.getSharedPreferences(TvRemoteShortcutsStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val profileA = ServerEntry("server-a", "https://a.example", profileId = "profile-a")
    private val profileB = profileA.copy(profileId = "profile-b")
    private val activeServer = MutableStateFlow<ServerEntry?>(profileA)
    private val keyboard get() = TvSearchKeyboardStore(keyboardPrefs, activeServer)
    private val shortcuts get() = TvRemoteShortcutsStore(shortcutPrefs, activeServer)

    @Before fun clearPreferences() {
        keyboardPrefs.edit(commit = true) { clear() }
        shortcutPrefs.edit(commit = true) { clear() }
    }

    @Test fun matchingProfileIdsOnDifferentAccountsKeepChoicesAndObserversSeparate() = runTest {
        val accountA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val accountB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val accounts = MutableStateFlow(listOf(
            AccountEntry(accountA, profileA.id), AccountEntry(accountB, profileA.id),
        ))
        activeServer.value = profileA.copy(accountId = accountA)
        val keyboard = TvSearchKeyboardStore(keyboardPrefs, activeServer, accounts)
        val shortcuts = TvRemoteShortcutsStore(shortcutPrefs, activeServer, accounts)
        val modes = mutableListOf<TvSearchKeyboardMode>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { keyboard.mode.collect { modes += it } }
        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.ToggleNightMode)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(TvSearchKeyboardMode.App, modes.last())

        activeServer.value = profileA.copy(accountId = accountB)
        runCurrent()
        assertEquals(TvSearchKeyboardMode.System, modes.last())
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.None)
        activeServer.value = profileA.copy(accountId = accountA)
        runCurrent()
        assertEquals(TvSearchKeyboardMode.App, modes.last())
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.ToggleNightMode, shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun onlyTheAdoptingAccountInheritsExistingProfileChoices() {
        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.PlayerStats)
        val accountA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        val accountB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        val accounts = MutableStateFlow(listOf(
            AccountEntry(accountA, profileA.id, adoptsLegacyData = true),
            AccountEntry(accountB, profileA.id),
        ))
        val keyboard = TvSearchKeyboardStore(keyboardPrefs, activeServer, accounts)
        val shortcuts = TvRemoteShortcutsStore(shortcutPrefs, activeServer, accounts)
        activeServer.value = profileA.copy(accountId = accountB)
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))
        keyboard.setMode(TvSearchKeyboardMode.System)
        activeServer.value = profileA.copy(accountId = accountA)
        assertEquals(TvSearchKeyboardMode.App, keyboard.currentMode)
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))
        keyboard.setMode(TvSearchKeyboardMode.System)
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        activeServer.value = profileA.copy(accountId = accountB)
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        shortcuts.reset()
        activeServer.value = profileA.copy(accountId = accountA)
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun profilesKeepIndependentChoicesAfterSwitchingAndReloading() {
        val keyboard = keyboard
        val shortcuts = shortcuts
        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.PlayerStats)

        activeServer.value = profileB
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))
        keyboard.setMode(TvSearchKeyboardMode.System)
        keyboard.setAutoOpenKeyboard(false)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.None)

        activeServer.value = profileA
        assertEquals(TvSearchKeyboardMode.App, keyboard.currentMode)
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))
        assertEquals(TvSearchKeyboardMode.App, this.keyboard.currentMode)
        assertTrue(this.keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.PlayerStats, this.shortcuts.actionFor(TvRemoteButton.Select))

        activeServer.value = profileB
        assertEquals(TvSearchKeyboardMode.System, this.keyboard.currentMode)
        assertFalse(this.keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.None, this.shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun matchingProfileIdsOnDifferentServersDoNotShareChoices() {
        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Left, TvRemoteShortcutAction.ToggleNightMode)

        activeServer.value = profileA.copy(id = "server-b", url = "https://b.example")
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Left))
        shortcuts.setAction(TvRemoteButton.Left, TvRemoteShortcutAction.None)

        activeServer.value = profileA
        assertEquals(TvSearchKeyboardMode.App, keyboard.currentMode)
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.ToggleNightMode, shortcuts.actionFor(TvRemoteButton.Left))
    }

    @Test fun resettingShortcutsOnlyResetsTheActiveProfile() {
        val shortcuts = shortcuts
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.PlayerStats)
        activeServer.value = profileB
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.ToggleNightMode)
        shortcuts.reset()
        assertTrue(shortcuts.currentMappings.values.all { it == TvRemoteShortcutAction.Default })

        activeServer.value = profileA
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun noActiveProfileReturnsDefaultsAndIgnoresEdits() {
        val keyboard = keyboard
        val shortcuts = shortcuts
        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.PlayerStats)

        for (entry in listOf(null, profileA.copy(profileId = null), profileA.copy(profileId = ""))) {
            activeServer.value = entry
            assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
            assertFalse(keyboard.currentAutoOpenKeyboard)
            assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))
            keyboard.setMode(TvSearchKeyboardMode.System)
            keyboard.setAutoOpenKeyboard(false)
            shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.None)
            shortcuts.reset()
        }

        activeServer.value = profileA
        assertEquals(TvSearchKeyboardMode.App, keyboard.currentMode)
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun oldTvWideChoicesMigrateOnceToTheFirstActiveProfile() {
        keyboardPrefs.edit(commit = true) {
            putString("mode", "app")
            putBoolean("auto_open_keyboard", true)
        }
        shortcutPrefs.edit(commit = true) { putString("select", "player_stats") }
        activeServer.value = null
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))

        activeServer.value = profileA
        assertEquals(TvSearchKeyboardMode.App, keyboard.currentMode)
        assertTrue(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.PlayerStats, shortcuts.actionFor(TvRemoteButton.Select))

        activeServer.value = profileB
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))

        activeServer.value = profileA
        keyboard.setMode(TvSearchKeyboardMode.System)
        keyboard.setAutoOpenKeyboard(false)
        shortcuts.reset()
        assertEquals(TvSearchKeyboardMode.System, keyboard.currentMode)
        assertFalse(keyboard.currentAutoOpenKeyboard)
        assertEquals(TvRemoteShortcutAction.Default, shortcuts.actionFor(TvRemoteButton.Select))
    }

    @Test fun existingCollectorsFollowEditsProfileSwitchesAndSignOut() = runTest {
        val keyboard = keyboard
        val shortcuts = shortcuts
        val modes = mutableListOf<TvSearchKeyboardMode>()
        val automaticOpening = mutableListOf<Boolean>()
        val mappings = mutableListOf<TvRemoteShortcutAction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { keyboard.mode.collect { modes += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            keyboard.autoOpenKeyboard.collect { automaticOpening += it }
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            shortcuts.mappings.collect { mappings += it.getValue(TvRemoteButton.Select) }
        }
        runCurrent()

        keyboard.setMode(TvSearchKeyboardMode.App)
        keyboard.setAutoOpenKeyboard(true)
        shortcuts.setAction(TvRemoteButton.Select, TvRemoteShortcutAction.PlayerStats)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(TvSearchKeyboardMode.App, modes.last())
        assertTrue(automaticOpening.last())
        assertEquals(TvRemoteShortcutAction.PlayerStats, mappings.last())

        activeServer.value = profileB
        runCurrent()
        assertEquals(TvSearchKeyboardMode.System, modes.last())
        assertFalse(automaticOpening.last())
        assertEquals(TvRemoteShortcutAction.Default, mappings.last())

        activeServer.value = profileA
        runCurrent()
        assertEquals(TvSearchKeyboardMode.App, modes.last())
        assertTrue(automaticOpening.last())
        assertEquals(TvRemoteShortcutAction.PlayerStats, mappings.last())

        activeServer.value = null
        runCurrent()
        assertEquals(TvSearchKeyboardMode.System, modes.last())
        assertFalse(automaticOpening.last())
        assertEquals(TvRemoteShortcutAction.Default, mappings.last())
    }
}
