package org.siloserver.silo.updates

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

private const val INSTALL_CHANNEL = "silora-automatic-installs"

internal fun postAutomaticInstallNotification(context: Context, installedVersion: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (!manager.areNotificationsEnabled()) return

    try {
        val packageManager = context.packageManager
        val versionName = packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
        val notificationId = installedVersion.toInt()
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(context, notificationId, it, PendingIntent.FLAG_IMMUTABLE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                INSTALL_CHANNEL,
                context.getString(R.string.updates_install_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ))
        }
        val builder = NotificationCompat.Builder(context, INSTALL_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.updates_install_notification_title))
            .setContentText(context.getString(R.string.updates_install_notification_body, versionName))
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            builder.extend(NotificationCompat.TvExtender().setChannelId(INSTALL_CHANNEL).setContentIntent(contentIntent))
        }
        manager.notify(INSTALL_CHANNEL, notificationId, builder.build())
    } catch (error: SecurityException) {
        Log.w("SiloraUpdates", "Could not post the automatic installation notification.", error)
    }
}
