package org.siloserver.silo.tv.ui.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class TvSettingsCategoryTest {
    @Test
    fun remoteShortcutsSitsBeforeDiagnosticsAndServer() {
        assertEquals(
            listOf(
                TvSettingsCategory.General,
                TvSettingsCategory.Playback,
                TvSettingsCategory.Subtitles,
                TvSettingsCategory.RemoteShortcuts,
                TvSettingsCategory.Diagnostics,
                TvSettingsCategory.Server,
            ),
            TvSettingsCategory.entries,
        )
    }

    @Test
    fun anIneligibleProfileHidesTheCategoryEntirely() {
        assertEquals(
            listOf(
                TvSettingsCategory.General,
                TvSettingsCategory.Playback,
                TvSettingsCategory.Subtitles,
                TvSettingsCategory.RemoteShortcuts,
                TvSettingsCategory.Server,
            ),
            tvSettingsVisibleCategories(diagnosticsEligible = false),
        )
    }

    @Test
    fun losingEligibilityWhileShownFallsBackToGeneral() {
        assertEquals(
            TvSettingsCategory.General,
            tvSettingsCategoryForEligibility(
                current = TvSettingsCategory.Diagnostics,
                diagnosticsEligible = false,
            ),
        )
    }

    @Test
    fun anUnrelatedCategoryIsNeverDisturbed() {
        assertEquals(
            TvSettingsCategory.Server,
            tvSettingsCategoryForEligibility(
                current = TvSettingsCategory.Server,
                diagnosticsEligible = false,
            ),
        )
        assertEquals(
            TvSettingsCategory.Diagnostics,
            tvSettingsCategoryForEligibility(
                current = TvSettingsCategory.Diagnostics,
                diagnosticsEligible = true,
            ),
        )
    }
}
