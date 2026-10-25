package com.autoflow.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.autoflow.app.MainActivity
import com.autoflow.app.R
import com.autoflow.app.data.db.AutoFlowDatabase
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.data.model.UncertaintyReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AutoFlowSpendWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_HOME = 3001

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, AutoFlowSpendWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            if (appWidgetIds.isEmpty()) return

            CoroutineScope(Dispatchers.IO).launch {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            }
        }

        suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val database = AutoFlowDatabase.getDatabase(context.applicationContext)
            val entities = database.transactionDao().getAllActiveTransactions().first()
            val domainList = entities.map { entity ->
                FinancialTransaction(
                    id = entity.id,
                    amountPaise = entity.amountPaise,
                    recordType = entity.recordType,
                    dateTime = entity.dateTime,
                    merchantName = entity.merchantName,
                    category = entity.category,
                    categorySource = entity.categorySource,
                    paymentMode = entity.paymentMode,
                    note = entity.note,
                    accountNumber = entity.accountNumber,
                    bankName = entity.bankName,
                    reference = entity.reference,
                    transactionHash = entity.transactionHash,
                    source = entity.source,
                    reviewStatus = entity.reviewStatus,
                    uncertaintyReasons = UncertaintyReason.fromBitmask(entity.uncertaintyBits),
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    isDeleted = entity.isDeleted != 0
                )
            }
            val content = SpendWidgetContentBuilder.build(domainList)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_autoflow_spend)
                views.setTextViewText(R.id.tv_spend_title, content.title)
                views.setTextViewText(R.id.tv_spend_amount, content.amountFormatted)

                val homeIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_HOME,
                    homeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                views.setOnClickPendingIntent(R.id.widget_spend_container, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
