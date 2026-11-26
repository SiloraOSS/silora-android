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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class InstallConfirmation internal constructor(
    val intent: Intent,
    internal val session: Int,
    internal val attemptId: String,
)

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
    private var recoveryJob: Job? = null

    fun launch() {
        offerPolicy.launch()
        shizuku.refresh()
        scope.launch {
            refreshWhatChanged()
            gate.withLock {
                reconcile()
                try {
                    restoreSchedule()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("SiloraUpdates", "Could not restore update scheduling.", error)
                }
                val current = state.value
                if (current.settings.interval.shouldCheckOnLaunch(current.checkedAt, System.currentTimeMillis())) {
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
        if (!next.showWhatChanged) store.markWhatChangedHandled(initialCode)
        _state.update { it.copy(settings = next, whatChanged = it.whatChanged.takeIf { next.showWhatChanged }) }
        if (old.interval != next.interval || old.dailyCheckHour != next.dailyCheckHour) {
            UpdateSchedule.configure(context, next, restart = true)
        }
        if (old.method != next.method) shizuku.refresh()
    }

    @Synchronized
    fun setNotifyOnAutomaticInstall(enabled: Boolean) {
        setSettings(state.value.settings.copy(notifyOnAutomaticInstall = enabled))
    }

    @Synchronized
    fun setShowWhatChanged(enabled: Boolean) {
        setSettings(state.value.settings.copy(showWhatChanged = enabled))
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
        if (!current.canShowForegroundPrompt) return null

        val release = current.release
        val prompt = when {
            current.whatChanged != null -> UpdatePrompt.WHAT_CHANGED
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

    @Synchronized
    fun dismissWhatChanged() {
        store.markWhatChangedHandled(initialCode)
        _state.update { it.copy(whatChanged = null) }
    }

    @Synchronized
    private fun refreshWhatChanged() {
        if (initialCode <= store.whatChangedHandledThrough() || state.value.whatChanged != null) return
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        if (!state.value.settings.showWhatChanged || installed.firstInstallTime == installed.lastUpdateTime) {
            store.markWhatChangedHandled(initialCode)
            return
        }
        val highlights = try {
            context.assets.open("silora-release-highlights.md").bufferedReader().useLines { lines ->
                lines.filter { it.startsWith("- ") }.joinToString("\n\n") { line ->
                    "• " + line.removePrefix("- ").removeSuffix(" *(Silora)*").removeSuffix(" *(Silo upstream)*")
                }
            }
        } catch (_: FileNotFoundException) {
            ""
        } catch (error: IOException) {
            Log.w("SiloraUpdates", "Could not read the bundled release highlights.", error)
            return
        }
        if (highlights.isBlank()) store.markWhatChangedHandled(initialCode)
        else _state.update { it.copy(whatChanged = highlights) }
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
                val attempt = store.attempt()?.takeIf { it.id == confirmation.attemptId } ?: return@withLock
                installer.abandon(confirmation.session)
                finishAttempt(confirmationVersion = attempt.version)
                status(UpdateStatus.NEEDS_CONFIRMATION)
            }
        }
    }

    internal suspend fun scheduledCheck(canRetry: Boolean): Boolean = gate.withLock {
        reconcile()
        if (!state.value.settings.interval.enabled) return@withLock true
        val checked = checkRelease(reportError = true)
        // Persist the next daily run before a self-update can stop this process.
        refreshDailySchedule()?.await()
        val settings = state.value.settings
        val retryable = !checked || settings.automatic && settings.interval.enabled && install(background = true)
        val latestSettings = state.value.settings
        val retry = canRetry && retryable && latestSettings.interval.enabled && (!checked || latestSettings.automatic)
        if (retry) refreshDailySchedule(retry = true)?.await()
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
        val nextRun = if (retry) minOf(nextDay, now + TimeUnit.MINUTES.toMillis(UPDATE_RETRY_MINUTES)) else nextDay
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

    private suspend fun backgroundInstallBlock(method: InstallMethod): UpdateStatus? {
        val idle = idleForPrompt()
        val settings = state.value.settings
        return when {
            !settings.automatic || !settings.interval.enabled || settings.method != method -> UpdateStatus.AVAILABLE
            method == InstallMethod.STANDARD && !canUseStandard() -> UpdateStatus.NEEDS_INSTALL_PERMISSION
            !idle -> UpdateStatus.WAITING_FOR_PLAYBACK
            else -> null
        }
    }

    /** Returns true if a scheduled worker should retry before its next regular run. */
    private suspend fun install(background: Boolean): Boolean {
        val current = state.value
        val release = current.release ?: return false
        if (store.attempt() != null || release.versionCode <= installedCode(context)) return false
        if (background && (
                store.laterVersion() == release.versionCode && System.currentTimeMillis() < store.laterUntil() ||
                    current.settings.method == InstallMethod.STANDARD && store.confirmationVersion() == release.versionCode
            )
        ) return false
        val allowedToCommit = AtomicBoolean(false)
        try {
            if (current.settings.method == InstallMethod.STANDARD && !canUseStandard()) {
                status(UpdateStatus.NEEDS_INSTALL_PERMISSION)
                return false
            }
            if (current.settings.method == InstallMethod.SHIZUKU) {
                val state = shizuku.awaitReady()
                if (!state.ready) {
                    status(UpdateStatus.ERROR, state.help)
                    return background && state == ShizukuState.NOT_RUNNING
                }
            }
            if (background && !idleForPrompt()) {
                status(UpdateStatus.WAITING_FOR_PLAYBACK)
                return true
            }
            status(UpdateStatus.DOWNLOADING)
            source.download(release, apk)
            installer.verify(apk, release)
            if (background) {
                backgroundInstallBlock(current.settings.method)?.let { blocked ->
                    status(blocked)
                    return blocked == UpdateStatus.WAITING_FOR_PLAYBACK
                }
            }

            val attempt = InstallAttempt(UUID.randomUUID().toString(), release.versionCode, background, System.currentTimeMillis())
            store.begin(attempt)
            watchAttempt(attempt)
            status(UpdateStatus.INSTALLING)
            val blockedAtCommit = AtomicReference<UpdateStatus?>()
            val canCommit: suspend () -> Boolean = {
                val blocked = if (background) backgroundInstallBlock(current.settings.method) else null
                blockedAtCommit.set(blocked)
                allowedToCommit.set(blocked == null)
                blocked == null
            }
            val submitted = when (current.settings.method) {
                InstallMethod.SHIZUKU -> installer.shizuku(apk, attempt.id, canCommit)
                InstallMethod.STANDARD -> installer.standard(apk, attempt.id, background, canCommit)
            }
            if (submitted) return false

            finishAttempt()
            val blocked = blockedAtCommit.get() ?: UpdateStatus.AVAILABLE
            status(blocked)
            return blocked == UpdateStatus.WAITING_FOR_PLAYBACK
        } catch (error: CancellationException) {
            if (!allowedToCommit.get()) {
                if (store.attempt() != null) finishAttempt()
                status(UpdateStatus.AVAILABLE)
            }
            throw error
        } catch (error: Exception) {
            if (reconcile()) return false
            finishAttempt()
            status(UpdateStatus.ERROR, error.message)
            return background && error is IOException
        }
    }

    /** Handles a process killed between committing a self-update and receiving its result. */
    private fun reconcile(expiredAttemptId: String? = null): Boolean {
        val attempt = store.attempt()
        if (attempt == null) {
            postPendingInstallNotification()
            return false
        }
        val installedVersion = installedCode(context)
        if (installedVersion >= attempt.version) {
            completeInstall(attempt, installedVersion)
            Log.i("SiloraUpdates", "Recovered installed version ${attempt.version}")
            return true
        }
        if (attempt.id == expiredAttemptId || System.currentTimeMillis() - attempt.startedAt >= INSTALL_TIMEOUT_MS) {
            context.packageManager.packageInstaller.mySessions
                .filter { it.appPackageName == context.packageName }.forEach { installer.abandon(it.sessionId) }
            finishAttempt()
            status(UpdateStatus.ERROR, "The installation did not finish. Choose Install update to try again.")
        } else {
            watchAttempt(attempt)
            status(UpdateStatus.INSTALLING)
        }
        return false
    }

    private fun watchAttempt(attempt: InstallAttempt) {
        if (recoveryJob?.isActive == true) return
        val remaining = (INSTALL_TIMEOUT_MS - (System.currentTimeMillis() - attempt.startedAt)).coerceIn(0L, INSTALL_TIMEOUT_MS)
        recoveryJob = scope.launch {
            delay(remaining)
            gate.withLock { reconcile(expiredAttemptId = attempt.id) }
        }
    }

    private fun finishAttempt(confirmationVersion: Long = 0, installedVersion: Long = 0, notify: Boolean = false) {
        store.finish(confirmationVersion, installedVersion, notify)
        _confirmation.value = null
        recoveryJob?.cancel()
        recoveryJob = null
    }

    @Synchronized
    private fun completeInstall(attempt: InstallAttempt, installedVersion: Long) {
        val notify = attempt.background && installedVersion == attempt.version &&
            state.value.settings.notifyOnAutomaticInstall
        finishAttempt(installedVersion = installedVersion, notify = notify)
        apk.delete()
        _state.update { it.copy(release = null, status = UpdateStatus.INSTALLED, detail = null) }
        postPendingInstallNotification()
    }

    @Synchronized
    private fun postPendingInstallNotification() {
        val pendingVersion = store.pendingInstallNotification() ?: return
        // A success callback can arrive before the package replacement is visible.
        val installedVersion = installedCode(context)
        if (installedVersion < pendingVersion) return
        if (installedVersion == pendingVersion && state.value.settings.notifyOnAutomaticInstall) {
            postAutomaticInstallNotification(context, installedVersion)
        }
        store.clearPendingInstallNotification()
    }

    internal suspend fun onInstallResult(intent: Intent, broadcastCode: Int) = gate.withLock {
        val id = intent.data?.lastPathSegment ?: return@withLock
        val attempt = store.attempt()?.takeIf { it.id == id } ?: return@withLock
        if (reconcile() || store.attempt()?.id != id) return@withLock
        val session = intent.getIntExtra(UpdateInstaller.EXTRA_SESSION, -1)
        val result = if (session < 0) broadcastCode
            else intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (result) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val prompt = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (!attempt.background && prompt != null) {
                    _confirmation.value = InstallConfirmation(prompt, session, id)
                } else {
                    installer.abandon(session)
                    finishAttempt(confirmationVersion = attempt.version)
                    status(UpdateStatus.NEEDS_CONFIRMATION)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> completeInstall(attempt, attempt.version)
            else -> {
                finishAttempt()
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
        private val INSTALL_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15)
        private var instance: SiloraUpdates? = null

        @Synchronized
        fun get(context: Context): SiloraUpdates =
            instance ?: SiloraUpdates(context.applicationContext).also { instance = it }
    }
}
