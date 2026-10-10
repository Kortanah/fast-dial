package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.RecentContactEntity
import com.example.data.model.ExecutionLog
import com.example.data.model.FlowCategory
import com.example.data.model.SecurityAuditIssue
import com.example.data.model.UssdFlow
import com.example.data.model.PhoneContact
import com.example.data.model.UserSettings
import com.example.data.repository.FlowRepository
import com.example.data.repository.NoticeRepository
import com.example.data.repository.SettingsRepository
import com.example.data.model.Announcement
import com.example.data.model.AppUpdateInfo
import com.example.data.model.PackageConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AuthState(
    val isAuthenticated: Boolean = false,
    val userEmail: String = "",
    val userName: String = "",
    val userCountry: String = "Ghana",
    val userRole: String = "Default",
    val isLocked: Boolean = false,
    val needsOnboarding: Boolean = false,
    val isApproved: Boolean = true,
    val isAdmin: Boolean? = null,
    val isMultipleDeviceLoginDetected: Boolean = false,
    val isAccountBlocked: Boolean = false,
    val blockReason: String = ""
)

data class DashboardStats(
    val totalFlows: Int = 0,
    val successfulExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val todayExecutions: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "flow_automator_db"
    ).fallbackToDestructiveMigration(true).build()

    val repository = FlowRepository(db.flowDao(), db.executionDao())
    val settingsRepository = SettingsRepository(application)
    val noticeRepository = NoticeRepository(application)

    val announcements: StateFlow<List<Announcement>> = noticeRepository.announcements
    val unreadAnnouncementCount: StateFlow<Int> = noticeRepository.unreadCount
    val appUpdateInfo: StateFlow<AppUpdateInfo> = noticeRepository.appUpdateInfo
    val showRatingDialog: StateFlow<Boolean> = noticeRepository.showRatingDialog
    val showUpdateDialog: StateFlow<Boolean> = noticeRepository.showUpdateDialog
    val packageConfig: StateFlow<PackageConfig> = noticeRepository.packageConfig

    fun fetchAnnouncements(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            noticeRepository.fetchAnnouncements(forceRefresh)
        }
    }

    fun checkForAppUpdate(forceCheck: Boolean = false) {
        viewModelScope.launch {
            noticeRepository.checkForAppUpdate(forceCheck)
        }
    }

    fun markAnnouncementAsRead(id: String) {
        noticeRepository.markAnnouncementAsRead(id)
    }

    fun markAllAnnouncementsAsRead() {
        noticeRepository.markAllAnnouncementsAsRead()
    }

    fun dismissRatingDialog(neverAskAgain: Boolean = false) {
        noticeRepository.dismissRatingDialog(neverAskAgain)
    }

    fun onUserRatedApp() {
        noticeRepository.onUserRatedApp()
    }

    fun dismissUpdateDialog() {
        noticeRepository.dismissUpdateDialog()
    }

    fun openPlayStore(customUrl: String? = null) {
        noticeRepository.openPlayStore(customUrl)
    }

    fun triggerRatingPrompt() {
        noticeRepository.triggerManualRatingPrompt()
    }

    fun recordFlowExecuted() {
        noticeRepository.recordFlowExecuted()
    }

    private var firebaseAuth: com.google.firebase.auth.FirebaseAuth? = null
    private var firestore: com.google.firebase.firestore.FirebaseFirestore? = null
    private var userSnapshotListener: com.google.firebase.firestore.ListenerRegistration? = null

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val userSettings: StateFlow<UserSettings> = settingsRepository.settingsState

    val recentContacts: StateFlow<List<PhoneContact>> = db.recentContactDao().getAllRecentContacts()
        .map { entities -> entities.map { it.toDomainModel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRecentContact(contact: PhoneContact) {
        viewModelScope.launch {
            db.recentContactDao().insertContact(RecentContactEntity.fromDomainModel(contact))
            db.recentContactDao().trimOldContacts()
        }
    }

    val allFlows: StateFlow<List<UssdFlow>> = repository.allFlows
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val executionLogs: StateFlow<List<ExecutionLog>> = repository.executionLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dashboardStats: StateFlow<DashboardStats> = combine(allFlows, executionLogs) { flows, logs ->
        val totalFlowsCount = flows.size
        val totalSuccess = logs.count { it.status == com.example.data.model.ExecutionState.COMPLETED }
        val totalFailed = logs.count { it.status == com.example.data.model.ExecutionState.FAILED }
        
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val todayCount = logs.count { (now - it.timestamp) < oneDayMs }

        DashboardStats(
            totalFlows = totalFlowsCount,
            successfulExecutions = totalSuccess,
            failedExecutions = totalFailed,
            todayExecutions = todayCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    private val _securityAuditIssues = MutableStateFlow<List<SecurityAuditIssue>>(emptyList())
    val securityAuditIssues: StateFlow<List<SecurityAuditIssue>> = _securityAuditIssues.asStateFlow()

    private val _notifications = MutableStateFlow<List<String>>(
        listOf(
            "Welcome to Fast Dail v2.4!",
            "AES 256-bit Vault Encryption active",
            "5 Ready-to-use USSD Templates loaded"
        )
    )
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    private val _templates = MutableStateFlow<List<com.example.data.model.UssdFlow>>(emptyList())
    val templates: StateFlow<List<com.example.data.model.UssdFlow>> = _templates.asStateFlow()

    private val _activeExecutionFlow = MutableStateFlow<com.example.data.model.UssdFlow?>(null)
    val activeExecutionFlow: StateFlow<com.example.data.model.UssdFlow?> = _activeExecutionFlow.asStateFlow()

    fun setActiveExecutionFlow(flow: com.example.data.model.UssdFlow?) {
        _activeExecutionFlow.value = flow
    }

    init {
        try {
            firebaseAuth = com.google.firebase.auth.FirebaseAuth.getInstance()
            firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val currentUser = firebaseAuth?.currentUser
            if (currentUser != null) {
                if (!currentUser.isEmailVerified) {
                    firebaseAuth?.signOut()
                    _authState.value = AuthState(isAuthenticated = false)
                } else {
                    _authState.value = AuthState(
                        isAuthenticated = true,
                        userEmail = currentUser.email ?: "",
                        userName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: currentUser.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User",
                        isLocked = false
                    )
                    fetchUserMetadata(currentUser.uid)
                }
            }
            loadCachedTemplatesOrFetch()
        } catch (e: Exception) {
            android.util.Log.w("MainViewModel", "FirebaseAuth could not be initialized: ${e.message}")
            loadCachedTemplatesOrFetch()
        }
        viewModelScope.launch {
            repository.migrateVariableKeysInDatabase()
            runSecurityAudit()
        }
    }

    fun updateUserCountry(country: String) {
        if (country.isBlank()) return
        _authState.value = _authState.value.copy(userCountry = country)
        val updatedSettings = userSettings.value.copy(userCountry = country)
        settingsRepository.updateSettings(updatedSettings)
        val uid = firebaseAuth?.currentUser?.uid
        if (uid != null && firestore != null) {
            firestore?.collection("users")?.document(uid)
                ?.update("country", country)
                ?.addOnFailureListener {
                    android.util.Log.w("MainViewModel", "Failed to update country in firestore: ${it.message}")
                }
        }
    }

    fun updateUserCountryAndLanguage(country: String) {
        updateUserCountry(country)
    }

    fun setAppLanguage(language: String) {
        val updatedSettings = userSettings.value.copy(language = language)
        settingsRepository.updateSettings(updatedSettings)
    }

    fun updateUserRole(role: String) {
        if (role.isBlank()) return
        _authState.value = _authState.value.copy(userRole = role)
        val updatedSettings = userSettings.value.copy(userRole = role)
        settingsRepository.updateSettings(updatedSettings)
        val uid = firebaseAuth?.currentUser?.uid
        if (uid != null && firestore != null) {
            firestore?.collection("users")?.document(uid)
                ?.update("userRole", role)
                ?.addOnFailureListener {
                    android.util.Log.w("MainViewModel", "Failed to update role in firestore: ${it.message}")
                }
        }
    }

    private fun fetchUserMetadata(uid: String) {
        userSnapshotListener?.remove()
        userSnapshotListener = firestore?.collection("users")?.document(uid)?.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("MainViewModel", "Error in userSnapshotListener: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status") ?: ""
                val isDeleted = status.equals("account deleted", ignoreCase = true) ||
                                status.equals("deleted", ignoreCase = true)
                if (isDeleted) {
                    signOut()
                    return@addSnapshotListener
                }

                val isBlocked = snapshot.getBoolean("isBlocked") == true ||
                                snapshot.getBoolean("blocked") == true ||
                                status.equals("blocked", ignoreCase = true)
                if (isBlocked) {
                    val defaultReason = "Your account has been blocked. You no longer have access to the service. If you believe this was done in error, please contact support."
                    val reason = snapshot.getString("blockReason")
                        ?: snapshot.getString("reason")
                        ?: defaultReason
                    _authState.value = _authState.value.copy(
                        isAccountBlocked = true,
                        blockReason = reason,
                        isAuthenticated = false
                    )
                    signOut()
                    return@addSnapshotListener
                }

                val localDevId = settingsRepository.getDeviceId()
                val activeDeviceId = snapshot.getString("activeDeviceId") ?: ""
                if (activeDeviceId.isNotEmpty() && activeDeviceId != localDevId) {
                    _authState.value = _authState.value.copy(
                        isMultipleDeviceLoginDetected = true
                    )
                    signOut()
                    return@addSnapshotListener
                }

                val isAdmin = snapshot.getBoolean("isAdmin") ?: false
                val country = snapshot.getString("country") ?: ""
                val phone = snapshot.getString("phone") ?: ""
                val isProfileCompleted = snapshot.getBoolean("isProfileCompleted") ?: (phone.isNotBlank() && country.isNotBlank())
                val userRole = snapshot.getString("userRole") ?: snapshot.getString("role") ?: userSettings.value.userRole
                val isApprovedBool = snapshot.getBoolean("isApproved")
                val isApproved = isApprovedBool ?: (isAdmin || status.equals("active", ignoreCase = true) || status.equals("approved", ignoreCase = true))
                val rawDocName = snapshot.getString("name") ?: ""
                
                _authState.value = _authState.value.copy(
                    isApproved = isApproved,
                    isAdmin = isAdmin,
                    needsOnboarding = !isProfileCompleted,
                    userName = if (rawDocName.isNotBlank()) rawDocName else _authState.value.userName,
                    userRole = if (userRole.isNotBlank()) userRole else _authState.value.userRole,
                    userCountry = if (country.isNotBlank()) country else _authState.value.userCountry
                )
                if (userRole.isNotBlank() && userSettings.value.userRole != userRole) {
                    settingsRepository.updateSettings(userSettings.value.copy(userRole = userRole))
                }
                if (country.isNotBlank() && userSettings.value.userCountry != country) {
                    updateUserCountry(country)
                }
            }
        }
    }

    private fun loadCachedTemplatesOrFetch() {
        val context = getApplication<android.app.Application>()
        val prefs = context.getSharedPreferences("templates_cache", android.content.Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("cached_templates", null)

        if (!cachedJson.isNullOrEmpty()) {
            try {
                val cachedList = repository.importAllFlowsFromJsonString(cachedJson)
                if (cachedList.isNotEmpty()) {
                    _templates.value = cachedList
                    refreshTemplates(forceServer = false)
                    return
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error parsing cached templates", e)
            }
        }

        refreshTemplates(forceServer = true)
    }

    fun refreshTemplates(forceServer: Boolean = true, onComplete: ((Int) -> Unit)? = null) {
        fetchTemplatesFromDatabase(forceServer) { fetched ->
            saveTemplatesToCache(fetched)
            _templates.value = fetched
            onComplete?.invoke(fetched.size)
        }
    }

    private fun saveTemplatesToCache(templatesList: List<com.example.data.model.UssdFlow>) {
        try {
            val json = repository.exportAllFlowsToJson(templatesList)
            val context = getApplication<android.app.Application>()
            val prefs = context.getSharedPreferences("templates_cache", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cached_templates", json).apply()
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Failed to cache templates", e)
        }
    }

    private fun getDefaultTemplates(): List<com.example.data.model.UssdFlow> {
        return emptyList()
    }

    private fun fetchTemplatesFromDatabase(forceServer: Boolean = true, onComplete: (List<com.example.data.model.UssdFlow>) -> Unit) {
        val fs = firestore
        if (fs == null) {
            onComplete(getDefaultTemplates())
            return
        }
        val source = if (forceServer) com.google.firebase.firestore.Source.SERVER else com.google.firebase.firestore.Source.DEFAULT
        fs.collection("templates").get(source).addOnSuccessListener { result ->
            val fetchedTemplates = mutableListOf<com.example.data.model.UssdFlow>()
            for (document in result) {
                val jsonStr = document.getString("flowJson")
                if (jsonStr != null) {
                    val flow = repository.importFlowFromJsonString(jsonStr)
                    if (flow != null) {
                        val docNetwork = document.getString("network")
                        val docUserRole = document.getString("userRole")
                        val docCountry = document.getString("country")
                        val docIcon = document.getString("icon")
                        val docStatus = document.getString("status") ?: "active"

                        val docIsTemplate = document.getBoolean("isTemplate")
                        val isTemplateFlag = docIsTemplate ?: flow.isTemplate

                        val docGridBtn = document.getBoolean("grid_btn")
                            ?: document.getBoolean("gridBtn")
                            ?: (document.get("grid_btn") as? Boolean)
                            ?: (document.getString("grid_btn")?.equals("true", ignoreCase = true))
                        val gridBtnFlag = docGridBtn ?: flow.gridBtn

                        if (docStatus.equals("active", ignoreCase = true)) {
                            fetchedTemplates.add(
                                flow.copy(
                                    isTemplate = isTemplateFlag,
                                    id = "tpl_${document.id}",
                                    network = docNetwork ?: flow.network,
                                    userRole = docUserRole ?: flow.userRole,
                                    country = docCountry ?: flow.country,
                                    icon = docIcon ?: flow.icon,
                                    gridBtn = gridBtnFlag
                                )
                            )
                        }
                    }
                }
            }
            if (fetchedTemplates.isNotEmpty()) {
                onComplete(fetchedTemplates)
            } else {
                onComplete(getDefaultTemplates())
            }
        }.addOnFailureListener { e ->
            if (forceServer) {
                // Retry with DEFAULT source if SERVER fetch fails (e.g. offline)
                fetchTemplatesFromDatabase(forceServer = false, onComplete)
            } else {
                android.util.Log.w("MainViewModel", "Could not fetch remote templates (${e.message}). Loading built-in default templates.")
                onComplete(getDefaultTemplates())
            }
        }
    }

    fun login(email: String, pass: String, forceOverride: Boolean = false, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val auth = firebaseAuth
        if (auth != null) {
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null && !user.isEmailVerified) {
                            try {
                                user.sendEmailVerification()
                            } catch (e: Exception) {
                                android.util.Log.w("MainViewModel", "sendEmailVerification failed: ${e.message}")
                            }
                            auth.signOut()
                            _authState.value = _authState.value.copy(
                                isAuthenticated = false,
                                isLocked = false,
                                isMultipleDeviceLoginDetected = false
                            )
                            onResult(false, "EMAIL_NOT_VERIFIED:${user.email ?: email}")
                            return@addOnCompleteListener
                        }
                        user?.uid?.let { uid ->
                            val localDevId = settingsRepository.getDeviceId()

                            // Fetch user document to check account status (deleted/blocked) and activeDeviceId
                            firestore?.collection("users")?.document(uid)?.get()?.addOnSuccessListener { document ->
                                if (document != null && document.exists()) {
                                    val docStatus = document.getString("status") ?: ""
                                    
                                    // 1. Check if account is deleted (Strict Deletion Flow: single source of truth is status)
                                    val isDeleted = docStatus.equals("account deleted", ignoreCase = true) ||
                                                    docStatus.equals("deleted", ignoreCase = true)
                                    if (isDeleted) {
                                        auth.signOut()
                                        onResult(false, "This account has been deleted.")
                                        return@addOnSuccessListener
                                    }

                                    // 2. Check if account is blocked
                                    val isBlocked = document.getBoolean("isBlocked") == true ||
                                                    document.getBoolean("blocked") == true ||
                                                    docStatus.equals("blocked", ignoreCase = true)
                                    if (isBlocked) {
                                        val defaultReason = "Your account has been blocked. You no longer have access to the service. If you believe this was done in error, please contact support."
                                        val reason = document.getString("blockReason")
                                            ?: document.getString("reason")
                                            ?: defaultReason
                                        auth.signOut()
                                        _authState.value = _authState.value.copy(
                                            isAccountBlocked = true,
                                            blockReason = reason,
                                            isAuthenticated = false
                                        )
                                        onResult(false, "ACCOUNT_BLOCKED:$reason")
                                        return@addOnSuccessListener
                                    }

                                    // 3. Check multi-device active session
                                    val activeDeviceId = document.getString("activeDeviceId") ?: ""
                                    if (activeDeviceId.isNotEmpty() && activeDeviceId != localDevId && !forceOverride) {
                                        auth.signOut()
                                        onResult(false, "CONFIRMATION_REQUIRED")
                                        return@addOnSuccessListener
                                    }
                                }

                                val rawDocName = document?.getString("name") ?: ""
                                val cleanNameInDoc = if (rawDocName.isNotBlank()) {
                                    rawDocName
                                } else {
                                    user.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                                }

                                // Update user active status and device info in Firestore
                                val userData = hashMapOf<String, Any>(
                                    "name" to cleanNameInDoc,
                                    "email" to (user.email ?: email),
                                    "status" to "active",
                                    "activeDeviceId" to localDevId,
                                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                firestore?.collection("users")?.document(uid)?.set(userData, com.google.firebase.firestore.SetOptions.merge())

                                val docStatus = document?.getString("status") ?: ""
                                val isAdmin = document?.getBoolean("isAdmin") ?: false
                                val isApprovedBool = document?.getBoolean("isApproved")
                                val isApproved = isApprovedBool ?: (isAdmin || docStatus.isEmpty() || docStatus.equals("active", ignoreCase = true) || docStatus.equals("approved", ignoreCase = true))
                                
                                val isDocCompleted = if (document != null && document.exists()) {
                                    document.getBoolean("isProfileCompleted") ?: (
                                        document.getString("phone")?.isNotBlank() == true &&
                                        document.getString("country")?.isNotBlank() == true
                                    )
                                } else false

                                val countryInDoc = document?.getString("country") ?: userSettings.value.userCountry
                                val roleInDoc = document?.getString("userRole") ?: userSettings.value.userRole

                                _authState.value = _authState.value.copy(
                                    isAuthenticated = true,
                                    userEmail = user.email ?: email,
                                    userName = cleanNameInDoc,
                                    userCountry = countryInDoc,
                                    userRole = roleInDoc,
                                    needsOnboarding = !isDocCompleted,
                                    isLocked = false,
                                    isApproved = isApproved,
                                    isAdmin = isAdmin,
                                    isMultipleDeviceLoginDetected = false,
                                    isAccountBlocked = false
                                )
                                fetchUserMetadata(uid)
                                onResult(true, null)
                            }?.addOnFailureListener {
                                // Default write active device ID and log in if document check fails
                                val userData = hashMapOf(
                                    "name" to (user.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }),
                                    "email" to (user.email ?: email),
                                    "status" to "active",
                                    "activeDeviceId" to localDevId,
                                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                firestore?.collection("users")?.document(uid)?.set(userData, com.google.firebase.firestore.SetOptions.merge())
                                _authState.value = _authState.value.copy(
                                    isAuthenticated = true,
                                    userEmail = user.email ?: email,
                                    userName = user.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() },
                                    needsOnboarding = true,
                                    isLocked = false,
                                    isMultipleDeviceLoginDetected = false,
                                    isAccountBlocked = false
                                )
                                fetchUserMetadata(uid)
                                onResult(true, null)
                            }
                        } ?: run {
                            _authState.value = _authState.value.copy(
                                isAuthenticated = true,
                                userEmail = email,
                                userName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                                needsOnboarding = true,
                                isLocked = false,
                                isMultipleDeviceLoginDetected = false,
                                isAccountBlocked = false
                            )
                            onResult(true, null)
                        }
                    } else {
                        if (task.exception is com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                            if (pass.length < 6) {
                                onResult(false, "Password must be at least 6 characters.")
                                return@addOnCompleteListener
                            }
                            val devId = settingsRepository.getDeviceId()
                            auth.createUserWithEmailAndPassword(email, pass)
                                .addOnCompleteListener { createParam ->
                                    if (createParam.isSuccessful) {
                                        val newUser = auth.currentUser
                                        val newUid = newUser?.uid
                                        if (newUid != null) {
                                            val cleanInitialName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
                                            val initialData = hashMapOf<String, Any>(
                                                "email" to email,
                                                "name" to cleanInitialName,
                                                "status" to "active",
                                                "activeDeviceId" to devId,
                                                "isProfileCompleted" to false,
                                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                            )
                                            firestore?.collection("users")?.document(newUid)?.set(initialData, com.google.firebase.firestore.SetOptions.merge())
                                            _authState.value = _authState.value.copy(
                                                isAuthenticated = true,
                                                userEmail = email,
                                                userName = cleanInitialName,
                                                needsOnboarding = true,
                                                isLocked = false,
                                                isMultipleDeviceLoginDetected = false,
                                                isAccountBlocked = false
                                            )
                                            fetchUserMetadata(newUid)
                                            onResult(true, null)
                                        } else {
                                            onResult(true, null)
                                        }
                                    } else {
                                        onResult(false, createParam.exception?.localizedMessage ?: "Registration failed")
                                    }
                                }
                            return@addOnCompleteListener
                        }
                        val errorMsg = when (val exception = task.exception) {
                            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
                            is com.google.firebase.FirebaseNetworkException -> "Network error. Please check your connection."
                            is com.google.firebase.FirebaseTooManyRequestsException -> "Too many failed attempts. Please try again later."
                            else -> exception?.localizedMessage ?: "Authentication failed"
                        }
                        onResult(false, errorMsg)
                    }
                }
        } else {
            // Local Sandbox Fallback
            viewModelScope.launch {
                if (email.contains("multidevice", ignoreCase = true)) {
                    onResult(false, "This account is already signed in on another device.")
                    return@launch
                }
                _authState.value = _authState.value.copy(
                    isAuthenticated = true,
                    userEmail = email,
                    userName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    isLocked = false,
                    isMultipleDeviceLoginDetected = false
                )
                onResult(true, "Sandbox Mode: Authenticated locally successfully")
            }
        }
    }

    fun signInWithGoogle(
        idToken: String,
        onResult: (success: Boolean, needsOnboarding: Boolean, errorMsg: String?) -> Unit
    ) {
        val auth = firebaseAuth
        if (auth == null) {
            viewModelScope.launch {
                val mockEmail = "user.google@example.com"
                val mockName = "Google User"
                _authState.value = _authState.value.copy(
                    isAuthenticated = true,
                    userEmail = mockEmail,
                    userName = mockName,
                    needsOnboarding = true,
                    isLocked = false
                )
                onResult(true, true, null)
            }
            return
        }

        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val uid = user.uid
                        val localDevId = settingsRepository.getDeviceId()

                        firestore?.collection("users")?.document(uid)?.get()
                            ?.addOnSuccessListener { doc ->
                                val exists = doc.exists()
                                val isCompleted = if (exists) {
                                    doc.getBoolean("isProfileCompleted") ?: (
                                        doc.getString("phone")?.isNotBlank() == true &&
                                        doc.getString("country")?.isNotBlank() == true
                                    )
                                } else false

                                val cleanName = doc.getString("name")?.takeIf { it.isNotBlank() }
                                    ?: user.displayName?.takeIf { it.isNotBlank() }
                                    ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                                    ?: "User"
                                val country = doc.getString("country") ?: userSettings.value.userCountry
                                val userRole = doc.getString("userRole") ?: userSettings.value.userRole

                                val updates = hashMapOf<String, Any>(
                                    "email" to (user.email ?: ""),
                                    "name" to cleanName,
                                    "status" to "active",
                                    "activeDeviceId" to localDevId,
                                    "lastLogin" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                                if (!exists) {
                                    updates["createdAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    updates["isProfileCompleted"] = false
                                }
                                firestore?.collection("users")?.document(uid)?.set(updates, com.google.firebase.firestore.SetOptions.merge())

                                _authState.value = _authState.value.copy(
                                    isAuthenticated = true,
                                    userEmail = user.email ?: "",
                                    userName = cleanName,
                                    userCountry = country,
                                    userRole = userRole,
                                    needsOnboarding = !isCompleted,
                                    isLocked = false,
                                    isApproved = true,
                                    isAccountBlocked = false
                                )
                                fetchUserMetadata(uid)
                                onResult(true, !isCompleted, null)
                            }
                            ?.addOnFailureListener {
                                _authState.value = _authState.value.copy(
                                    isAuthenticated = true,
                                    userEmail = user.email ?: "",
                                    userName = user.displayName ?: "User",
                                    needsOnboarding = true,
                                    isLocked = false
                                )
                                fetchUserMetadata(uid)
                                onResult(true, true, null)
                            } ?: run {
                                _authState.value = _authState.value.copy(
                                    isAuthenticated = true,
                                    userEmail = user.email ?: "",
                                    userName = user.displayName ?: "User",
                                    needsOnboarding = true,
                                    isLocked = false
                                )
                                onResult(true, true, null)
                            }
                    } else {
                        onResult(false, false, "Unable to load Google user profile")
                    }
                } else {
                    onResult(false, false, task.exception?.localizedMessage ?: "Google Sign-In failed")
                }
            }
    }

    fun completeProfileOnboarding(
        name: String,
        phone: String,
        country: String,
        userRole: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val uid = firebaseAuth?.currentUser?.uid
        val email = firebaseAuth?.currentUser?.email ?: _authState.value.userEmail

        val profileData = hashMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "country" to country,
            "userRole" to userRole,
            "isProfileCompleted" to true,
            "status" to "active",
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        val updatedSettings = userSettings.value.copy(userRole = userRole, userCountry = country)
        settingsRepository.updateSettings(updatedSettings)
        updateUserCountry(country)

        if (uid != null && firestore != null) {
            firestore?.collection("users")?.document(uid)
                ?.set(profileData, com.google.firebase.firestore.SetOptions.merge())
                ?.addOnSuccessListener {
                    _authState.value = _authState.value.copy(
                        userName = name,
                        userCountry = country,
                        userRole = userRole,
                        needsOnboarding = false
                    )
                    onResult(true, null)
                }
                ?.addOnFailureListener {
                    _authState.value = _authState.value.copy(
                        userName = name,
                        userCountry = country,
                        userRole = userRole,
                        needsOnboarding = false
                    )
                    onResult(true, null)
                }
        } else {
            _authState.value = _authState.value.copy(
                userName = name,
                userCountry = country,
                userRole = userRole,
                needsOnboarding = false
            )
            onResult(true, null)
        }
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        pass: String,
        country: String,
        userRole: String = "Default",
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        val auth = firebaseAuth
        if (auth != null) {
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                            displayName = name
                        }
                        user?.updateProfile(profileUpdates)
                        
                        user?.uid?.let { uid ->
                            val userData = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "phone" to phone,
                                "country" to country,
                                "userRole" to userRole,
                                "status" to "active",
                                "isAdmin" to false,
                                "isApproved" to true,
                                "isProfileCompleted" to true,
                                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                            firestore?.collection("users")?.document(uid)?.set(userData, com.google.firebase.firestore.SetOptions.merge())
                        }
                        
                        if (user != null) {
                            try {
                                user.sendEmailVerification()
                            } catch (e: Exception) {
                                android.util.Log.w("MainViewModel", "sendEmailVerification failed on register: ${e.message}")
                            }
                            auth.signOut()
                            _authState.value = _authState.value.copy(
                                isAuthenticated = false,
                                userRole = userRole,
                                needsOnboarding = false
                            )
                            val updatedSettings = userSettings.value.copy(userRole = userRole, userCountry = country)
                            settingsRepository.updateSettings(updatedSettings)
                            updateUserCountry(country)
                            onResult(false, "EMAIL_NOT_VERIFIED:${user.email ?: email}")
                            return@addOnCompleteListener
                        }

                        _authState.value = _authState.value.copy(
                            isAuthenticated = true,
                            userEmail = user?.email ?: email,
                            userName = name,
                            userRole = userRole,
                            needsOnboarding = false,
                            isApproved = true,
                            isLocked = false
                        )
                        val updatedSettings = userSettings.value.copy(userRole = userRole, userCountry = country)
                        settingsRepository.updateSettings(updatedSettings)
                        updateUserCountry(country)
                        onResult(true, null)
                    } else {
                        val errorMsg = when (val exception = task.exception) {
                            is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "An account already exists with this email."
                            is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Password is too weak. Please choose a stronger password."
                            is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Invalid email format."
                            is com.google.firebase.FirebaseNetworkException -> "Network error. Please check your connection."
                            else -> exception?.localizedMessage ?: "Registration failed"
                        }
                        onResult(false, errorMsg)
                    }
                }
        } else {
            // Local Sandbox Fallback
            viewModelScope.launch {
                _authState.value = _authState.value.copy(
                    isAuthenticated = true,
                    userEmail = email,
                    userName = name,
                    userRole = userRole,
                    isLocked = false
                )
                val updatedSettings = userSettings.value.copy(userRole = userRole, userCountry = country)
                settingsRepository.updateSettings(updatedSettings)
                onResult(true, "Sandbox Mode: Registered locally successfully")
            }
        }
    }

    fun selectNetwork(network: String) {
        if (network.isNotBlank() && userSettings.value.selectedNetwork != network) {
            settingsRepository.updateSettings(userSettings.value.copy(selectedNetwork = network))
        }
    }

    fun setUserRoleOverride(role: String) {
        if (role.isBlank()) return
        _authState.value = _authState.value.copy(userRole = role)
        settingsRepository.updateSettings(userSettings.value.copy(userRole = role))
    }

    fun resendVerificationEmail(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth != null && email.isNotBlank() && pass.isNotBlank()) {
            auth.signInWithEmailAndPassword(email, pass)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            user.sendEmailVerification()
                                .addOnCompleteListener { verifyTask ->
                                    auth.signOut()
                                    if (verifyTask.isSuccessful) {
                                        onResult(true, "Verification email resent successfully.")
                                    } else {
                                        onResult(false, verifyTask.exception?.localizedMessage ?: "Failed to resend verification email.")
                                    }
                                }
                        } else {
                            onResult(false, "User not found.")
                        }
                    } else {
                        onResult(false, task.exception?.localizedMessage ?: "Failed to authenticate. Check credentials.")
                    }
                }
        } else {
            onResult(false, "Please provide valid email and password.")
        }
    }

    fun resetPassword(email: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val auth = firebaseAuth
        if (auth != null) {
            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onResult(true, null)
                    } else {
                        val errorMsg = when (val exception = task.exception) {
                            is com.google.firebase.auth.FirebaseAuthInvalidUserException -> "No account found with this email."
                            is com.google.firebase.FirebaseNetworkException -> "Network error. Please check your connection."
                            else -> exception?.localizedMessage ?: "Reset failed"
                        }
                        onResult(false, errorMsg)
                    }
                }
        } else {
            viewModelScope.launch {
                onResult(true, "Sandbox Mode: Reset instructions simulated")
            }
        }
    }

    fun logout() {
        try {
            com.example.gate.GateManager.detachListener()
            userSnapshotListener?.remove()
            userSnapshotListener = null

            val currentUser = firebaseAuth?.currentUser
            val uid = currentUser?.uid
            val localDevId = settingsRepository.getDeviceId()
            if (uid != null && firestore != null) {
                firestore?.collection("users")?.document(uid)?.get()?.addOnSuccessListener { doc ->
                    if (doc != null && doc.getString("activeDeviceId") == localDevId) {
                        firestore?.collection("users")?.document(uid)?.set(
                            mapOf("activeDeviceId" to ""),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    }
                }
            }

            firebaseAuth?.signOut()
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error signing out of Firebase Auth", e)
        }
        val currentBlocked = _authState.value.isAccountBlocked
        val currentReason = _authState.value.blockReason
        val currentMultiDev = _authState.value.isMultipleDeviceLoginDetected

        _authState.value = _authState.value.copy(
            isAuthenticated = false,
            isLocked = false,
            isApproved = true,
            isAdmin = null,
            isAccountBlocked = currentBlocked,
            blockReason = currentReason,
            isMultipleDeviceLoginDetected = currentMultiDev
        )
    }

    fun signOut() {
        logout()
    }

    fun clearMultipleDeviceLoginFlag() {
        _authState.value = _authState.value.copy(isMultipleDeviceLoginDetected = false)
    }

    fun clearAccountBlockedFlag() {
        _authState.value = _authState.value.copy(isAccountBlocked = false, blockReason = "")
    }

    fun simulateMultipleDeviceKickout() {
        _authState.value = _authState.value.copy(
            isMultipleDeviceLoginDetected = true
        )
        logout()
    }

    fun lockApp() {
        if (userSettings.value.isPinLockEnabled || userSettings.value.isBiometricEnabled) {
            _authState.value = _authState.value.copy(isLocked = true)
        }
    }

    fun unlockAppWithPin(pin: String): Boolean {
        val isValid = settingsRepository.verifyPin(pin)
        if (isValid) {
            _authState.value = _authState.value.copy(isLocked = false)
        }
        return isValid
    }

    fun runSecurityAudit() {
        viewModelScope.launch {
            val flows = repository.allFlows.first()
            _securityAuditIssues.value = repository.runSecurityScanOnFlows(flows)
        }
    }

    fun autoVaultFlow(flow: UssdFlow) {
        viewModelScope.launch {
            val vaulted = com.example.engine.SecurityScanner.autoVaultFlow(flow)
            repository.saveFlow(vaulted)
            runSecurityAudit()
        }
    }

    fun disableAccount(onComplete: () -> Unit) {
        val user = firebaseAuth?.currentUser
        if (user != null) {
            val uid = user.uid
            val data = hashMapOf<String, Any>(
                "status" to "account deleted"
            )
            firestore?.collection("users")?.document(uid)?.set(data, com.google.firebase.firestore.SetOptions.merge())?.addOnCompleteListener {
                signOut()
                onComplete()
            } ?: run {
                signOut()
                onComplete()
            }
        } else {
            signOut()
            onComplete()
        }
    }

    fun deleteFlow(flowId: String) {
        viewModelScope.launch {
            repository.deleteFlow(flowId)
            runSecurityAudit()
        }
    }

    fun duplicateFlow(flow: UssdFlow) {
        viewModelScope.launch {
            repository.duplicateFlow(flow)
            runSecurityAudit()
        }
    }

    fun importFlowFromJson(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val imported = repository.importFlowFromJson(json)
            onResult(imported != null)
            runSecurityAudit()
        }
    }

    fun exportAllFlows(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val flows = repository.allFlows.first()
            val json = repository.exportAllFlowsToJson(flows)
            onResult(json)
        }
    }

    fun importAllFlowsFromJson(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importAllFlowsFromJson(json)
            onResult(success)
            runSecurityAudit()
        }
    }

    fun updateUserSettings(settings: UserSettings) {
        settingsRepository.updateSettings(settings)
    }

    override fun onCleared() {
        super.onCleared()
        userSnapshotListener?.remove()
        userSnapshotListener = null
    }
}
