package com.autoflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.autoflow.app.MainActivity
import com.autoflow.app.R

class AutoFlowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_EXPENSE = "com.autoflow.app.action.WIDGET_EXPENSE"
        const val ACTION_SAVED = "com.autoflow.app.action.WIDGET_SAVED"

        private const val REQUEST_CODE_EXPENSE = 1001
        private const val REQUEST_CODE_SAVED = 1002

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_autoflow)

            // PendingIntent for + Expense
            val expenseIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_EXPENSE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val expensePendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_EXPENSE,
                expenseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btn_widget_expense, expensePendingIntent)

            // PendingIntent for + Saved
            val savedIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_SAVED
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val savedPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_SAVED,
                savedIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btn_widget_saved, savedPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
