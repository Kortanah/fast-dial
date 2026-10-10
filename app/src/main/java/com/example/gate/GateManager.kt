package com.example.gate

import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GateManager {
    private const val TAG = "GateManager"
    private const val PREFS_NAME = "gate_prefs"
    private const val KEY_PENDING_ACTION_REF = "pending_action_ref"

    private val _isGateActive = MutableStateFlow(false)
    val isGateActive: StateFlow<Boolean> = _isGateActive.asStateFlow()

    private var userListenerRegistration: ListenerRegistration? = null
    private var configListenerRegistration: ListenerRegistration? = null
    private var activeUid: String? = null
    private var currentlyDisplayedFlagId: String? = null
    private var currentBottomSheet: GateBottomSheet? = null

    private var cachedUserFlags: Map<String, Any>? = null
    private var cachedConfigFlags: Map<String, Any>? = null

    fun savePendingActionRef(context: android.content.Context, reference: String) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_ACTION_REF, reference)
            .apply()
    }

    fun checkAndVerifyPendingAction(context: android.content.Context) {
        // Paused per user request
        return
    }

    /**
     * Attaches live Firestore snapshot listeners on config/flags and users/{uid}
     */
    fun attachListener(activity: FragmentActivity, uid: String) {
        // Paused per user request
        return
    }

    private fun parseFlagsFromSnapshot(snapshot: DocumentSnapshot?): Map<String, Any> {
        if (snapshot == null || !snapshot.exists()) return emptyMap()

        val result = mutableMapOf<String, Any>()
        val data = snapshot.data ?: return emptyMap()

        // 1. Parse 'flags' field if present
        val rawFlags = data["flags"]
        if (rawFlags is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (rawFlags as? Map<String, Any>)?.let { result.putAll(it) }
        } else if (rawFlags is Boolean && rawFlags) {
            result["flag_a1"] = true
        } else if (rawFlags is String && rawFlags.isNotBlank()) {
            result[rawFlags] = true
        }

        // 2. Also inspect root-level fields for flag keys (e.g., flag_a1, flag_a2, etc.)
        data.forEach { (key, value) ->
            if (key.startsWith("flag_")) {
                result[key] = value
            }
        }

        return result
    }

    /**
     * Detaches live snapshot listeners and dismisses any active gate
     */
    fun detachListener(activity: FragmentActivity? = null) {
        userListenerRegistration?.remove()
        userListenerRegistration = null

        configListenerRegistration?.remove()
        configListenerRegistration = null

        cachedUserFlags = null
        cachedConfigFlags = null
        activeUid = null

        activity?.let { dismissCurrentGate(it) } ?: run {
            currentBottomSheet?.dismissAllowingStateLoss()
            currentBottomSheet = null
            currentlyDisplayedFlagId = null
            _isGateActive.value = false
        }
    }

    fun reEvaluateGate(activity: FragmentActivity) {
        if (activeUid != null) {
            evaluateAndProcessGates(activity)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluateAndProcessGates(activity: FragmentActivity) {
        val userFlags = cachedUserFlags ?: emptyMap()
        val configFlags = cachedConfigFlags ?: emptyMap()

        // Combine flag keys from both central config and user document
        val allFlagKeys = userFlags.keys + configFlags.keys
        if (allFlagKeys.isEmpty()) {
            dismissCurrentGate(activity)
            return
        }

        val activeFlags = mutableListOf<GateFlag>()

        for (flagId in allFlagKeys) {
            val rawUserFlag = userFlags[flagId]
            val rawConfigFlag = configFlags[flagId]

            val userFlagData = rawUserFlag as? Map<String, Any>
            val configFlagData = rawConfigFlag as? Map<String, Any>

            // Extract active state dynamically
            val isUserActive = extractActive(rawUserFlag)
            val isGlobalActive = extractActive(rawConfigFlag)

            // User flag explicitly OVERWRITES config flag:
            val isEffectiveActive: Boolean = when {
                isUserActive != null -> isUserActive
                isGlobalActive != null -> isGlobalActive
                rawUserFlag != null -> true
                rawConfigFlag != null -> true
                else -> false
            }

            if (isEffectiveActive) {
                val title = extractTitle(configFlagData, userFlagData, flagId)
                val message = extractMessage(configFlagData, userFlagData, flagId)
                val optionsList = extractOptions(configFlagData, userFlagData, flagId)

                activeFlags.add(
                    GateFlag(
                        flagId = flagId,
                        active = true,
                        title = title,
                        message = message,
                        options = optionsList
                    )
                )
            }
        }

        if (activeFlags.isEmpty()) {
            // Backend flipped active = false -> auto-dismiss gate
            dismissCurrentGate(activity)
            return
        }

        if (activeFlags.size > 1) {
            Log.w(TAG, "Multiple active flags detected (${activeFlags.size}). Selecting first by key deterministically.")
        }

        // Sort by flagId deterministically
        val targetFlag = activeFlags.sortedBy { it.flagId }.first()

        // Display gate or update existing bottom sheet live with latest flag details
        if (currentlyDisplayedFlagId == targetFlag.flagId && currentBottomSheet?.isAdded == true) {
            currentBottomSheet?.updateFlag(targetFlag)
            _isGateActive.value = true
        } else {
            showGateBottomSheet(activity, targetFlag)
        }
    }

    private fun extractActive(flagData: Any?): Boolean? {
        if (flagData == null) return null
        if (flagData is Boolean) return flagData
        if (flagData is String) return flagData.equals("true", ignoreCase = true)
        if (flagData is Map<*, *>) {
            val activeKeys = listOf("active", "enabled", "is_active", "status", "activeState")
            for (k in activeKeys) {
                if (flagData.containsKey(k)) {
                    val v = flagData[k]
                    if (v is Boolean) return v
                    if (v is String) return v.equals("true", ignoreCase = true)
                    if (v is Number) return v.toInt() == 1
                }
            }
            return true
        }
        if (flagData is Number) return flagData.toInt() == 1
        return null
    }

    private fun extractTitle(configData: Map<String, Any>?, userData: Map<String, Any>?, flagId: String): String {
        val keys = listOf("title", "header", "heading", "name")
        for (k in keys) {
            (userData?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (k in keys) {
            (configData?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "Notice"
    }

    private fun extractMessage(configData: Map<String, Any>?, userData: Map<String, Any>?, flagId: String): String {
        val keys = listOf("message", "description", "desc", "body", "text", "content")
        for (k in keys) {
            (userData?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        for (k in keys) {
            (configData?.get(k) as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return "Select an option to proceed."
    }

    private fun extractOptions(configData: Map<String, Any>?, userData: Map<String, Any>?, flagId: String): List<GateOption> {
        val optionKeys = listOf("options", "buttons", "choices", "actions")
        var rawOptions: Any? = null
        for (k in optionKeys) {
            if (userData?.containsKey(k) == true) {
                rawOptions = userData[k]
                break
            }
        }
        if (rawOptions == null) {
            for (k in optionKeys) {
                if (configData?.containsKey(k) == true) {
                    rawOptions = configData[k]
                    break
                }
            }
        }

        val optionMaps = mutableListOf<Map<String, Any>>()
        if (rawOptions is List<*>) {
            rawOptions.filterIsInstance<Map<String, Any>>().forEach { optionMaps.add(it) }
        } else if (rawOptions is Map<*, *>) {
            rawOptions.values.filterIsInstance<Map<String, Any>>().forEach { optionMaps.add(it) }
        }

        val extracted = optionMaps.mapNotNull { optMap ->
            val textKeys = listOf("buttonText", "text", "label", "btnText", "title", "name")
            val actionKeys = listOf("action", "act", "id", "key", "url", "link")

            var btnText = ""
            for (tk in textKeys) {
                val v = optMap[tk] as? String
                if (!v.isNullOrBlank()) {
                    btnText = v
                    break
                }
            }
            if (btnText.isBlank()) btnText = "Continue"

            var act = ""
            for (ak in actionKeys) {
                val v = optMap[ak] as? String
                if (!v.isNullOrBlank()) {
                    act = v
                    break
                }
            }

            GateOption(buttonText = btnText, action = act)
        }

        if (extracted.isNotEmpty()) {
            return extracted
        }

        return listOf(
            GateOption(buttonText = "Option A", action = "act_x1"),
            GateOption(buttonText = "Option B", action = "act_x2")
        )
    }

    private fun showGateBottomSheet(activity: FragmentActivity, flag: GateFlag) {
        try {
            if (activity.isFinishing || activity.isDestroyed) return

            // Dismiss old if switching flags
            if (currentBottomSheet != null && currentlyDisplayedFlagId != flag.flagId) {
                currentBottomSheet?.dismissAllowingStateLoss()
            }

            val fragmentManager = activity.supportFragmentManager
            val existing = fragmentManager.findFragmentByTag(GateBottomSheet.TAG) as? GateBottomSheet

            if (existing != null && existing.getFlagId() == flag.flagId) {
                currentBottomSheet = existing
                existing.updateFlag(flag)
                currentlyDisplayedFlagId = flag.flagId
                return
            }

            val bottomSheet = GateBottomSheet.newInstance(flag)
            currentBottomSheet = bottomSheet
            currentlyDisplayedFlagId = flag.flagId

            bottomSheet.show(fragmentManager, GateBottomSheet.TAG)
            _isGateActive.value = true
            Log.d(TAG, "Displayed GateBottomSheet for flag: ${flag.flagId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying GateBottomSheet: ${e.message}", e)
        }
    }

    fun onBottomSheetDismissed() {
        currentBottomSheet = null
        currentlyDisplayedFlagId = null
        _isGateActive.value = false
    }

    private fun dismissCurrentGate(activity: FragmentActivity) {
        try {
            currentBottomSheet?.dismissAllowingStateLoss()
            currentBottomSheet = null
            currentlyDisplayedFlagId = null
            _isGateActive.value = false

            val fragmentManager = activity.supportFragmentManager
            val existing = fragmentManager.findFragmentByTag(GateBottomSheet.TAG)
            if (existing is GateBottomSheet) {
                existing.dismissAllowingStateLoss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing GateBottomSheet: ${e.message}", e)
        }
    }
}
