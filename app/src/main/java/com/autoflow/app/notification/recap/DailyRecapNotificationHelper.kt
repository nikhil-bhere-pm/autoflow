package com.autoflow.app.notification.recap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoflow.app.MainActivity
import com.autoflow.app.R

object DailyRecapNotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val name = "Daily recap"
            val descriptionText = "Summary of today's spending"
            val channel = NotificationChannel(
                DailyRecapContent.CHANNEL_ID,
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

    fun createOpenIntent(context: Context): Intent {
        return com.autoflow.app.navigation.AppLaunchResolver.buildHomeIntent(
            Intent(context, MainActivity::class.java)
        )
    }

    fun postNotification(context: Context, content: DailyRecapContent) {
        ensureChannel(context)

        val openIntent = createOpenIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            5001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val publicNotification = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_cat_wallet)
            .setContentTitle(content.publicTitle)
            .setContentText(content.publicText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notification = NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_cat_wallet)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(content.notificationId, notification)
        } catch (_: SecurityException) {
            // Handled when notification permission is missing
        }
    }
}
