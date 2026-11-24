package org.siloserver.silo.updates

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class InstallMethod { STANDARD, SHIZUKU }

enum class UpdateCheckInterval(val hours: Int) {
    OFF(0), EVERY_3_HOURS(3), EVERY_6_HOURS(6), EVERY_12_HOURS(12), EVERY_24_HOURS(24), CUSTOM(24);

    val enabled: Boolean get() = this != OFF

    companion object {
        internal val DEFAULT = EVERY_12_HOURS

        internal fun fromSavedHours(hours: Int): UpdateCheckInterval =
            entries.firstOrNull { it != CUSTOM && it.hours == hours } ?: DEFAULT
    }
}

private const val DEFAULT_CHECK_HOUR = 3

data class UpdateSettings(
    val interval: UpdateCheckInterval = UpdateCheckInterval.DEFAULT,
    val automatic: Boolean = false,
    val method: InstallMethod = InstallMethod.STANDARD,
    val checkHour: Int = DEFAULT_CHECK_HOUR,
) {
    internal val dailyCheckHour: Int? get() = checkHour.takeIf { interval == UpdateCheckInterval.CUSTOM }

    internal fun normalized(): UpdateSettings =
        if (!interval.enabled && automatic) copy(automatic = false) else this
}

enum class UpdateStatus {
    IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLING,
    WAITING_FOR_PLAYBACK, NEEDS_CONFIRMATION, NEEDS_INSTALL_PERMISSION, INSTALLED, ERROR,
}

data class UpdateState(
    val settings: UpdateSettings,
    val currentVersion: String,
    val release: SiloraRelease? = null,
    val checkedAt: Long = 0,
    val status: UpdateStatus = UpdateStatus.IDLE,
    val detail: String? = null,
) {
    val busy: Boolean
        get() = status in listOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING)
}

internal data class InstallAttempt(
    val id: String,
    val version: Long,
    val background: Boolean,
    val startedAt: Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("code", version)
        put("background", background)
        put("time", startedAt)
    }.toString()

    companion object {
        fun fromJson(text: String): InstallAttempt = JSONObject(text).let {
            InstallAttempt(it.getString("id"), it.getLong("code"), it.getBoolean("background"), it.getLong("time"))
        }
    }
}

internal class UpdateStore(context: Context) {
    private val prefs = context.getSharedPreferences("silora-updates", Context.MODE_PRIVATE)

    fun settings() = UpdateSettings(
        interval = if (prefs.getBoolean("custom-interval", false)) UpdateCheckInterval.CUSTOM
            else UpdateCheckInterval.fromSavedHours(prefs.getInt("interval", UpdateCheckInterval.DEFAULT.hours)),
        automatic = prefs.getBoolean("automatic", false),
        method = if (prefs.getBoolean("shizuku", false)) InstallMethod.SHIZUKU else InstallMethod.STANDARD,
        checkHour = prefs.getInt("check-hour", DEFAULT_CHECK_HOUR).coerceIn(0, 23),
    ).normalized()

    fun save(settings: UpdateSettings) {
        prefs.edit()
            .putInt("interval", settings.interval.hours)
            .putBoolean("custom-interval", settings.interval == UpdateCheckInterval.CUSTOM)
            .putInt("check-hour", settings.checkHour)
            .putBoolean("automatic", settings.automatic)
            .putBoolean("shizuku", settings.method == InstallMethod.SHIZUKU)
            .apply()
    }

    fun release(): SiloraRelease? = prefs.getString("release", null)?.let {
        runCatching { SiloraRelease.fromJson(it) }.getOrNull()
    }

    fun checkedAt(): Long = prefs.getLong("checked", 0)

    fun recordCheck(release: SiloraRelease?, time: Long) {
        prefs.edit().putString("release", release?.toJson()).putLong("checked", time).apply()
    }

    fun attempt(): InstallAttempt? = prefs.getString("attempt", null)?.let {
        runCatching { InstallAttempt.fromJson(it) }.getOrNull()
    }

    fun begin(attempt: InstallAttempt) {
        check(prefs.edit().putString("attempt", attempt.toJson()).commit()) { "Could not save the installation attempt." }
    }

    fun finish(confirmationVersion: Long = 0, installedVersion: Long = 0) {
        prefs.edit().remove("attempt").putLong("confirmation", confirmationVersion)
            .putLong("installed", installedVersion).commit()
    }

    fun confirmationVersion(): Long = prefs.getLong("confirmation", 0)
    fun recordedInstall(): Long = prefs.getLong("installed", 0)
    fun laterVersion(): Long = prefs.getLong("later-version", 0)
    fun laterUntil(): Long = prefs.getLong("later-until", 0)

    fun later(version: Long, until: Long) {
        prefs.edit().putLong("later-version", version).putLong("later-until", until).apply()
    }
}

/** A launcher return clears launch suppression; a refresh interval can clear it in place. */
internal class UpdateOfferPolicy {
    private var dismissedVersion: Long? = null
    private var dismissedAt = 0L

    fun launch() {
        dismissedVersion = null
    }

    fun later(version: Long, now: Long) {
        dismissedVersion = version
        dismissedAt = now
    }

    fun visible(version: Long, interval: Int, now: Long): Boolean =
        version != dismissedVersion || interval > 0 && now - dismissedAt >= TimeUnit.HOURS.toMillis(interval.toLong())
}
