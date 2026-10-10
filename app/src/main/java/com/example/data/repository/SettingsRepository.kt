package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(appContext: Context) {

    private val prefs: SharedPreferences = appContext.getSharedPreferences("flow_automator_settings", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings(appContext))
    val settingsState: StateFlow<UserSettings> = _settingsState.asStateFlow()

    private fun loadSettings(appContext: Context): UserSettings {
        val hasThemeSet = prefs.contains("is_dark_mode")
        return UserSettings(
            isDarkModeEnabled = prefs.getBoolean("is_dark_mode", false),
            useSystemTheme = prefs.getBoolean("use_system_theme", !hasThemeSet),
            isBiometricEnabled = prefs.getBoolean("is_biometric_enabled", false),
            isPinLockEnabled = prefs.getBoolean("is_pin_lock_enabled", false),
            pinHash = prefs.getString("pin_hash", "") ?: "",
            autoLockTimeoutMinutes = prefs.getInt("auto_lock_timeout", 5),
            defaultTimeoutSeconds = prefs.getInt("default_timeout", 40),
            retryAttempts = prefs.getInt("retry_attempts", 1),
            automationDelayMs = prefs.getLong("automation_delay", 50L),
            simSlot = prefs.getInt("sim_slot", 1),
            autoVaultSensitiveNodes = prefs.getBoolean("auto_vault", true),
            isAccessibilityPermissionEnabled = prefs.getBoolean("is_accessibility_enabled", true),
            isContactsPermissionEnabled = prefs.getBoolean("is_contacts_enabled", true),
            isAccountDeleted = prefs.getBoolean("is_account_deleted", false),
            language = prefs.getString("app_language", null) ?: com.example.data.model.CountryConstants.detectDeviceLanguage(appContext),
            userCountry = prefs.getString("user_country", null) ?: com.example.data.model.CountryConstants.detectDeviceCountry(appContext),
            userRole = prefs.getString("user_role", "Default") ?: "Default",
            selectedNetwork = prefs.getString("selected_network", "") ?: ""
        )
    }

    fun updateSettings(newSettings: UserSettings) {
        prefs.edit().apply {
            putBoolean("is_dark_mode", newSettings.isDarkModeEnabled)
            putBoolean("use_system_theme", newSettings.useSystemTheme)
            putBoolean("is_biometric_enabled", newSettings.isBiometricEnabled)
            putBoolean("is_pin_lock_enabled", newSettings.isPinLockEnabled)
            putString("pin_hash", newSettings.pinHash)
            putInt("auto_lock_timeout", newSettings.autoLockTimeoutMinutes)
            putInt("default_timeout", newSettings.defaultTimeoutSeconds)
            putInt("retry_attempts", newSettings.retryAttempts)
            putLong("automation_delay", newSettings.automationDelayMs)
            putInt("sim_slot", newSettings.simSlot)
            putBoolean("auto_vault", newSettings.autoVaultSensitiveNodes)
            putBoolean("is_accessibility_enabled", newSettings.isAccessibilityPermissionEnabled)
            putBoolean("is_contacts_enabled", newSettings.isContactsPermissionEnabled)
            putBoolean("is_account_deleted", newSettings.isAccountDeleted)
            putString("app_language", newSettings.language)
            putString("user_country", newSettings.userCountry)
            putString("user_role", newSettings.userRole)
            putString("selected_network", newSettings.selectedNetwork)
            apply()
        }
        _settingsState.value = newSettings
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString("pin_hash", "") ?: ""
        return if (storedHash.isEmpty()) enteredPin == "1234" else storedHash == enteredPin
    }

    fun savePin(pin: String) {
        prefs.edit().putString("pin_hash", pin).putBoolean("is_pin_lock_enabled", true).apply()
        _settingsState.value = _settingsState.value.copy(pinHash = pin, isPinLockEnabled = true)
    }

    fun getDeviceId(): String {
        var devId = prefs.getString("device_id", "") ?: ""
        if (devId.isEmpty()) {
            devId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", devId).apply()
        }
        return devId
    }

    fun getFirebaseProjectId(): String = prefs.getString("firebase_project_id", "") ?: ""
    fun getFirebaseApiKey(): String = prefs.getString("firebase_api_key", "") ?: ""
    fun getFirebaseAppId(): String = prefs.getString("firebase_app_id", "") ?: ""

    fun saveFirebaseConfig(projectId: String, apiKey: String, appId: String) {
        prefs.edit().apply {
            putString("firebase_project_id", projectId)
            putString("firebase_api_key", apiKey)
            putString("firebase_app_id", appId)
            apply()
        }
    }
}
