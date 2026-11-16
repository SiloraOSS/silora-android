package org.siloserver.silo.viewmodel

import org.siloserver.silo.model.catalog.isEpisodeItemType
import org.siloserver.silo.model.section.PLAYBACK_SECTION_TYPES
import org.siloserver.silo.model.section.SectionItem

/**
 * Which Home list a card's dismissal goes to, and so what its menu calls it.
 *
 * The server keys the two lists differently. An in-progress card is dismissed
 * on `continue_watching` against its exact `progress_updated_at`, so resuming
 * playback brings it back. A Next Up episode has no progress row; it is
 * dismissed on `next_up`, keyed by its series. Progress wins when a card has
 * both, as on Apple.
 */
class HomeDismissal private constructor(val surface: String, val anchor: String, val menuLabel: String) {
    companion object {
        /** Home rows whose cards can be removed. */
        val sectionTypes = PLAYBACK_SECTION_TYPES

        /** Null when neither list can represent the card, which then has no remove action. */
        fun of(item: SectionItem): HomeDismissal? = of(item.progressUpdatedAt, item.seriesId)

        /**
         * Fork: the same rule for a card on a progress row's full page
         * ("Explore all"), which lists [BrowseItem]s.
         */
        fun of(item: org.siloserver.silo.model.catalog.BrowseItem): HomeDismissal? =
            of(item.progressUpdatedAt, item.seriesId)

        private fun of(progressUpdatedAt: String?, seriesId: String?): HomeDismissal? {
            progressUpdatedAt?.takeIf { it.isNotBlank() }?.let {
                return HomeDismissal("continue_watching", it, "Remove from Continue Watching")
            }
            seriesId?.takeIf { it.isNotBlank() }?.let {
                return HomeDismissal("next_up", it, "Remove from Next Up")
            }
            return null
        }

        /**
         * Fork: whether a Home row of [sectionType] is a progress row whose
         * cards can be removed (Continue Watching, In Progress, Next Up).
         */
        fun isProgressSectionType(sectionType: String?): Boolean {
            val type = sectionType?.lowercase() ?: return false
            return type.contains("continue") || type.contains("in_progress") ||
                type.contains("next_up") || type.contains("up_next")
        }
    }
}

/**
 * The show the server drops when this card is dismissed from either list: an
 * episode's series, or the series itself. Movies and books go one card at a
 * time, including an audiobook that belongs to a book series.
 */
internal fun SectionItem.droppedSeriesId(): String? = when {
    type.trim().equals("series", ignoreCase = true) -> contentId
    isEpisodeItemType(type) -> seriesId?.takeIf { it.isNotBlank() }
    else -> null
}

/** Fork: the show the server drops when this full-page card is dismissed (see [droppedSeriesId]). */
internal fun org.siloserver.silo.model.catalog.BrowseItem.droppedSeriesId(): String? = when {
    type.trim().equals("series", ignoreCase = true) -> contentId
    isEpisodeItemType(type) -> seriesId?.takeIf { it.isNotBlank() }
    else -> null
}

/**
 * Fork: tells Home that a card was removed somewhere else (a progress row's
 * full page), so Home drops it at once and re-reads its rows. One stream per
 * process; Home ignores a removal made for another profile.
 */
object HomeProgressDismissals {
    data class Removed(val owner: org.siloserver.silo.network.AuthScopeSnapshot, val contentId: String, val droppedSeriesId: String?)

    private val _removed = kotlinx.coroutines.flow.MutableSharedFlow<Removed>(extraBufferCapacity = 16)
    val removed: kotlinx.coroutines.flow.SharedFlow<Removed> = _removed

    fun publish(removed: Removed) {
        _removed.tryEmit(removed)
    }
}
