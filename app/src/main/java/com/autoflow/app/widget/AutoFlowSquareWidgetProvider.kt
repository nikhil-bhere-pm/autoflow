package com.autoflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.autoflow.app.MainActivity
import com.autoflow.app.R

class AutoFlowSquareWidgetProvider : AppWidgetProvider() {

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
        private const val REQUEST_CODE_EXPENSE_SQUARE = 2001
        private const val REQUEST_CODE_SAVED_SQUARE = 2002

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_autoflow_square)

            // PendingIntent for + Expense
            val expenseIntent = Intent(context, MainActivity::class.java).apply {
                action = AutoFlowWidgetProvider.ACTION_EXPENSE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val expensePendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_EXPENSE_SQUARE,
                expenseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btn_widget_expense, expensePendingIntent)

            // PendingIntent for + Saved
            val savedIntent = Intent(context, MainActivity::class.java).apply {
                action = AutoFlowWidgetProvider.ACTION_SAVED
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val savedPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_SAVED_SQUARE,
                savedIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btn_widget_saved, savedPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
