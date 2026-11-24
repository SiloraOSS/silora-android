package org.siloserver.silo.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.work.Operation
import androidx.work.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class InstallConfirmation internal constructor(val intent: Intent, internal val session: Int)

class SiloraUpdates private constructor(private val context: Context) {
    private val store = UpdateStore(context)
    private val initialCode = installedCode(context)
    private val source = SiloraReleaseSource(isTv = initialCode % 2 == 1L)
    private val shizuku = ShizukuManager(context)
    private val installer = UpdateInstaller(context, shizuku)
    private val playback = SiloraMediaSession(context)
    private val apk = File(context.cacheDir, "silora-update.apk")
    private val gate = Mutex()
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val offerPolicy = UpdateOfferPolicy()

    private val _state = MutableStateFlow(UpdateState(
        settings = store.settings(),
        currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
        release = store.release()?.takeIf { it.versionCode > initialCode },
        checkedAt = store.checkedAt(),
        status = when {
            store.recordedInstall() == initialCode -> UpdateStatus.INSTALLED
            store.release()?.versionCode == store.confirmationVersion() -> UpdateStatus.NEEDS_CONFIRMATION
            else -> UpdateStatus.IDLE
        },
    ))
    val state = _state.asStateFlow()
    val shizukuState = shizuku.state
    private val _confirmation = MutableStateFlow<InstallConfirmation?>(null)
    val confirmation = _confirmation.asStateFlow()

    fun launch() {
        offerPolicy.launch()
        shizuku.refresh()
        scope.launch {
            gate.withLock {
                reconcile()
                try {
                    restoreSchedule()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("SiloraUpdates", "Could not restore update scheduling.", error)
                }
                val hours = state.value.settings.interval.hours.coerceAtLeast(1)
                if (System.currentTimeMillis() - state.value.checkedAt >= TimeUnit.HOURS.toMillis(hours.toLong())) {
                    checkRelease(reportError = false)
                }
            }
            refreshStandaloneUpdaterNotice()
        }
    }

    fun checkNow() {
        scope.launch {
            if (!gate.tryLock()) return@launch
            try {
                reconcile()
                checkRelease(reportError = true)
            } finally {
                gate.unlock()
            }
        }
    }

    @Synchronized
    fun setSettings(settings: UpdateSettings) {
        require(settings.checkHour in 0..23)
        val old = state.value.settings
        val next = settings.normalized()
        if (old == next) return
        store.save(next)
        _state.update { it.copy(settings = next) }
        if (old.interval != next.interval || old.dailyCheckHour != next.dailyCheckHour) {
            UpdateSchedule.configure(context, next, restart = true)
        }
        if (old.method != next.method) shizuku.refresh()
    }

