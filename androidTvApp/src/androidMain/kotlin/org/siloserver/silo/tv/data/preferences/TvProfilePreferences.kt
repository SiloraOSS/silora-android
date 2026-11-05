package org.siloserver.silo.tv.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.MessageDigest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.siloserver.silo.model.server.ServerEntry
import org.siloserver.silo.model.server.AccountEntry
import org.siloserver.silo.common.store.AccountScopedKeys

/** Local preferences partitioned by server/account/profile, with no server writes. */
internal class TvProfilePreferences(
    private val prefs: SharedPreferences,
    private val activeServer: StateFlow<ServerEntry?>,
    private val legacyKeys: Set<String>,
    private val accounts: StateFlow<List<AccountEntry>>? = null,
) {
    fun <T> read(default: T, value: (SharedPreferences, String) -> T): T = synchronized(prefs) {
        val prefix = currentPrefix() ?: return@synchronized default
        migrateLegacy(prefix)
        value(prefs, prefix)
    }

    fun edit(update: SharedPreferences.Editor.(String) -> Unit) = synchronized(prefs) {
        val prefix = currentPrefix() ?: return@synchronized
        migrateLegacy(prefix)
        prefs.edit { update(prefix) }
    }

    fun <T> observe(default: T, value: (SharedPreferences, String) -> T): Flow<T> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read(default, value))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val profileChanges = launch {
            activeServer.collect { trySend(read(default, value)) }
        }
        awaitClose {
            profileChanges.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    private fun currentPrefix(): String? {
        val server = activeServer.value ?: return null
        val profileId = server.profileId?.takeIf(String::isNotBlank) ?: return null
        val identity = "${server.id.length}:${server.id}$profileId"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val legacyPrefix = "profile_$hash."
        val accountId = server.accountId?.takeIf(String::isNotBlank) ?: return legacyPrefix
        val prefix = "${AccountScopedKeys.token(accountId)}.$legacyPrefix"
        if (adoptsLegacyData(accountId)) {
            prefs.edit {
                legacyKeys.forEach { key ->
                    val oldKey = legacyPrefix + key
                    if (!prefs.contains(prefix + key)) {
                        when (val value = prefs.all[oldKey]) {
                            is String -> putString(prefix + key, value)
                            is Boolean -> putBoolean(prefix + key, value)
                        }
                    }
                    remove(oldKey)
                }
            }
        }
        return prefix
    }

    private fun adoptsLegacyData(accountId: String): Boolean =
        accounts?.value?.any { it.localAccountId == accountId && it.adoptsLegacyData } == true

    /** Only the first active profile of the adopting account inherits TV-wide values. */
    private fun migrateLegacy(prefix: String) {
        if (prefs.getBoolean(MIGRATED, false)) return
        val accountId = activeServer.value?.accountId
        if (!accountId.isNullOrBlank() && !adoptsLegacyData(accountId)) return
        val legacy = prefs.all.filterKeys { it in legacyKeys }
        prefs.edit {
            legacy.forEach { (key, value) ->
                if (!prefs.contains(prefix + key)) {
                    when (value) {
                        is String -> putString(prefix + key, value)
                        is Boolean -> putBoolean(prefix + key, value)
                    }
                }
                remove(key)
            }
            putBoolean(MIGRATED, true)
        }
    }

    private companion object {
        const val MIGRATED = "profile_preferences_migrated"
    }
}
