package org.siloserver.silo.tv.ui.screens.search

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardMode
import org.siloserver.silo.tv.data.preferences.TvSearchKeyboardStore
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TvSearchKeyboardStoreTest {
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(TvSearchKeyboardStore.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun clearPreferences() { prefs.edit(commit = true) { clear() } }

    @Test fun appKeyboardIsTheDefaultForExistingAndNewInstalls() {
        assertEquals(TvSearchKeyboardMode.App, TvSearchKeyboardStore(prefs).mode.value)
    }

    @Test fun choosingSystemKeyboardUpdatesTheFlowAndSurvivesReload() {
        val store = TvSearchKeyboardStore(prefs)
        store.setMode(TvSearchKeyboardMode.System)
        assertEquals(TvSearchKeyboardMode.System, store.mode.value)
        assertEquals(TvSearchKeyboardMode.System, TvSearchKeyboardStore(prefs).mode.value)
        store.setMode(TvSearchKeyboardMode.App)
        assertEquals(TvSearchKeyboardMode.App, TvSearchKeyboardStore(prefs).mode.value)
    }

    @Test fun unknownStoredModesFallBackToAppKeyboard() {
        prefs.edit(commit = true) { putString("mode", "future-mode") }
        assertEquals(TvSearchKeyboardMode.App, TvSearchKeyboardStore(prefs).mode.value)
    }
}
