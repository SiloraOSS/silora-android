package org.siloserver.silo.updates

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

fun openInstallPermissionSettings(context: Context): Boolean {
    val screens = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val packageUri = Uri.fromParts("package", context.packageName, null)
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri))
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
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
