package com.autoflow.app.notification.recap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.presentation.preferences.SharedPreferencesNotificationPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class DailyRecapWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SharedPreferencesNotificationPreferences.fromContext(applicationContext)
        if (!prefs.isDailyRecapEnabled()) {
            return Result.success()
        }

        try {
            val app = applicationContext as? AutoFlowApplication
            val repository = app?.transactionRepository
            if (repository != null) {
                val zoneId = ZoneId.systemDefault()
                val today = LocalDate.now(zoneId)
                val startOfDay = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

                val todayTxs = repository.getTransactionsInRange(startOfDay, endOfDay).first()
                val content = DailyRecapContentBuilder.buildContent(todayTxs)

                if (content != null) {
                    DailyRecapNotificationHelper.postNotification(applicationContext, content)
                }
            }
        } catch (_: Exception) {
            // Error handled cleanly without crashing
        } finally {
            DailyRecapScheduler.scheduleNextRun(applicationContext, replaceExisting = true)
        }

        return Result.success()
    }
}
