package org.siloserver.silo.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.siloserver.silo.model.catalog.BrowseItem

/** Fork: a Home progress row's full page offers Remove, as Home's row does. */
class SectionBrowseHomeRemovalTest {
    private fun item(progress: String? = null, series: String? = null, type: String = "episode") =
        BrowseItem(contentId = "c1", type = type, title = "T", progressUpdatedAt = progress, seriesId = series)

    @Test
    fun onlyHomeProgressRowsAllowRemoval() {
        assertTrue(SectionBrowseArgs("s", "Continue Watching", sectionType = "continue_watching").isHomeProgressRow)
        assertTrue(SectionBrowseArgs("s", "Next Up", sectionType = "next_up").isHomeProgressRow)
        assertTrue(SectionBrowseArgs("s", "In progress", sectionType = "in_progress").isHomeProgressRow)
        assertFalse(SectionBrowseArgs("s", "Recently Added", sectionType = "recently_added").isHomeProgressRow)
        assertFalse(SectionBrowseArgs("s", "Continue Watching", libraryId = 3, sectionType = "continue_watching").isHomeProgressRow)
        assertFalse(SectionBrowseArgs("s", "Unknown").isHomeProgressRow)
    }

    @Test
    fun progressWinsOverNextUp() {
        assertEquals("continue_watching", HomeDismissal.of(item(progress = "2026-11-12T10:00:00Z", series = "s1"))?.surface)
        assertEquals("next_up", HomeDismissal.of(item(series = "s1"))?.surface)
        assertNull(HomeDismissal.of(item(type = "movie")))
    }
}
