package org.siloserver.silo.tv.ui.screens.search

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardMode
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardStore
import org.siloserver.silo.model.server.ServerEntry
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TvSearchKeyboardStoreTest {
    private val activeServer = MutableStateFlow<ServerEntry?>(
        ServerEntry("server", "https://silo.example", profileId = "profile"),
    )
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(TvSearchKeyboardStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun store() = TvSearchKeyboardStore(prefs, activeServer)

    @Before fun clearPreferences() { prefs.edit(commit = true) { clear() } }

    @Test fun systemKeyboardIsTheDefaultForExistingAndNewInstalls() = runTest {
        assertEquals(TvSearchKeyboardMode.System, store().mode.first())
    }

    @Test fun choosingEitherKeyboardUpdatesTheFlowAndSurvivesReload() = runTest {
        val store = store()
        store.setMode(TvSearchKeyboardMode.System)
        assertEquals(TvSearchKeyboardMode.System, store.mode.first())
        assertEquals(TvSearchKeyboardMode.System, store().mode.first())
        store.setMode(TvSearchKeyboardMode.App)
        assertEquals(TvSearchKeyboardMode.App, store.mode.first())
        assertEquals(TvSearchKeyboardMode.App, store().mode.first())
    }

    @Test fun unknownStoredModesFallBackToSystemKeyboard() = runTest {
        prefs.edit(commit = true) { putString("mode", "future-mode") }
        assertEquals(TvSearchKeyboardMode.System, store().mode.first())
    }
}
