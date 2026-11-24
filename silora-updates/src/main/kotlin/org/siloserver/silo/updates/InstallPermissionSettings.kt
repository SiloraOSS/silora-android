package org.siloserver.silo.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun openInstallPermissionSettings(context: Context): Boolean {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val screens = listOf(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )
    return screens.any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
