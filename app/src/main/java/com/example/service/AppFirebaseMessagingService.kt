package com.example.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        // Automatically subscribe devices to the public "announcements" topic if Play Services is available
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(applicationContext)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.d(TAG, "Google Play Services unavailable (code: $resultCode), skipping FCM topic subscription.")
                return
            }
            FirebaseMessaging.getInstance().subscribeToTopic("announcements")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Subscribed to FCM topic: announcements")
                    } else {
                        Log.d(TAG, "FCM subscription skipped or not supported: ${task.exception?.message}")
                    }
                }
        } catch (e: Throwable) {
            Log.d(TAG, "Skipping FCM topic subscription: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val id = data["id"] ?: data["announcement_id"] ?: remoteMessage.messageId ?: ""
        val title = notification?.title ?: data["title"] ?: "Announcement"
        val message = notification?.body ?: data["message"] ?: ""
        val category = data["category"] ?: "NOTICE"
        val date = data["date"] ?: "Recent"
        val actionText = data["actionText"]
        val actionUrl = data["actionUrl"]

        if (id.isNotBlank()) {
            NoticeNotificationHelper.handleFcmMessage(
                context = applicationContext,
                id = id,
                title = title,
                message = message,
                category = category,
                date = date,
                actionText = actionText,
                actionUrl = actionUrl
            )
        }
    }

    companion object {
        private const val TAG = "AppFcmService"
    }
}
