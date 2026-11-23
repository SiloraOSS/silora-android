package org.siloserver.silo.updates

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.File
import java.io.IOException


internal fun installedCode(context: Context): Long = context.packageManager.getPackageInfo(context.packageName, 0).let {
    if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
}

internal class UpdateInstaller(private val context: Context, private val shizuku: ShizukuManager) {
    @Suppress("DEPRECATION")
    fun verify(apk: File, release: SiloraRelease) {
        val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pm = context.packageManager
        val current = pm.getPackageInfo(context.packageName, flag)
        val archive = pm.getPackageArchiveInfo(apk.path, flag) ?: throw IOException("This file is not an Android APK.")
        val currentCode = installedCode(context)
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(archive.packageName == context.packageName && archiveCode == release.versionCode && archiveCode > currentCode) {
            "The APK is not a newer Silora build for this device."
        }
        val currentSigners = if (Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else current.signatures
        val archiveSigners = if (Build.VERSION.SDK_INT >= 28) archive.signingInfo?.apkContentsSigners else archive.signatures
        require(!currentSigners.isNullOrEmpty() && currentSigners.toSet() == archiveSigners?.toSet()) {
            "The APK was signed with a different key."
        }
    }

    fun standard(apk: File, attempt: String, background: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(if (background) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                    else PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                session.commit(callback(attempt, sessionId).intentSender)
            }
        } catch (error: Exception) {
            abandon(sessionId)
            throw error
        }
    }

    fun abandon(sessionId: Int) {
        if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
    }

    suspend fun shizuku(apk: File, attempt: String) {
        val status = shizuku.awaitReady()
        if (!status.ready) throw IOException(status.help)
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuInstallService::class.java))
            .tag("silora-updates-${Process.myUid()}")
            .processNameSuffix("shizuku_install")
            .version(installedCode(context).toInt())
            .daemon(true)
        val connected = CompletableDeferred<IShizukuInstaller>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                connected.complete(IShizukuInstaller.Stub.asInterface(binder))
            }
            override fun onServiceDisconnected(name: ComponentName) {
                connected.completeExceptionally(IOException("Shizuku disconnected. Start it and try again."))
            }
        }
        try {
            val service = withTimeout(15_000) {
                withContext(Dispatchers.Main.immediate) { Shizuku.bindUserService(args, connection) }
                connected.await()
            }
            if (service.uid !in listOf(0, 2000)) throw IOException("Shizuku did not provide installation privileges.")
            ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use {
                service.install(context.packageName, arrayOf(it), arrayOf("base.apk"), longArrayOf(apk.length()),
                    callback(attempt, -1).intentSender)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }
    }

    private fun callback(attempt: String, sessionId: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
            .setData(Uri.parse("silora-update://install/$attempt"))
            .putExtra(EXTRA_SESSION, sessionId)
        val flags = when {
            sessionId < 0 -> PendingIntent.FLAG_IMMUTABLE
            Build.VERSION.SDK_INT >= 31 -> PendingIntent.FLAG_MUTABLE
            else -> 0
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        const val EXTRA_SESSION = "silora_update_session"
    }
}
