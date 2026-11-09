package org.siloserver.silo.common.admin

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.siloserver.silo.common.store.AccountScopedKeys
import org.siloserver.silo.network.ServerRegistry

/** Device-local profile-menu visibility, scoped to the server, account and profile. */
class AdminMenuPreferences(
    private val prefs: SharedPreferences,
    private val registry: ServerRegistry,
) {
    val visible: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(isVisible())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val profileChanges = launch {
            registry.activeEntry.collect { trySend(isVisible()) }
        }
        awaitClose {
            profileChanges.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    fun isVisible(): Boolean = key()?.let { prefs.getBoolean(it, false) } ?: false

    fun setVisible(visible: Boolean) {
        val key = key() ?: return
        prefs.edit().putBoolean(key, visible).apply()
    }

    private fun key(): String? {
        val server = registry.activeEntry.value ?: return null
        val profileId = server.profileId?.takeIf(String::isNotBlank) ?: return null
        val accountId = server.accountId?.takeIf(String::isNotBlank) ?: return null
        return AccountScopedKeys.identity(server.id, accountId, profileId) + ".show_admin"
    }

    companion object {
        const val PREFS_NAME = "silo_admin_menu"
    }
}
