package org.siloserver.silo.updates

import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.annotation.Keep
import kotlin.system.exitProcess
import java.util.Timer
import java.util.TimerTask

/** Loaded by Shizuku, not an exported Android service. Installs only Silora. */
@Keep
class ShizukuInstallService : IShizukuInstaller.Stub() {
    private var cleanup = stopAfter(30_000)

    private fun stopAfter(delay: Long) = Timer("shizuku-cleanup", true).apply {
        schedule(object : TimerTask() { override fun run() = exitProcess(0) }, delay)
    }

    override fun getUid(): Int = Process.myUid()

    @Synchronized
    override fun install(
        packageName: String,
        apks: Array<ParcelFileDescriptor>,
        names: Array<String>,
        sizes: LongArray,
        result: IntentSender,
        commitCheck: IUpdateCommitCheck,
    ): Boolean {
        cleanup.cancel()
        try {
            val status = try {
                require(packageName == SILORA_PACKAGE) { "This is not a Silora package" }
                require(apks.size == names.size && apks.size == sizes.size) { "Invalid APK input" }
                val sources = apks.mapIndexed { index, fd ->
                    InstallSource(names[index], sizes[index], ParcelFileDescriptor.AutoCloseInputStream(fd))
                }
                // Preserve the caller's Android user/profile, including when Shizuku runs as root.
                val userId = Binder.getCallingUid() / 100_000
                val installer = ShellPackageInstaller(InstallProcessRunner())
                if (!installer.install(sources, packageName, userId, commitCheck::canCommit)) return false
                PackageInstaller.STATUS_SUCCESS
            } catch (error: Exception) {
                val message = error.message ?: "Shizuku could not install the update"
                Log.w("ShizukuInstall", message.take(700))
                when {
                    "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in message || "INSTALL_FAILED_CONFLICTING_PROVIDER" in message -> PackageInstaller.STATUS_FAILURE_CONFLICT
                    isStorageFailureMessage(message) -> PackageInstaller.STATUS_FAILURE_STORAGE
                    "INSTALL_FAILED_NO_MATCHING_ABIS" in message || "INSTALL_FAILED_OLDER_SDK" in message -> PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                    else -> PackageInstaller.STATUS_FAILURE
                }
            } finally {
                apks.forEach { runCatching { it.close() } }
            }
            // Sending no fill-in Intent avoids ContentResolver in the privileged process.
            try {
                result.sendIntent(null, status, null, null, null)
            } catch (error: Exception) {
                throw IllegalStateException("Installation finished but its result could not be recorded. Reopen Silora.", error)
            }
            return true
        } finally {
            // Also exit if the caller died and cannot unbind this daemon after completion.
            cleanup = stopAfter(10_000)
        }
    }

    override fun destroy() = exitProcess(0)
}
