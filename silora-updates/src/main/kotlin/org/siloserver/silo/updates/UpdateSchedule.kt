package org.siloserver.silo.updates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

internal const val UPDATE_RETRY_MINUTES = 15L
internal const val MAX_UPDATE_RETRIES = 3

internal object UpdateSchedule {
    private const val WORK_NAME = "silora-in-app-update-check"

    private fun dailyHourTag(hour: Int) = "$WORK_NAME-daily-$hour"

    suspend fun hasOutdatedDailyWork(context: Context, hour: Int): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).first().any { work ->
            !work.state.isFinished && dailyHourTag(hour) !in work.tags
        }

    fun configure(
        context: Context,
        settings: UpdateSettings,
        restart: Boolean = false,
        nextRunAt: Long? = null,
    ): Operation {
        val manager = WorkManager.getInstance(context)
        if (!settings.interval.enabled) return manager.cancelUniqueWork(WORK_NAME)

        val hours = settings.interval.hours.toLong()
        val dailyHour = settings.dailyCheckHour
        val request = PeriodicWorkRequestBuilder<SiloraUpdateWorker>(hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, UPDATE_RETRY_MINUTES, TimeUnit.MINUTES)
            .apply {
                if (dailyHour == null) {
                    setInitialDelay(hours, TimeUnit.HOURS)
                } else {
                    addTag(dailyHourTag(dailyHour))
                }
                val override = nextRunAt ?: dailyHour?.let { nextDailyCheck(it, System.currentTimeMillis()) }
                if (override != null) setNextScheduleTimeOverride(override)
            }
            .build()
        val policy = when {
            dailyHour == null && restart -> ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
            // Preserve a queued daily check, including one that is already overdue.
            dailyHour != null && !restart && nextRunAt == null -> ExistingPeriodicWorkPolicy.KEEP
            // UPDATE supports daily overrides and applies the new default without resetting fixed work.
            else -> ExistingPeriodicWorkPolicy.UPDATE
        }
        return manager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
    }
}

internal fun nextDailyCheck(hour: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): Long {
    val next = Calendar.getInstance(zone)
    fun setHour() {
        next.set(Calendar.HOUR_OF_DAY, hour)
        next.set(Calendar.MINUTE, 0)
        next.set(Calendar.SECOND, 0)
        next.set(Calendar.MILLISECOND, 0)
    }
    next.timeInMillis = now
    setHour()
    if (next.timeInMillis <= now) {
        next.timeInMillis = now
        next.add(Calendar.DATE, 1)
        setHour()
    }
    return next.timeInMillis
}

/** Format the selected local hour using the device's 12/24-hour preference. */
fun formatUpdateCheckHour(context: Context, hour: Int): String =
    DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(TimeUnit.HOURS.toMillis(hour.toLong())))

class SiloraUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (SiloraUpdates.get(applicationContext).scheduledCheck(runAttemptCount)) Result.success()
        else Result.retry()
}

class UpdateScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = SiloraUpdates.get(context)
        val action = intent.action
        val pending = goAsync()
        updates.scope.launch {
            try {
                withTimeout(8_000) {
                    when (action) {
                        Intent.ACTION_MY_PACKAGE_REPLACED -> updates.afterReplacement()
                        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> updates.afterClockChange()
                    }
                }
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
