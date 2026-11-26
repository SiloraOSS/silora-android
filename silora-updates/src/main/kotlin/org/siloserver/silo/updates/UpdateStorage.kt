package org.siloserver.silo.updates

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.system.ErrnoException
import android.system.OsConstants
import java.io.File
import java.io.IOException

internal class InsufficientUpdateStorage : IOException()

internal fun IOException.isInsufficientUpdateStorage(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.any { error ->
        error is InsufficientUpdateStorage ||
            error is ErrnoException && error.errno in setOf(OsConstants.ENOSPC, OsConstants.EDQUOT) ||
            error.message?.let(::isStorageFailureMessage) == true
    }

internal fun isStorageFailureMessage(message: String): Boolean =
    message.contains("no space left on device", ignoreCase = true) ||
        message.contains("disk quota exceeded", ignoreCase = true) ||
        message.contains("not enough space", ignoreCase = true) ||
        message.contains("insufficient storage", ignoreCase = true) ||
        message.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ||
        (message.contains("failed to allocate", ignoreCase = true) &&
            message.contains("allocatable", ignoreCase = true))

internal fun requireUpdateDownloadSpace(context: Context, directory: File, bytes: Long) {
    val available = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(StorageManager::class.java)
        runCatching { manager?.getAllocatableBytes(manager.getUuidForPath(directory)) }.getOrNull()
    } else {
        null
    }
    if ((available ?: directory.usableSpace) < bytes) throw InsufficientUpdateStorage()
}

fun openUpdateStorageSettings(context: Context): Boolean =
    buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) add(StorageManager.ACTION_MANAGE_STORAGE)
        add(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
    }.any { action ->
        try {
            context.startActivity(Intent(action))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

// A retry needs a change in real free bytes, even if the estimated reclaimable cache stays flat.
@SuppressLint("UsableSpace")
internal fun updateRetryFreeBytes(directory: File): Long = directory.usableSpace
