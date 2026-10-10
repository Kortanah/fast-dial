package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Announcement

object NoticeNotificationHelper {
    private const val TAG = "NoticeNotificationHelper"
    private const val CHANNEL_ID = "announcements_channel"
    private const val PREFS_NAME = "app_notices_prefs"
    private const val KEY_NOTIFIED_ANNOUNCEMENT_IDS = "notified_announcement_ids"

    fun handleFcmMessage(
        context: Context,
        id: String,
        title: String,
        message: String,
        category: String = "NOTICE",
        date: String = "Recent",
        actionText: String? = null,
        actionUrl: String? = null
    ) {
        if (id.isBlank() || id == "default_welcome") return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentNotifiedIds = prefs.getStringSet(KEY_NOTIFIED_ANNOUNCEMENT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentNotifiedIds.contains(id)) {
            Log.d(TAG, "FCM announcement '$id' already notified. Skipping duplicate.")
            return
        }

        val ann = Announcement(
            id = id,
            title = title,
            message = message,
            category = category,
            date = date,
            timestamp = System.currentTimeMillis(),
            actionText = actionText,
            actionUrl = actionUrl
        )
        showNativeNotification(context, ann)
    }

    fun showNativeNotification(context: Context, announcement: Announcement) {
        if (announcement.id == "default_welcome") return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentNotifiedIds = prefs.getStringSet(KEY_NOTIFIED_ANNOUNCEMENT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentNotifiedIds.contains(announcement.id)) {
            Log.d(TAG, "Notification for announcement '${announcement.id}' already shown. Skipping.")
            return
        }

        currentNotifiedIds.add(announcement.id)
        prefs.edit().putStringSet(KEY_NOTIFIED_ANNOUNCEMENT_IDS, currentNotifiedIds).apply()

        createNotificationChannel(context)

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_announcements", true)
                putExtra("announcement_id", announcement.id)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                announcement.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_custom)
                .setContentTitle(announcement.title)
                .setContentText(announcement.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(announcement.message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(announcement.id.hashCode(), builder.build())
                }
            } else {
                notificationManager.notify(announcement.id.hashCode(), builder.build())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post native notification: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Announcements & Updates"
            val descriptionText = "Notifications for new announcements, system notices, and updates"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
