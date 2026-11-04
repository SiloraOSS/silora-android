package org.siloserver.silo.tv.ui.shell

/**
 * Identifies which top-level destination the menu bar selects/highlights:
 * `Home`, one tab per visible [TvLibraryTabType], For You, then `Calendar`,
 * and Requests while the server enables it. Search and the profile avatar
 * sit outside the tabs.
 */
sealed class TvRootDestination {
    /** Curated landing surface. Always the first tab. */
    data object Home : TvRootDestination()

    /** A library content-type tab (Movies / Series / Music / Audiobooks). */
    data class LibraryType(val type: TvLibraryTabType) : TvRootDestination()

    /** Personal recommendations ("For You") — tvOS `.recommendations`. */
    data object ForYou : TvRootDestination()

    /** Upcoming releases by week. */
    data object Calendar : TvRootDestination()

    /**
     * Media requests, tvOS `.requests`. Present only while the server has
     * requests enabled for this profile; it trails the content tabs.
     */
    data object Requests : TvRootDestination()
}
