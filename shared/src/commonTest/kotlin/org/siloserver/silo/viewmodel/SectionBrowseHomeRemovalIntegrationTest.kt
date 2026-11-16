package org.siloserver.silo.viewmodel

import androidx.lifecycle.ViewModelStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import org.siloserver.silo.domain.MediaActionsCoordinator
import org.siloserver.silo.model.catalog.BrowseItem
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.TokenManagerImpl
import org.siloserver.silo.network.api.PersonalDataApi
import org.siloserver.silo.network.api.SectionApi
import org.siloserver.silo.network.apiv2.ApiV2Gate
import org.siloserver.silo.network.apiv2.CatalogV2Api
import org.siloserver.silo.network.apiv2.HomeSectionsV2Api
import org.siloserver.silo.repository.PersonalDataRepository
import org.siloserver.silo.repository.SectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises real catalog/dismissal transports and the shared Home refresh. */
@OptIn(ExperimentalCoroutinesApi::class)
class SectionBrowseHomeRemovalIntegrationTest {
    @Test
    fun continueWatchingRemovalDropsTheShowAndRefreshesHomeOnce() = scenario { f ->
        val episode = f.page.uiState.value.items.first()
        val remaining = f.originalItems.filterNot { it.seriesId == "show" }.map { it.contentId }
        f.failHomeRefresh = true

        f.page.removeFromHome(episode)
        f.page.removeFromHome(episode)
        assertEquals(remaining, f.page.ids(), "remove the show before the server replies")
        runCurrent()
        assertEquals(listOf("/api/v2/home/dismissals/continue_watching/ep-1"), f.putPaths)
        assertEquals(listOf("""{"progress_updated_at":"$STAMP"}"""), f.putBodies)

        f.reply.complete(Unit)
        runCurrent()
        assertEquals(remaining, f.page.ids())
        assertEquals(remaining, f.home.ids(), "Home must keep the removal even if its refresh fails")
        assertEquals(2, f.homeReads)
    }

    @Test
    fun nextUpUsesTheSeriesAnchor() = scenario { f ->
        val next = f.page.uiState.value.items[1]
        f.page.removeFromHome(next)
        runCurrent()
        assertEquals(listOf("/api/v2/home/dismissals/next_up/ep-2"), f.putPaths)
        assertEquals(listOf("""{"series_id":"show"}"""), f.putBodies)
        f.reply.complete(Unit)
        runCurrent()
        assertEquals(false, f.page.uiState.value.items.any { it.seriesId == "show" })
    }

    @Test
    fun serverRefusalRestoresTheOriginalOrderAndDoesNotRefreshHome() = scenario { f ->
        f.dismissStatus = HttpStatusCode.Conflict
        f.page.removeFromHome(f.page.uiState.value.items.first())
        runCurrent()
        f.reply.complete(Unit)
        runCurrent()
        assertEquals(f.originalItems.map { it.contentId }, f.page.ids())
        assertEquals(f.originalItems.map { it.contentId }, f.home.ids())
        assertEquals(1, f.homeReads)
    }

    @Test
    fun aFailedOldRemovalCannotRestoreCardsIntoAReloadedPage() = scenario { f ->
        f.dismissStatus = HttpStatusCode.Conflict
        f.page.removeFromHome(f.page.uiState.value.items.first())
        runCurrent()
        f.catalogItems = listOf(BrowseItem(contentId = "new", type = "movie", title = "New"))
        f.page.retry()
        runCurrent()
        assertEquals(listOf("new"), f.page.ids())
        f.reply.complete(Unit)
        runCurrent()
        assertEquals(listOf("new"), f.page.ids())
    }

    @Test
    fun aProfileSwitchWhileRemovingCannotUpdateTheNewProfilesHome() = scenario { f ->
        f.page.removeFromHome(f.page.uiState.value.items.first())
        runCurrent()
        f.owner = f.owner.copy(profileId = "other", profileToken = "other-pin", identityGeneration = 2)
        f.home.refresh()
        runCurrent()
        assertEquals(2, f.homeReads)
        f.reply.complete(Unit)
        runCurrent()
        assertEquals(2, f.homeReads, "an old-profile reply must not refresh the new Home")
        assertEquals(f.originalItems.map { it.contentId }, f.home.ids())
    }

