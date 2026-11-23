package org.siloserver.silo.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal object UpdateSchedule {
    private const val WORK_NAME = "silora-in-app-update-check"

    fun configure(context: Context, hours: Int, changed: Boolean = false): Operation? {
        val manager = WorkManager.getInstance(context)
        if (hours == 0) return if (changed) manager.cancelUniqueWork(WORK_NAME) else null
        val request = PeriodicWorkRequestBuilder<SiloraUpdateWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
            .build()
        return manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (changed) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

class SiloraUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (SiloraUpdates.get(applicationContext).scheduledCheck() || runAttemptCount >= 2) Result.success()
        else Result.retry()
}

class UpdateReplacementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val updates = SiloraUpdates.get(context)
        val pending = goAsync()
        updates.scope.launch {
            try {
                updates.afterReplacement()
            } catch (error: Exception) {
                Log.w("SiloraUpdates", "Could not restore update scheduling.", error)
            } finally {
                pending.finish()
            }
        }
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = SiloraUpdates.get(context)
        val code = resultCode
        val pending = goAsync()
        updates.scope.launch {
            try {
                updates.onInstallResult(intent, code)
            } finally {
                pending.finish()
            }
        }
    }
}
