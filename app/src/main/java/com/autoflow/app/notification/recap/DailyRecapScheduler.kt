package com.autoflow.app.notification.recap

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.autoflow.app.presentation.preferences.SharedPreferencesNotificationPreferences
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object DailyRecapScheduler {
    const val UNIQUE_WORK_NAME = "autoflow_daily_recap_work"

    /**
     * Calculates the delay in milliseconds until the next occurrence of [targetHour]:[targetMinute].
     * If the target time today has already passed, schedules for tomorrow at the same time.
     */
    fun calculateDelayMillis(
        targetHour: Int,
        targetMinute: Int,
        now: ZonedDateTime
    ): Long {
        var target = now
            .withHour(targetHour)
            .withMinute(targetMinute)
            .withSecond(0)
            .withNano(0)

        if (!target.isAfter(now)) {
            target = target.plusDays(1)
        }

        return Duration.between(now, target).toMillis()
    }

    fun scheduleNextRun(context: Context, replaceExisting: Boolean = false) {
        val prefs = SharedPreferencesNotificationPreferences.fromContext(context)
        if (!prefs.isDailyRecapEnabled()) {
            cancel(context)
            return
        }

        val hour = prefs.getDailyRecapTimeHour()
        val minute = prefs.getDailyRecapTimeMinute()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val delayMillis = calculateDelayMillis(hour, minute, now)

        val workRequest = OneTimeWorkRequestBuilder<DailyRecapWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, policy, workRequest)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