    @Test
    fun aStalePageCannotSendARemovalForAnotherProfile() = scenario { f ->
        f.owner = f.owner.copy(profileId = "other", profileToken = "other-pin", identityGeneration = 2)
        f.page.removeFromHome(f.page.uiState.value.items.first())
        runCurrent()
        assertEquals(emptyList(), f.putPaths)
        assertEquals(f.originalItems.map { it.contentId }, f.page.ids())
    }

    @Test
    fun anAudiobookRemovalLeavesOtherBooksInItsSeries() = scenario { f ->
        f.page.removeFromHome(f.page.uiState.value.items.single { it.contentId == "book-1" })
        runCurrent()
        f.reply.complete(Unit)
        runCurrent()
        assertEquals(f.originalItems.filterNot { it.contentId == "book-1" }.map { it.contentId }, f.page.ids())
    }

    private fun scenario(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = Fixture(dispatcher)
        try {
            runCurrent()
            assertEquals(fixture.originalItems.map { it.contentId }, fixture.page.ids())
            assertEquals(fixture.originalItems.map { it.contentId }, fixture.home.ids())
            block(fixture)
        } finally {
            fixture.close()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(dispatcher: CoroutineDispatcher) {
        var owner = AuthScopeSnapshot("server", "profile", "https://silo.test", "pin", identityGeneration = 1)
        private val tokens = object : TokenManager by TokenManagerImpl() {
            override suspend fun snapshotCurrentScope() = owner
        }
        val originalItems = listOf(
            BrowseItem(contentId = "ep-1", type = "episode", title = "Episode 1", seriesId = "show", progressUpdatedAt = STAMP),
            BrowseItem(contentId = "ep-2", type = "episode", title = "Episode 2", seriesId = "show"),
            BrowseItem(contentId = "movie", type = "movie", title = "Movie", progressUpdatedAt = STAMP),
            BrowseItem(contentId = "book-1", type = "audiobook", title = "Book 1", seriesId = "books", progressUpdatedAt = STAMP),
            BrowseItem(contentId = "book-2", type = "audiobook", title = "Book 2", seriesId = "books", progressUpdatedAt = STAMP),
        )
        var catalogItems = originalItems
        val reply = CompletableDeferred<Unit>()
        var dismissStatus = HttpStatusCode.NoContent
        var failHomeRefresh = false
        var homeReads = 0
        val putPaths = mutableListOf<String>()
        val putBodies = mutableListOf<String>()
        private val client = HttpClient(MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    request.method == HttpMethod.Put -> {
                        putPaths += request.url.encodedPath
                        putBodies += (request.body as TextContent).text
                        reply.await()
                        respond(if (dismissStatus == HttpStatusCode.NoContent) "" else "{}", dismissStatus, headers)
                    }
                    request.url.encodedPath == "/api/v2/catalog" -> respond(
                        """{"items":${SiloJson.encodeToString(catalogItems)},"page":{"has_more":false},"total":${catalogItems.size},"total_exact":true,"window_cursor":"window"}""",
                        headers = headers,
                    )
                    request.url.encodedPath == "/api/v2/home/sections" -> {
                        homeReads++
                        if (failHomeRefresh && homeReads > 1) {
                            respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                        } else {
                            respond("""{"sections":[{"id":"cw","section_type":"continue_watching","title":"Continue Watching","items":${SiloJson.encodeToString(originalItems)}}]}""", headers = headers)
                        }
                    }
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        })) { install(ContentNegotiation) { json(SiloJson) } }
        private val repository = SectionRepository(SectionApi(
            client,
            v2 = CatalogV2Api(client, ApiV2Gate.Unrestricted, tokens),
            home = HomeSectionsV2Api(client, tokens, ApiV2Gate.Unrestricted, decodeDispatcher = dispatcher),
        ))
        private val store = ViewModelStore()
        val page = SectionBrowseViewModel(repository, SectionBrowseArgs("cw", "Continue Watching", sectionType = "continue_watching"))
        val home = HomeViewModel(repository, MediaActionsCoordinator(PersonalDataRepository(PersonalDataApi(client))))

        init {
            store.put("page", page)
            store.put("home", home)
        }

        fun close() {
            store.clear()
            client.close()
        }
    }

    private fun SectionBrowseViewModel.ids() = uiState.value.items.map { it.contentId }
    private fun HomeViewModel.ids() = uiState.value.sections.single().items.map { it.contentId }

    private companion object {
        const val STAMP = "2026-11-15T20:00:00Z"
    }
}
