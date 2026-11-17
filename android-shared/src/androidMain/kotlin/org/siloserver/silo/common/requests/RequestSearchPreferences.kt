package org.siloserver.silo.common.requests

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.siloserver.silo.common.store.AccountScopedKeys
import org.siloserver.silo.network.ServerRegistry

/** Search section order on this device, scoped to the server, account and profile. */
class RequestSearchPreferences(
    private val prefs: SharedPreferences,
    private val registry: ServerRegistry,
) {
    init {
        enableRequestsFirstByDefaultOnce()
    }

    val requestsFirst: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(isRequestsFirst())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val profileChanges = launch {
            registry.activeEntry.collect { trySend(isRequestsFirst()) }
        }
        awaitClose {
            profileChanges.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    fun isRequestsFirst(): Boolean = key()?.let { prefs.getBoolean(it, true) } ?: true

    fun setRequestsFirst(value: Boolean) {
        val key = key() ?: return
        prefs.edit().putBoolean(key, value).apply()
    }

    private fun enableRequestsFirstByDefaultOnce() {
        if (prefs.getBoolean(ENABLED_DEFAULT_MIGRATION, false)) return
        // Include inactive accounts/profiles. Persist the reset and its marker
        // together so a later opt-out survives reopening the app or updating it.
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if (key.endsWith(REQUESTS_FIRST_SUFFIX) && value == false) {
                editor.putBoolean(key, true)
            }
        }
        editor.putBoolean(ENABLED_DEFAULT_MIGRATION, true).apply()
    }

    private fun key(): String? {
        val server = registry.activeEntry.value ?: return null
        val profileId = server.profileId?.takeIf(String::isNotBlank) ?: return null
        val accountId = server.accountId?.takeIf(String::isNotBlank) ?: return null
        return AccountScopedKeys.identity(server.id, accountId, profileId) + REQUESTS_FIRST_SUFFIX
    }

    companion object {
        const val PREFS_NAME = "silo_request_search"
        private const val REQUESTS_FIRST_SUFFIX = ".requests_first"
        private const val ENABLED_DEFAULT_MIGRATION = "requests_first_default_enabled_v1"
    }
}
