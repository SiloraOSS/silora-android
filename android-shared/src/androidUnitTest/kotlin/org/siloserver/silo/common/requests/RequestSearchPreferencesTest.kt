package org.siloserver.silo.common.requests

import android.app.Application
import android.content.Context
import android.os.Looper
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
import org.siloserver.silo.common.store.AccountLocalData
import org.siloserver.silo.common.store.AccountScopedKeys
import org.siloserver.silo.model.server.AccountEntry
import org.siloserver.silo.model.server.ServerEntry
import org.siloserver.silo.network.ServerRegistry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RequestSearchPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences(RequestSearchPreferences.PREFS_NAME, Context.MODE_PRIVATE)
    private val accountA = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
    private val accountB = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
    private val profileA = ServerEntry("server-a", "https://a.example", accountId = accountA, profileId = "profile-a")
    private val registry = TestRegistry(profileA)

    @Before
    fun clearPreferences() {
        prefs.edit().clear().commit()
    }

    @Test
    fun newInstallsAndUnvisitedProfilesDefaultToRequestsFirst() {
        val store = RequestSearchPreferences(prefs, registry)
        assertTrue(store.isRequestsFirst())
        registry.activeEntry.value = profileA.copy(profileId = "profile-b")
        assertTrue(store.isRequestsFirst())
    }

    @Test
    fun upgradeEnablesEverySavedOffValueBeforeAnyProfileIsSelected() {
        val scopes = listOf(
            profileA,
            profileA.copy(profileId = "profile-b"),
            profileA.copy(accountId = accountB),
            profileA.copy(id = "server-b", url = "https://b.example"),
        )
        prefs.edit().apply {
            scopes.forEach { putBoolean(savedKey(it), false) }
            putBoolean(savedKey(profileA.copy(profileId = "already-on")), true)
            putBoolean("unrelated", false)
            putString("unrelated_text", "keep")
        }.commit()
        registry.activeEntry.value = null

        RequestSearchPreferences(prefs, registry)

        scopes.forEach { assertTrue(prefs.getBoolean(savedKey(it), false), savedKey(it)) }
        assertTrue(prefs.getBoolean(savedKey(profileA.copy(profileId = "already-on")), false))
        assertFalse(prefs.getBoolean("unrelated", true))
        assertEquals("keep", prefs.getString("unrelated_text", null))
    }

    @Test
    fun optingOutAfterTheMigrationSurvivesReopeningTheStore() {
        prefs.edit().putBoolean(savedKey(profileA), false).commit()
        val store = RequestSearchPreferences(prefs, registry)
        assertTrue(store.isRequestsFirst())

        store.setRequestsFirst(false)

        assertFalse(store.isRequestsFirst())
        assertFalse(RequestSearchPreferences(prefs, registry).isRequestsFirst())
    }

    @Test
    fun laterOptOutStaysScopedToItsServerAccountAndProfile() {
        val store = RequestSearchPreferences(prefs, registry)
        store.setRequestsFirst(false)

        for (other in listOf(
            profileA.copy(profileId = "profile-b"),
            profileA.copy(accountId = accountB),
            profileA.copy(id = "server-b", url = "https://b.example"),
        )) {
            registry.activeEntry.value = other
            assertTrue(store.isRequestsFirst())
        }

        registry.activeEntry.value = profileA
        assertFalse(store.isRequestsFirst())
    }

    @Test
    fun observersStartWithTheMigratedValueAndFollowChoicesAndProfileChanges() = runTest {
        prefs.edit().putBoolean(savedKey(profileA), false).commit()
        val store = RequestSearchPreferences(prefs, registry)
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.requestsFirst.collect { observed += it }
        }
        runCurrent()
        assertEquals(listOf(true), observed)

        store.setRequestsFirst(false)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(listOf(true, false), observed)

        registry.activeEntry.value = profileA.copy(profileId = "profile-b")
        runCurrent()
        assertEquals(listOf(true, false, true), observed)
    }

    @Test
    fun missingIdentityUsesTheNewDefaultWithoutSavingAnUnscopedChoice() {
        registry.activeEntry.value = null
        val store = RequestSearchPreferences(prefs, registry)
        val before = prefs.all

        store.setRequestsFirst(false)

        assertTrue(store.isRequestsFirst())
        assertEquals(before, prefs.all)
        registry.activeEntry.value = profileA.copy(accountId = null)
        store.setRequestsFirst(false)
        assertTrue(store.isRequestsFirst())
        assertEquals(before, prefs.all)
        registry.activeEntry.value = profileA.copy(profileId = null)
        store.setRequestsFirst(false)
        assertTrue(store.isRequestsFirst())
        assertEquals(before, prefs.all)
    }

    @Test
    fun signingOutRemovesTheAccountChoiceWithoutResettingOtherAccountsAgain() {
        val otherAccount = profileA.copy(accountId = accountB)
        prefs.edit()
            .putBoolean(savedKey(profileA), false)
            .putBoolean(savedKey(otherAccount), false)
            .commit()
        val store = RequestSearchPreferences(prefs, registry)
        store.setRequestsFirst(false)
        registry.activeEntry.value = otherAccount
        store.setRequestsFirst(false)
        registry.accounts.value = listOf(AccountEntry(accountB, profileA.id))

        AccountLocalData(context, registry).reconcile()

        assertFalse(prefs.contains(savedKey(profileA)))
        assertFalse(RequestSearchPreferences(prefs, registry).isRequestsFirst())
        registry.activeEntry.value = profileA
        assertTrue(store.isRequestsFirst())
    }

    private fun savedKey(entry: ServerEntry): String =
        AccountScopedKeys.identity(entry.id, entry.accountId, requireNotNull(entry.profileId)) + ".requests_first"

    private class TestRegistry(initial: ServerEntry) : ServerRegistry {
        override val entries = MutableStateFlow(listOf(initial))
        override val activeServerId = MutableStateFlow<String?>(initial.id)
        override val activeEntry = MutableStateFlow<ServerEntry?>(initial)
        override val accounts = MutableStateFlow(emptyList<AccountEntry>())
        override suspend fun addOrUpdate(url: String, fetchedName: String?): String = error("not used")
        override suspend fun rename(serverId: String, userOverrideName: String?) = Unit
        override suspend fun setFetchedName(serverId: String, fetchedName: String?) = Unit
        override suspend fun setProfileId(serverId: String, profileId: String?) = Unit
        override suspend fun remove(serverId: String) = Unit
        override suspend fun signOut(serverId: String) = Unit
        override suspend fun switchTo(serverId: String) = Unit
        override suspend fun touchActive() = Unit
    }
}
