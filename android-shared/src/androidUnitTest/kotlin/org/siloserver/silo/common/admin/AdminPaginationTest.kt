package org.siloserver.silo.common.admin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.TokenManagerImpl
import org.siloserver.silo.network.apiv2.AdminV2Api
import org.siloserver.silo.network.apiv2.ApiV2Gate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AdminPaginationTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private suspend fun service(client: HttpClient): AdminService {
        val loader = AdminSpecLoader(ApplicationProvider.getApplicationContext<Context>())
        loader.catalog()
        val tokens = object : TokenManager by TokenManagerImpl() {
            override suspend fun snapshotCurrentScope() = AuthScopeSnapshot("s", "primary", "https://example.invalid", "proof")
        }
        return AdminService(loader, AdminV2Api(client, tokens, ApiV2Gate.Unrestricted))
    }

    @Test
    fun changingRequestViewCancelsItsPendingPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val releasePage = CompletableDeferred<Unit>()
        var pageCancelled = false
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (request.url.parameters["cursor"] != null) {
                    try { releasePage.await() } finally { pageCancelled = true }
                    respond(requestPage("old-page", false), headers = json)
                } else if (request.url.encodedPath.endsWith("/requests")) {
                    val view = request.url.parameters["view"]!!
                    respond(requestPage(view, view == "needs_approval"), headers = json)
                } else respond("{}", headers = json)
            }
        }))
        try {
            val vm = AdminRequestsViewModel(service(client))
            advanceUntilIdle()
            vm.loadMore()
            runCurrent()
            assertTrue(vm.state.value.loadingMore)
            vm.select(AdminRequestView.Failed)
            advanceUntilIdle()
            releasePage.complete(Unit)
            advanceUntilIdle()
            assertTrue(pageCancelled)
            assertFalse(vm.state.value.loadingMore)
            assertEquals(listOf("failed"), vm.state.value.items.map { it.id })
        } finally {
            releasePage.complete(Unit)
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun changingLogStreamCancelsItsPendingPage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val releasePage = CompletableDeferred<Unit>()
        var pageCancelled = false
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                if (request.url.parameters["cursor"] != null) {
                    try { releasePage.await() } finally { pageCancelled = true }
                    respond(logPage("old-page", false), headers = json)
                } else respond(logPage("first", request.url.encodedPath.endsWith("/app")), headers = json)
            }
        }))
        try {
            val vm = AdminLogsViewModel(service(client))
            advanceUntilIdle()
            vm.loadMore()
            runCurrent()
            vm.setStream(AdminLogStream.Audit)
            advanceUntilIdle()
            releasePage.complete(Unit)
            advanceUntilIdle()
            assertTrue(pageCancelled)
            assertFalse(vm.state.value.loadingMore)
            assertEquals(1, vm.state.value.app.size)
            assertEquals(1, vm.state.value.audit.size)
        } finally {
            releasePage.complete(Unit)
            client.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedPagesKeepTheCursorForRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                when {
                    request.url.parameters["cursor"] != null -> respond("{}", HttpStatusCode.ServiceUnavailable, json)
                    request.url.encodedPath.endsWith("/requests") -> respond(requestPage("first", true), headers = json)
                    request.url.encodedPath.endsWith("/app") -> respond(logPage("first", true), headers = json)
                    else -> respond("{}", headers = json)
            }
            }
        }))
        try {
            val service = service(client)
            val requests = AdminRequestsViewModel(service)
            val logs = AdminLogsViewModel(service)
            advanceUntilIdle()
            requests.loadMore()
            logs.loadMore()
            advanceUntilIdle()
            assertEquals("next", requests.state.value.nextCursor)
            assertEquals("next", logs.state.value.nextCursor)
            assertTrue(requests.state.value.notice?.isError == true)
            assertTrue(logs.state.value.error != null)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    private fun requestPage(id: String, more: Boolean) = """{"items":[{"id":"$id","media_type":"movie","tmdb_id":1,"title":"Example","status":"pending","outcome":"active","created_at":"2026-11-08T00:00:00Z","updated_at":"2026-11-08T00:00:00Z"}],"page":{"has_more":$more,"next_cursor":"next"}}"""

    private fun logPage(id: String, more: Boolean) = """{"items":[{"id":"$id"}],"page":{"has_more":$more,"next_cursor":"next"}}"""
}