    fun canUseStandard(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun requestShizukuPermission(): String? = shizuku.requestPermission()

    fun later() {
        val current = state.value
        val release = current.release ?: return
        val now = System.currentTimeMillis()
        offerPolicy.later(release.versionCode, now)
        store.later(release.versionCode, now + TimeUnit.HOURS.toMillis(current.settings.interval.hours.toLong()))
    }

    suspend fun foregroundPrompt(showing: UpdatePrompt?): UpdatePrompt? {
        val current = state.value
        if (current.busy) return null

        val release = current.release
        val prompt = when {
            release != null && offerPolicy.visible(
                release.versionCode, current.settings.interval.hours, System.currentTimeMillis(),
            ) -> UpdatePrompt.UPDATE_AVAILABLE
            current.standaloneUpdaterNoticePending -> UpdatePrompt.STANDALONE_UPDATER
            else -> return null
        }
        return prompt.takeIf { prompt == showing || idleForPrompt() }
    }

    @Synchronized
    fun dismissStandaloneUpdaterNotice() {
        store.dismissStandaloneUpdaterNotice()
        _state.update { it.copy(standaloneUpdaterNoticePending = false) }
    }

    private fun refreshStandaloneUpdaterNotice() {
        if (store.standaloneUpdaterNoticeDismissed()) return
        val installed = try {
            context.packageManager.getPackageInfo(STANDALONE_UPDATER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        synchronized(this) {
            if (!store.standaloneUpdaterNoticeDismissed()) {
                _state.update { it.copy(standaloneUpdaterNoticePending = installed) }
            }
        }
    }

    private suspend fun idleForPrompt(): Boolean = playback.read() == SiloraPlaybackStatus.IDLE

    fun installNow() {
        scope.launch {
            if (!gate.tryLock()) return@launch
            try {
                reconcile()
                install(background = false)
            } finally {
                gate.unlock()
            }
        }
    }

    fun takeConfirmation(): InstallConfirmation? = _confirmation.getAndUpdate { null }

    fun unavailableConfirmation(confirmation: InstallConfirmation) {
        scope.launch {
            gate.withLock {
                installer.abandon(confirmation.session)
                val attempt = store.attempt() ?: return@withLock
                store.finish(confirmationVersion = attempt.version)
                status(UpdateStatus.NEEDS_CONFIRMATION)
            }
        }
    }

    internal suspend fun scheduledCheck(canRetry: Boolean): Boolean = gate.withLock {
        reconcile()
        if (!state.value.settings.interval.enabled) return@withLock true
        val checked = checkRelease(reportError = true)
        val retry = !checked && canRetry
        // Persist the next daily run before a self-update can stop this process.
        refreshDailySchedule(retry)?.await()
        if (checked && state.value.settings.automatic && state.value.settings.interval.enabled) {
            install(background = true)
        }
        !retry
    }

    internal suspend fun afterReplacement() = gate.withLock {
        reconcile()
        restoreSchedule()
    }

    internal suspend fun afterClockChange() {
        refreshDailySchedule()?.await()
    }

    private suspend fun restoreSchedule() {
        val hour = state.value.settings.dailyCheckHour
        val outdated = hour != null && UpdateSchedule.hasOutdatedDailyWork(context, hour)
        synchronized(this) {
            val settings = state.value.settings
            // Recover if the process stopped after saving the hour and before enqueuing it.
            UpdateSchedule.configure(context, settings, restart = outdated && hour == settings.dailyCheckHour)
        }.await()
    }

    @Synchronized
    private fun refreshDailySchedule(retry: Boolean = false): Operation? {
        val settings = state.value.settings
        val hour = settings.dailyCheckHour ?: return null
        val now = System.currentTimeMillis()
        val nextDay = nextDailyCheck(hour, now)
        // A WorkManager schedule override takes precedence over its normal retry backoff.
        val nextRun = if (retry) minOf(nextDay, now + TimeUnit.MINUTES.toMillis(15)) else nextDay
        return UpdateSchedule.configure(context, settings, nextDailyRunAt = nextRun)
    }

    private fun checkRelease(reportError: Boolean): Boolean {
        if (store.attempt() != null) return true
        status(UpdateStatus.CHECKING)
        return try {
            val latest = source.latest().takeIf { it.versionCode > installedCode(context) }
            val now = System.currentTimeMillis()
            store.recordCheck(latest, now)
            val result = when {
                latest == null -> UpdateStatus.UP_TO_DATE
                store.confirmationVersion() == latest.versionCode && state.value.settings.method == InstallMethod.STANDARD ->
                    UpdateStatus.NEEDS_CONFIRMATION
                else -> UpdateStatus.AVAILABLE
            }
            _state.update { it.copy(release = latest, checkedAt = now, status = result, detail = null) }
            true
        } catch (error: Exception) {
            if (reportError) status(UpdateStatus.ERROR, error.message)
            else status(if (state.value.release == null) UpdateStatus.IDLE else UpdateStatus.AVAILABLE)
            false
        }
    }

    private suspend fun install(background: Boolean) {
        val current = state.value
        val release = current.release ?: return
        if (store.attempt() != null || release.versionCode <= installedCode(context)) return
        if (background && (
                store.laterVersion() == release.versionCode && System.currentTimeMillis() < store.laterUntil() ||
                    current.settings.method == InstallMethod.STANDARD && store.confirmationVersion() == release.versionCode
            )
        ) return
        try {
            if (current.settings.method == InstallMethod.STANDARD && !canUseStandard()) {
                status(UpdateStatus.NEEDS_INSTALL_PERMISSION)
                return
            }
            if (current.settings.method == InstallMethod.SHIZUKU && !shizuku.awaitReady().ready) {
                status(UpdateStatus.ERROR, shizuku.state.value.help)
                return
            }
            if (background && !idleForPrompt()) {
                status(UpdateStatus.WAITING_FOR_PLAYBACK)
                return
            }
            status(UpdateStatus.DOWNLOADING)
            source.download(release, apk)
            installer.verify(apk, release)
            // Recheck after download. The viewer may have started playback or changed settings.
            if (background) {
                val settings = state.value.settings
                if (!settings.automatic || !settings.interval.enabled || settings.method != current.settings.method) {
                    status(UpdateStatus.AVAILABLE)
                    return
                }
                if (!idleForPrompt()) {
                    status(UpdateStatus.WAITING_FOR_PLAYBACK)
                    return
                }
            }

            val attempt = InstallAttempt(UUID.randomUUID().toString(), release.versionCode, background, System.currentTimeMillis())
            store.begin(attempt)
            status(UpdateStatus.INSTALLING)
            if (current.settings.method == InstallMethod.SHIZUKU) installer.shizuku(apk, attempt.id)
            else installer.standard(apk, attempt.id, background)
        } catch (error: CancellationException) {
            if (store.attempt() == null) status(UpdateStatus.AVAILABLE)
            throw error
        } catch (error: Exception) {
            if (!reconcile()) {
                store.finish()
                status(UpdateStatus.ERROR, error.message)
            }
        }
    }

    /** Handles a process killed between committing a self-update and receiving its result. */
    private fun reconcile(): Boolean {
        val attempt = store.attempt() ?: return false
        if (installedCode(context) >= attempt.version) {
            store.finish(installedVersion = installedCode(context))
            apk.delete()
            _state.update { it.copy(release = null, status = UpdateStatus.INSTALLED, detail = null) }
            Log.i("SiloraUpdates", "Recovered installed version ${attempt.version}")
            return true
        }
        if (System.currentTimeMillis() - attempt.startedAt >= TimeUnit.MINUTES.toMillis(15)) {
            context.packageManager.packageInstaller.mySessions
                .filter { it.appPackageName == context.packageName }.forEach { installer.abandon(it.sessionId) }
            store.finish()
            status(UpdateStatus.ERROR, "The installation did not finish. Choose Install update to try again.")
        } else {
            status(UpdateStatus.INSTALLING)
        }
        return false
    }

    internal suspend fun onInstallResult(intent: Intent, broadcastCode: Int) = gate.withLock {
        val id = intent.data?.lastPathSegment ?: return@withLock
        val attempt = store.attempt()?.takeIf { it.id == id } ?: return@withLock
        if (reconcile()) return@withLock
        val session = intent.getIntExtra(UpdateInstaller.EXTRA_SESSION, -1)
        val result = if (session < 0) broadcastCode
            else intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (result) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val prompt = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (!attempt.background && prompt != null) {
                    _confirmation.value = InstallConfirmation(prompt, session)
                } else {
                    installer.abandon(session)
                    store.finish(confirmationVersion = attempt.version)
                    status(UpdateStatus.NEEDS_CONFIRMATION)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // Android normally publishes the installed version before delivering success.
                store.finish(installedVersion = attempt.version)
                apk.delete()
                _state.update { it.copy(release = null, status = UpdateStatus.INSTALLED, detail = null) }
            }
            else -> {
                store.finish()
                status(UpdateStatus.ERROR, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Android did not install the update. Try again in Settings.")
            }
        }
    }

    private fun status(status: UpdateStatus, detail: String? = null) {
        _state.update { it.copy(status = status, detail = detail) }
    }

    companion object {
        private const val STANDALONE_UPDATER_PACKAGE = "org.silora.updater"
        private var instance: SiloraUpdates? = null

        @Synchronized
        fun get(context: Context): SiloraUpdates =
            instance ?: SiloraUpdates(context.applicationContext).also { instance = it }
    }
}
