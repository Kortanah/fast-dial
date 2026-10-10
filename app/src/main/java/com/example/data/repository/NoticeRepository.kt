package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.AppUpdateInfo
import com.example.data.model.PackageConfig
import com.example.data.model.PackageItem
import com.example.service.NoticeNotificationHelper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class NoticeRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("app_notices_prefs", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _appUpdateInfo = MutableStateFlow(AppUpdateInfo())
    val appUpdateInfo: StateFlow<AppUpdateInfo> = _appUpdateInfo.asStateFlow()

    private val _showRatingDialog = MutableStateFlow(false)
    val showRatingDialog: StateFlow<Boolean> = _showRatingDialog.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _packageConfig = MutableStateFlow(PackageConfig())
    val packageConfig: StateFlow<PackageConfig> = _packageConfig.asStateFlow()

    init {
        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_TIME, 0L)
        if (firstInstall == 0L) {
            prefs.edit().putLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis()).apply()
        }
        createNotificationChannel()
        subscribeToAnnouncementsTopic()
        loadCachedAnnouncements()
        incrementLaunchAndCheckRating()
        listenToAppUpdateConfig()
        listenToPackageConfig()
        listenToAnnouncements()
    }

    private fun subscribeToAnnouncementsTopic() {
        // FCM topic sync requires working GMS and valid credentials.
        // In emulator / restricted environments where GMS is not fully available,
        // FirebaseMessaging initiates background token sync that fails with hard failure.
        // Real announcements are already synced in real-time via Firestore snapshot listener (listenToAnnouncements).
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.d(TAG, "Google Play Services unavailable on this environment (code: $resultCode), skipping FCM topic subscription.")
                return
            }

            // Only attempt FCM if explicitly enabled
            val fcm = FirebaseMessaging.getInstance()
            if (!fcm.isAutoInitEnabled) {
                Log.d(TAG, "FCM auto init is disabled. Skipping topic subscription; announcements handled via Firestore.")
                return
            }

            fcm.subscribeToTopic("announcements")
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

    /**
     * Record whenever user completes a USSD flow execution.
     */
    fun recordFlowExecuted() {
        prefs.edit().putBoolean(KEY_HAS_EXECUTED_FLOW, true).apply()
    }

    /**
     * Increment app launch count and evaluate if rating prompt should be shown.
     * Follows Play Store UX guidelines: launchCount >= 5, daysSinceInstall >= 3,
     * and user has experienced genuine value (executed at least 1 flow or launchCount >= 8).
     * Cost: 0 Firebase reads/writes.
     */
    private fun incrementLaunchAndCheckRating() {
        val hasRated = prefs.getBoolean(KEY_HAS_RATED, false)
        if (hasRated) return

        val launchCount = prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LAUNCH_COUNT, launchCount).apply()

        val firstInstall = prefs.getLong(KEY_FIRST_INSTALL_TIME, System.currentTimeMillis())
        val currentTime = System.currentTimeMillis()
        val daysSinceInstall = (currentTime - firstInstall) / (1000 * 60 * 60 * 24)

        val lastPromptTime = prefs.getLong(KEY_LAST_RATING_PROMPT_TIME, 0L)
        val daysSinceLastPrompt = (currentTime - lastPromptTime) / (1000 * 60 * 60 * 24)

        val hasExecutedFlow = prefs.getBoolean(KEY_HAS_EXECUTED_FLOW, false)

        // Rating Trigger: launchCount >= 5 AND daysSinceInstall >= 3 AND (hasExecutedFlow OR launchCount >= 8)
        val experiencedValue = hasExecutedFlow || launchCount >= 8
        if (launchCount >= 5 && daysSinceInstall >= 3 && experiencedValue && (lastPromptTime == 0L || daysSinceLastPrompt >= 3)) {
            _showRatingDialog.value = true
        }
    }

    fun dismissRatingDialog(neverAskAgain: Boolean = false) {
        _showRatingDialog.value = false
        val editor = prefs.edit()
        editor.putLong(KEY_LAST_RATING_PROMPT_TIME, System.currentTimeMillis())
        if (neverAskAgain) {
            editor.putBoolean(KEY_HAS_RATED, true)
        }
        editor.apply()
    }

    fun onUserRatedApp() {
        _showRatingDialog.value = false
        prefs.edit().putBoolean(KEY_HAS_RATED, true).apply()
        openPlayStore()
    }

    fun openPlayStore(customUrl: String? = null) {
        val packageName = context.packageName
        val url = if (!customUrl.isNullOrBlank()) customUrl else "https://play.google.com/store/apps/details?id=$packageName"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (err: Exception) {
                Log.e(TAG, "Could not open Play Store: ${err.message}")
            }
        }
    }

    /**
     * Loads announcements from local SharedPreferences cache.
     * Cost: 0 Firebase reads.
     */
    private fun loadCachedAnnouncements() {
        val cachedJson = prefs.getString(KEY_CACHED_ANNOUNCEMENTS, null)
        val readIds = prefs.getStringSet(KEY_READ_ANNOUNCEMENT_IDS, emptySet()) ?: emptySet()

        val list = mutableListOf<Announcement>()
        val currentTime = System.currentTimeMillis()
        if (!cachedJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(cachedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val expiresAt = obj.optLong("expiresAt", 0L)

                    // Skip expired announcements
                    if (expiresAt > 0 && expiresAt < currentTime) {
                        continue
                    }

                    val ann = Announcement(
                        id = id,
                        title = obj.optString("title", "Announcement"),
                        message = obj.optString("message", ""),
                        category = obj.optString("category", "NOTICE"),
                        date = obj.optString("date", "Recent"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        expiresAt = expiresAt,
                        actionText = if (obj.has("actionText")) obj.optString("actionText") else null,
                        actionUrl = if (obj.has("actionUrl")) obj.optString("actionUrl") else null,
                        isRead = readIds.contains(id)
                    )
                    list.add(ann)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cached announcements: ${e.message}")
            }
        }

        // If no cached announcements, provide default welcoming system announcement
        if (list.isEmpty()) {
            val defaultAnn = Announcement(
                id = "default_welcome",
                title = "Welcome to FlowDial",
                message = "Automate your USSD operations securely with offline capabilities and real-time execution monitoring.",
                category = "NOTICE",
                date = "Today",
                isRead = readIds.contains("default_welcome")
            )
            list.add(defaultAnn)
        }

        _announcements.value = list
        updateUnreadCount(list)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Announcements & Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new announcements, system notices, and updates"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun listenToAnnouncements() {
        try {
            firestore.collection("config").document("announcements")
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.w(TAG, "Announcements snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        processAnnouncementsDoc(doc.data)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception listening to announcements: ${e.message}")
        }
    }

    private fun processAnnouncementsDoc(data: Map<String, Any>?) {
        if (data == null) return
        val rawItems = data["items"] as? List<Map<String, Any>> ?: emptyList()
        val readIds = prefs.getStringSet(KEY_READ_ANNOUNCEMENT_IDS, emptySet()) ?: emptySet()
        val notifiedIds = prefs.getStringSet(KEY_NOTIFIED_ANNOUNCEMENT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        val newList = mutableListOf<Announcement>()
        val jsonArray = JSONArray()
        val now = System.currentTimeMillis()

        for (item in rawItems) {
            val id = (item["id"] as? String) ?: "ann_${newList.size + 1}"
            val title = (item["title"] as? String) ?: "Notice"
            val message = (item["message"] as? String) ?: ""
            val category = (item["category"] as? String) ?: "NOTICE"
            val date = (item["date"] as? String) ?: "Recent"
            val parsedTimestamp = parseTimestamp(item["timestamp"]).let { if (it == 0L) System.currentTimeMillis() else it }
            val expiresAt = parseTimestamp(item["expiresAt"])
            val actionText = item["actionText"] as? String
            val actionUrl = item["actionUrl"] as? String

            if (expiresAt > 0 && expiresAt < now) {
                continue
            }

            val ann = Announcement(
                id = id,
                title = title,
                message = message,
                category = category,
                date = date,
                timestamp = parsedTimestamp,
                expiresAt = expiresAt,
                actionText = actionText,
                actionUrl = actionUrl,
                isRead = readIds.contains(id)
            )
            newList.add(ann)

            val jsonObj = JSONObject().apply {
                put("id", id)
                put("title", title)
                put("message", message)
                put("category", category)
                put("date", date)
                put("timestamp", parsedTimestamp)
                put("expiresAt", expiresAt)
                if (actionText != null) put("actionText", actionText)
                if (actionUrl != null) put("actionUrl", actionUrl)
            }
            jsonArray.put(jsonObj)

            // Trigger native notification if not previously notified
            if (!notifiedIds.contains(id) && id != "default_welcome") {
                NoticeNotificationHelper.showNativeNotification(context, ann)
                notifiedIds.add(id)
            }
        }

        if (newList.isNotEmpty()) {
            _announcements.value = newList
            updateUnreadCount(newList)
            prefs.edit()
                .putString(KEY_CACHED_ANNOUNCEMENTS, jsonArray.toString())
                .putStringSet(KEY_NOTIFIED_ANNOUNCEMENT_IDS, notifiedIds)
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    fun handleFcmMessage(
        id: String,
        title: String,
        message: String,
        category: String = "NOTICE",
        date: String = "Recent",
        actionText: String? = null,
        actionUrl: String? = null
    ) {
        NoticeNotificationHelper.handleFcmMessage(context, id, title, message, category, date, actionText, actionUrl)
    }

    fun showNativeNotification(announcement: Announcement) {
        NoticeNotificationHelper.showNativeNotification(context, announcement)
    }

    /**
     * Fetches announcements from Firestore with strict caching (e.g. max once per 6 hours unless forceRefresh).
     * This minimizes Firebase reads!
     */
    suspend fun fetchAnnouncements(forceRefresh: Boolean = false) {
        withContext(Dispatchers.IO) {
            val lastFetchTime = prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
            val isCacheValid = (System.currentTimeMillis() - lastFetchTime) < CACHE_EXPIRATION_MS

            if (!forceRefresh && isCacheValid && _announcements.value.size > 1) {
                return@withContext
            }

            try {
                // Read public announcements document under config/announcements or announcements collection
                firestore.collection("config").document("announcements").get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists() && doc.data != null) {
                            processAnnouncementsDoc(doc.data)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Firestore announcements fetch failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception fetching announcements: ${e.message}")
            }
        }
    }

    /**
     * Listens in real-time to config/app_info doc changes in Firestore.
     * When forceUpdate or a new version is pushed in Firestore, the UI receives it immediately.
     */
    private fun listenToAppUpdateConfig() {
        val currentVersionCode = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }

        try {
            firestore.collection("config").document("app_info")
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.w(TAG, "App update snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        val latestCode = (doc.getLong("latestVersionCode") ?: 1L).toInt()
                        val latestName = doc.getString("latestVersionName") ?: "1.0.0"
                        val minCode = (doc.getLong("minRequiredVersionCode") ?: 1L).toInt()
                        val title = doc.getString("updateTitle") ?: "New Version Available"
                        val message = doc.getString("updateMessage") ?: "A new update for FlowDial is available on the Play Store."
                        val force = doc.getBoolean("forceUpdate") ?: false
                        val url = doc.getString("updateUrl") ?: ""

                        val isForceUpdate = force || (currentVersionCode < minCode)
                        val isAvailable = (latestCode > currentVersionCode) || isForceUpdate

                        val info = AppUpdateInfo(
                            isUpdateAvailable = isAvailable,
                            latestVersionCode = latestCode,
                            latestVersionName = latestName,
                            minRequiredVersionCode = minCode,
                            updateTitle = title,
                            updateMessage = message,
                            isForceUpdate = isForceUpdate,
                            updateUrl = url
                        )

                        _appUpdateInfo.value = info
                        if (isAvailable) {
                            _showUpdateDialog.value = true
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception listening to app update info: ${e.message}")
        }
    }

    /**
     * Checks if a new app update is available by querying config/app_info doc.
     * Caches result to minimize Firebase reads.
     */
    suspend fun checkForAppUpdate(forceCheck: Boolean = false) {
        withContext(Dispatchers.IO) {
            val lastUpdateCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK_TIME, 0L)
            if (!forceCheck && (System.currentTimeMillis() - lastUpdateCheck) < CACHE_EXPIRATION_MS) {
                return@withContext
            }

            val currentVersionCode = try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
            } catch (e: Exception) {
                1
            }

            firestore.collection("config").document("app_info").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val latestCode = (doc.getLong("latestVersionCode") ?: 1L).toInt()
                        val latestName = doc.getString("latestVersionName") ?: "1.0.0"
                        val minCode = (doc.getLong("minRequiredVersionCode") ?: 1L).toInt()
                        val title = doc.getString("updateTitle") ?: "New Version Available"
                        val message = doc.getString("updateMessage") ?: "A new update for FlowDial is available on the Play Store."
                        val force = doc.getBoolean("forceUpdate") ?: false
                        val url = doc.getString("updateUrl") ?: ""

                        val isForceUpdate = force || (currentVersionCode < minCode)
                        val isAvailable = (latestCode > currentVersionCode) || isForceUpdate

                        val info = AppUpdateInfo(
                            isUpdateAvailable = isAvailable,
                            latestVersionCode = latestCode,
                            latestVersionName = latestName,
                            minRequiredVersionCode = minCode,
                            updateTitle = title,
                            updateMessage = message,
                            isForceUpdate = isForceUpdate,
                            updateUrl = url
                        )

                        _appUpdateInfo.value = info
                        if (isAvailable) {
                            _showUpdateDialog.value = true
                        }
                        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK_TIME, System.currentTimeMillis()).apply()
                    }
                }
                .addOnFailureListener {
                    Log.w(TAG, "App update check failed: ${it.message}")
                }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun markAnnouncementAsRead(id: String) {
        val currentRead = prefs.getStringSet(KEY_READ_ANNOUNCEMENT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentRead.add(id)
        prefs.edit().putStringSet(KEY_READ_ANNOUNCEMENT_IDS, currentRead).apply()

        val updated = _announcements.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _announcements.value = updated
        updateUnreadCount(updated)
    }

    fun markAllAnnouncementsAsRead() {
        val allIds = _announcements.value.map { it.id }.toSet()
        prefs.edit().putStringSet(KEY_READ_ANNOUNCEMENT_IDS, allIds).apply()

        val updated = _announcements.value.map { it.copy(isRead = true) }
        _announcements.value = updated
        updateUnreadCount(updated)
    }

    private fun updateUnreadCount(list: List<Announcement>) {
        _unreadCount.value = list.count { !it.isRead }
    }

    /**
     * Helper to safely parse any Firestore timestamp value for `expiresAt` or `timestamp`.
     * Supports Firebase Timestamp objects, numeric values (Unix millis or seconds), and String representations.
     */
    private fun parseTimestamp(value: Any?): Long {
        if (value == null) return 0L
        return when (value) {
            is Timestamp -> value.toDate().time
            is Number -> {
                val num = value.toLong()
                // If number is in seconds (e.g. 10-digit Unix timestamp under 100,000,000,000), convert to millis
                if (num in 1L..99999999999L) num * 1000L else num
            }
            is String -> {
                value.toLongOrNull()?.let { num ->
                    if (num in 1L..99999999999L) num * 1000L else num
                } ?: 0L
            }
            else -> 0L
        }
    }

    fun triggerManualRatingPrompt() {
        _showRatingDialog.value = true
    }

    /**
     * Listens in real-time to config/packages and config/pricing in Firestore.
     * Parses package price, durationValue, durationUnit (minutes/months), currency, etc.
     */
    private fun listenToPackageConfig() {
        try {
            firestore.collection("config").document("packages")
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.w(TAG, "Package config snapshot error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc != null && doc.exists()) {
                        val data = doc.data ?: return@addSnapshotListener
                        val topCurrency = (data["currency"] as? String) ?: "USD"
                        val activePkgId = (data["activePackageId"] as? String) ?: ""

                        val rawItems = data["packages"] ?: data["items"] ?: data["plans"] ?: data["pricing"]
                        val packageList = mutableListOf<PackageItem>()

                        if (rawItems is Map<*, *>) {
                            rawItems.forEach { (key, value) ->
                                val map = value as? Map<String, Any?>
                                if (map != null) {
                                    packageList.add(parsePackageItemMap(key.toString(), map, topCurrency))
                                }
                            }
                        } else if (rawItems is List<*>) {
                            rawItems.filterIsInstance<Map<String, Any?>>().forEachIndexed { idx, map ->
                                packageList.add(parsePackageItemMap("pkg_$idx", map, topCurrency))
                            }
                        }

                        _packageConfig.value = PackageConfig(
                            currency = topCurrency,
                            activePackageId = activePkgId,
                            packages = packageList
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception listening to package config: ${e.message}")
        }
    }

    private fun parsePackageItemMap(defaultId: String, map: Map<String, Any?>, fallbackCurrency: String): PackageItem {
        val id = (map["id"] as? String) ?: defaultId
        val name = (map["name"] as? String) ?: (map["title"] as? String) ?: (map["label"] as? String) ?: id
        val price = (map["price"] as? Number)?.toDouble()
            ?: (map["amount"] as? Number)?.toDouble()
            ?: (map["cost"] as? Number)?.toDouble()
            ?: (map["price"] as? String)?.toDoubleOrNull()
            ?: 0.0

        val currency = (map["currency"] as? String) ?: (map["curr"] as? String) ?: fallbackCurrency

        val durationValue = (map["durationValue"] as? Number)?.toLong()
            ?: (map["duration"] as? Number)?.toLong()
            ?: (map["duration_amount"] as? Number)?.toLong()
            ?: (map["length"] as? Number)?.toLong()
            ?: (map["period"] as? Number)?.toLong()
            ?: (map["duration"] as? String)?.toLongOrNull()
            ?: 0L

        val durationUnit = (map["durationUnit"] as? String)
            ?: (map["unit"] as? String)
            ?: (map["durationType"] as? String)
            ?: (map["periodUnit"] as? String)
            ?: "months"

        val active = (map["active"] as? Boolean) ?: (map["enabled"] as? Boolean) ?: true
        val description = (map["description"] as? String) ?: (map["desc"] as? String) ?: ""

        return PackageItem(
            id = id,
            name = name,
            price = price,
            currency = currency,
            durationValue = durationValue,
            durationUnit = durationUnit,
            active = active,
            description = description
        )
    }

    companion object {
        private const val TAG = "NoticeRepository"
        private const val CHANNEL_ID = "announcements_channel"
        private const val KEY_CACHED_ANNOUNCEMENTS = "cached_announcements_json"
        private const val KEY_READ_ANNOUNCEMENT_IDS = "read_announcement_ids"
        private const val KEY_NOTIFIED_ANNOUNCEMENT_IDS = "notified_announcement_ids"
        private const val KEY_LAST_FETCH_TIME = "last_announcement_fetch_time"
        private const val KEY_LAST_UPDATE_CHECK_TIME = "last_update_check_time"
        private const val KEY_LAUNCH_COUNT = "app_launch_count"
        private const val KEY_HAS_RATED = "has_rated_or_dismissed_app"
        private const val KEY_LAST_RATING_PROMPT_TIME = "last_rating_prompt_time"
        private const val KEY_FIRST_INSTALL_TIME = "first_install_time"
        private const val KEY_HAS_EXECUTED_FLOW = "has_executed_flow"

        private const val CACHE_EXPIRATION_MS = 6 * 60 * 60 * 1000L // 6 hours cache
    }
}
