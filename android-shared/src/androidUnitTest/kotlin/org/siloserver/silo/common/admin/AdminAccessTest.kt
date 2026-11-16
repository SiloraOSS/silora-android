package org.siloserver.silo.common.admin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.siloserver.silo.model.profile.Profile
import org.siloserver.silo.network.ApiResult
import org.siloserver.silo.network.AuthScopeSnapshot
import org.siloserver.silo.network.SiloJson
import org.siloserver.silo.network.TokenManager
import org.siloserver.silo.network.TokenManagerImpl
import org.siloserver.silo.network.api.AuthApi
import org.siloserver.silo.network.api.ProfileApi
import org.siloserver.silo.network.apiv2.ApiV2Gate
import org.siloserver.silo.repository.AuthRepository
import org.siloserver.silo.repository.ProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminAccessTest {
    private class Fixture(engineDispatcher: CoroutineDispatcher) : AutoCloseable {
        var scope = AuthScopeSnapshot("server", "profile", "https://example.invalid", "proof", accountId = "account")
        var role = "admin"
        var primary = true
        var profileAvailable = true
        var fail = false
        var reads = 0
        var duringRead: () -> Unit = {}
        private val tokens = object : TokenManager by TokenManagerImpl() {
            override suspend fun snapshotCurrentScope() = scope
            override suspend fun getProfileId() = scope.profileId
        }
        private val client = HttpClient(MockEngine(MockEngineConfig().apply {
            dispatcher = engineDispatcher
            addHandler {
                reads++
                duringRead()
                val headers = headersOf(HttpHeaders.ContentType, "application/json")
                if (fail) respond("{}", HttpStatusCode.ServiceUnavailable, headers)
                else respond("""{"id":"user","username":"example","email":"test@example.invalid","role":"$role"}""", headers = headers)
            }
        })) {
            install(ContentNegotiation) { json(SiloJson) }
        }
        private val profiles = object : ProfileRepository(ProfileApi(client, ApiV2Gate.Unrestricted), tokens) {
            override suspend fun listProfiles(): ApiResult<List<Profile>> = ApiResult.Success(
                if (profileAvailable) listOf(Profile(scope.profileId.orEmpty(), "Example", isPrimary = primary)) else emptyList(),
            )
        }
        val access = AdminAccess(AuthRepository(AuthApi(client, ApiV2Gate.Unrestricted), tokens), profiles, tokens)
        override fun close() = client.close()
    }

    @Test
    fun repeatedChecksUseTheSameIdentityAnswer() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            assertEquals(AdminAvailability.Available, f.access.check())
            assertEquals(AdminAvailability.Available, f.access.check())
            assertEquals(1, f.reads)
        }
    }

    @Test
    fun matchingProfileIdsOnDifferentServersDoNotShareAccess() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            assertEquals(AdminAvailability.Available, f.access.check())
            f.scope = f.scope.copy(serverId = "other", serverUrl = "https://other.invalid")
            f.role = "user"
            assertEquals(AdminAvailability.NotAdmin, f.access.check())
            assertEquals(2, f.reads)
        }
    }

    @Test
    fun matchingProfileIdsOnDifferentAccountsDoNotShareAccess() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            assertEquals(AdminAvailability.Available, f.access.check())
            f.scope = f.scope.copy(accountId = "other")
            f.role = "user"
            assertEquals(AdminAvailability.NotAdmin, f.access.check())
        }
    }

    @Test
    fun switchingProfilesRechecksPrimaryProfileAccess() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            assertEquals(AdminAvailability.Available, f.access.check())
            f.scope = f.scope.copy(profileId = "secondary")
            f.primary = false
            assertEquals(AdminAvailability.NeedsPrimaryProfile, f.access.check())
        }
    }

    @Test
    fun aNewLoginWithTheSameIdsDoesNotReuseTheOldAnswer() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            assertEquals(AdminAvailability.Available, f.access.check())
            f.scope = f.scope.copy(identityGeneration = 1, credentialEpoch = 1)
            f.role = "user"
            assertEquals(AdminAvailability.NotAdmin, f.access.check())
        }
    }

    @Test
    fun anUnavailableProfileIsUnknownAndCanBeRetried() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            f.profileAvailable = false
            assertEquals(AdminAvailability.Unknown, f.access.check())
            f.profileAvailable = true
            assertEquals(AdminAvailability.Available, f.access.check())
            assertEquals(2, f.reads)
        }
    }

    @Test
    fun aReplyForAnIdentityThatChangedDuringTheReadIsDiscarded() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            f.duringRead = { f.scope = f.scope.copy(accountId = "other") }
            assertEquals(AdminAvailability.Unknown, f.access.check())
            f.duringRead = {}
            f.role = "user"
            assertEquals(AdminAvailability.NotAdmin, f.access.check())
        }
    }

    @Test
    fun aTransientFailureRetriesAndPublishesTheAnswer() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            f.fail = true
            val answers = mutableListOf<AdminAvailability>()
            val job = launch { f.access.checkUntilKnown(answers::add) }
            runCurrent()
            assertEquals(listOf(AdminAvailability.Unknown), answers)
            f.fail = false
            advanceTimeBy(2_000)
            runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(listOf(AdminAvailability.Unknown, AdminAvailability.Available), answers)
        }
    }

    @Test
    fun cancellationStopsRetryingAndPublishing() = runTest {
        Fixture(StandardTestDispatcher(testScheduler)).use { f ->
            f.fail = true
            val answers = mutableListOf<AdminAvailability>()
            val job = launch { f.access.checkUntilKnown(answers::add) }
            runCurrent()
            job.cancelAndJoin()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(1, f.reads)
            assertEquals(listOf(AdminAvailability.Unknown), answers)
        }
    }
}
