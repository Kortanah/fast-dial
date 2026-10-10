package com.example.data.model

data class Announcement(
    val id: String,
    val title: String,
    val message: String,
    val category: String = "NOTICE", // NOTICE, UPDATE, FEATURE, ALERT
    val date: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0L, // 0L means no expiry
    val actionText: String? = null,
    val actionUrl: String? = null,
    val isRead: Boolean = false
)

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersionCode: Int = 1,
    val latestVersionName: String = "1.0.0",
    val minRequiredVersionCode: Int = 1,
    val updateTitle: String = "App Update Available",
    val updateMessage: String = "A new version is available with performance improvements and new features.",
    val isForceUpdate: Boolean = false,
    val updateUrl: String = ""
)

data class RatingPromptState(
    val shouldShow: Boolean = false,
    val hasRatedOrDismissed: Boolean = false,
    val launchCount: Int = 0
)
