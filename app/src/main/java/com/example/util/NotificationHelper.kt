package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object NotificationHelper {
    const val CHANNEL_MESSAGES = "leno_messages_channel"
    const val CHANNEL_SYSTEM = "leno_system_channel"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Leno Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for new incoming chat messages"
                enableVibration(true)
            }

            val systemChannel = NotificationChannel(
                CHANNEL_SYSTEM,
                "Leno System & Connections",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for connection requests and system announcements"
            }

            notificationManager.createNotificationChannel(messageChannel)
            notificationManager.createNotificationChannel(systemChannel)
        }
    }

    fun showMessageNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        senderId: String? = null
    ) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!senderId.isNull_or_Empty()) {
                    putExtra("chat_partner_id", senderId)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val smallIconRes = context.applicationInfo.icon

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(if (smallIconRes != 0) smallIconRes else android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing message notification: ${e.message}")
        }
    }

    private fun String?.isNull_or_Empty(): Boolean = this == null || this.isEmpty()
}
