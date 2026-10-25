package com.autoflow.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.MainActivity
import com.autoflow.app.R
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.preferences.NotificationPreferences
import com.autoflow.app.presentation.preferences.SharedPreferencesNotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object CapturedExpenseNotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val name = context.getString(R.string.notification_channel_captured_expenses)
            val descriptionText = context.getString(R.string.notification_channel_captured_expenses_desc)
            val channel = NotificationChannel(
                CapturedExpenseNotificationBuilder.CHANNEL_ID,
                name,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = descriptionText
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun notifyIfEnabled(
        context: Context,
        transactionId: Long,
        preferences: NotificationPreferences = SharedPreferencesNotificationPreferences.fromContext(context)
    ) {
        if (!preferences.isCapturedExpenseNotificationEnabled()) {
            return
        }

        val notificationManager = NotificationManagerCompat.from(context)
        if (!notificationManager.areNotificationsEnabled()) {
            return
        }

        val app = context.applicationContext as? AutoFlowApplication ?: return
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val tx = app.transactionRepository.getTransactionById(transactionId) ?: return@launch
            if (tx.isDeleted || tx.recordType != RecordType.EXPENSE) {
                return@launch
            }

            val categories = try {
                app.categoryRepository.getAllCategories().first().map { it.name }
            } catch (e: Exception) {
                BUILT_IN_CATEGORIES
            }

            val content = CapturedExpenseNotificationBuilder.buildInitialContent(tx, categories)
            postNotification(context, content, tx.id)
        }
    }

    fun postNotification(
        context: Context,
        content: NotificationContent,
        transactionId: Long,
        undoExtras: Map<String, Any?> = emptyMap()
    ) {
        ensureChannel(context)

        // Lock screen: visibility PRIVATE with public version
        val publicNotification = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_cat_wallet)
            .setContentTitle(content.publicTitle)
            .setContentText(content.publicText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Content intent: opens Edit transaction in MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = CapturedExpenseNotificationBuilder.ACTION_EDIT_TRANSACTION
            putExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, transactionId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (transactionId * 10).toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_cat_wallet)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)

        // Action buttons
        content.actions.forEachIndexed { index, action ->
            val pendingIntent = if (action.isActivity) {
                val actIntent = Intent(context, MainActivity::class.java).apply {
                    this.action = action.actionType
                    putExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, transactionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                PendingIntent.getActivity(
                    context,
                    (transactionId * 10 + index + 1).toInt(),
                    actIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                val bcastIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    this.action = action.actionType
                    putExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, transactionId)
                    if (action.category != null) {
                        putExtra(CapturedExpenseNotificationBuilder.EXTRA_CATEGORY, action.category)
                    }
                    undoExtras.forEach { (key, value) ->
                        when (value) {
                            is String -> putExtra(key, value)
                            is Int -> putExtra(key, value)
                            is Long -> putExtra(key, value)
                            is Boolean -> putExtra(key, value)
                        }
                    }
                }
                PendingIntent.getBroadcast(
                    context,
                    (transactionId * 10 + index + 1).toInt(),
                    bcastIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            builder.addAction(0, action.title, pendingIntent)
        }

        if (content.timeoutMs != null) {
            builder.setTimeoutAfter(content.timeoutMs)
            scheduleAutoDismiss(context, content.notificationId, content.timeoutMs)
        }

        val notificationManager = NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(content.notificationId, builder.build())
        } catch (e: SecurityException) {
            // Permission missing
        }
    }

    private fun scheduleAutoDismiss(context: Context, notificationId: Int, delayMs: Long) {
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(notificationId)
        }, delayMs)
    }
}
