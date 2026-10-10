package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface UssdAccessibilityBridge {
    fun onScreenTextChanged(promptText: String, hasInputField: Boolean)
    fun onSendButtonClicked()
    fun onDialogDisappeared()
    fun onSimPickerDetected() {}
    fun onUssdDialogDetected(promptText: String, hasInputField: Boolean) {}
}

class UssdAccessibilityService : AccessibilityService() {

    companion object {
        var bridge: UssdAccessibilityBridge? = null
        var instance: UssdAccessibilityService? = null
        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
        var appContextRef: android.content.Context? = null

        fun refreshServiceState(context: Context): Boolean {
            val active = instance != null || isAccessibilityServiceEnabled(context)
            _isServiceActive.value = active
            return active
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
                val expectedComponentName = android.content.ComponentName(context, UssdAccessibilityService::class.java)
                val enabledServicesSetting = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServicesSetting)
                while (colonSplitter.hasNext()) {
                    val componentNameString = colonSplitter.next()
                    val enabledComponent = android.content.ComponentName.unflattenFromString(componentNameString)
                    if (enabledComponent != null && enabledComponent == expectedComponentName) {
                        return true
                    }
                }
                false
            } catch (e: Exception) {
                false
            }
        }

        private val IGNORED_PACKAGES = setOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.settings",
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice",
            "com.sec.android.app.launcher",
            "com.samsung.systemui"
        )

        private val SIM_PICKER_KEYWORDS = listOf(
            "sim 1", "sim 2", "select sim", "choose sim", "choose a sim",
            "select a sim", "sim selection", "card 1", "card 2",
            "sélectionner sim", "choisir sim", "choix sim"
        )

        private val TRANSIENT_KEYWORDS = listOf(
            "running", "loading", "please wait", "processing", "connecting", "initiating",
            "patienter", "cours", "chargement", "traitement", "connexion", "initialisation", "patientez", "attente"
        )

        private val SEND_KEYWORDS = listOf(
            "send", "ok", "proceed", "submit", "reply", "confirm", "yes", "accept", "next", "continue",
            "envoyer", "répondre", "repondre", "continuer", "suivant", "valider", "confirmer", "oui", "accepter", "soumettre"
        )

        private val SEND_VIEW_ID_KEYWORDS = listOf(
            "button1", "button2", "positive", "ok", "send"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastExtractedPrompt: String = ""

    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("USSD_SERVICE", "Accessibility service created")
        appContextRef = applicationContext
        refreshServiceState(applicationContext)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("USSD_SERVICE", "Accessibility service connected")
        instance = this
        _isServiceActive.value = true
        appContextRef = applicationContext
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        android.util.Log.d("USSD_SERVICE", "Accessibility service unbound")
        instance = null
        _isServiceActive.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        android.util.Log.d("USSD_SERVICE", "Accessibility service destroyed")
        instance = null
        _isServiceActive.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        android.util.Log.d("USSD_SERVICE", "Accessibility service interrupted")
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        android.util.Log.d("USSD_SERVICE", "Task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event == null) return

            val pkgName = event.packageName?.toString() ?: return
            if (pkgName == packageName || IGNORED_PACKAGES.contains(pkgName)) {
                return
            }

            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                    evaluateScreen()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("USSD_SERVICE", "Error in onAccessibilityEvent: ${e.message}")
        }
    }

    /**
     * Finds active root node across active window and candidate application/system windows.
     * Rejects accessibility overlays, keyboards, and explicitly ignored packages.
     */
    fun getDialogRoot(): AccessibilityNodeInfo? {
        // 1. Primary check: rootInActiveWindow (returns active foreground window)
        val activeRoot = rootInActiveWindow
        if (activeRoot != null) {
            val pkg = activeRoot.packageName?.toString() ?: ""
            if (pkg.isNotBlank() && pkg != packageName && !IGNORED_PACKAGES.contains(pkg)) {
                return activeRoot
            }
        }

        // 2. Secondary check: inspect interactive windows list
        val winList = try { windows } catch (e: Exception) { emptyList() }

        // Prefer active or focused TYPE_APPLICATION or TYPE_SYSTEM window first
        for (window in winList) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                continue
            }
            if (window.isActive || window.isFocused) {
                val winRoot = window.root ?: continue
                val winPkg = winRoot.packageName?.toString() ?: ""
                if (winPkg.isNotBlank() && winPkg != packageName && !IGNORED_PACKAGES.contains(winPkg)) {
                    return winRoot
                }
            }
        }

        // Fallback: check any non-overlay, non-keyboard window root
        for (window in winList) {
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY ||
                window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                continue
            }
            val winRoot = window.root ?: continue
            val winPkg = winRoot.packageName?.toString() ?: ""
            if (winPkg.isNotBlank() && winPkg != packageName && !IGNORED_PACKAGES.contains(winPkg)) {
                return winRoot
            }
        }

        return null
    }

    /**
     * Checks if the active window belongs to a supported telephony/dialer process.
     */
    fun isSupportedTelephonyProcess(): Boolean {
        val root = getDialogRoot() ?: return false
        val pkgName = root.packageName?.toString()?.lowercase() ?: ""
        if (pkgName.isBlank()) return false
        if (pkgName == packageName.lowercase() || IGNORED_PACKAGES.contains(pkgName)) return false
        return true
    }

    /**
     * Checks if active screen is a SIM selection picker dialog.
     */
    fun isSimPickerVisible(): Boolean {
        val root = getDialogRoot() ?: return false
        return checkNodeForSimPicker(root)
    }

    private fun checkNodeForSimPicker(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (SIM_PICKER_KEYWORDS.any { text.contains(it) || desc.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (checkNodeForSimPicker(node.getChild(i))) return true
        }
        return false
    }

    /**
     * Verifies that a valid USSD dialog exists, is visible, contains actionable fields/buttons,
     * and belongs to a supported process. Used for initial dialog detection.
     */
    fun isValidUssdDialogVisible(): Boolean {
        val root = getDialogRoot() ?: return false
        if (!isSupportedTelephonyProcess()) return false
        if (isSimPickerVisible()) return false

        val hasEditable = hasActionableField(root)
        val sendButton = findSendButtonNode(root)
        val promptText = extractPromptText(root)

        return promptText.isNotBlank() && (hasEditable || sendButton != null)
    }

    /**
     * Stricter check requiring an editable field to be present, used when preparing
     * for text injection in AUTO/INPUT steps.
     */
    fun isValidUssdInputDialog(): Boolean {
        val root = getDialogRoot() ?: return false
        if (!isSupportedTelephonyProcess()) return false
        if (isSimPickerVisible()) return false

        val hasEditable = hasActionableField(root)
        val promptText = extractPromptText(root)

        return promptText.isNotBlank() && hasEditable
    }

    /**
     * Evaluates current active dialog screen, checks for input field, extracts prompt text,
     * and reports to bridge.
     */
    fun evaluateScreen() {
        val root = getDialogRoot()
        if (root == null) {
            bridge?.onDialogDisappeared()
            return
        }

        if (isSimPickerVisible()) {
            bridge?.onSimPickerDetected()
            return
        }

        if (!isValidUssdDialogVisible()) {
            return
        }

        val hasInputField = hasActionableField(root)
        val promptText = extractPromptText(root)

        if (promptText.isBlank()) return

        val isTransient = TRANSIENT_KEYWORDS.any { promptText.contains(it, ignoreCase = true) }
        if (isTransient) return

        lastExtractedPrompt = promptText
        bridge?.onUssdDialogDetected(promptText, hasInputField)
        bridge?.onScreenTextChanged(promptText, hasInputField)
    }

    /**
     * Checks if dialog root contains an editable text field.
     */
    fun hasActionableField(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (hasActionableField(node.getChild(i))) return true
        }
        return false
    }

    /**
     * Finds the editable field node.
     */
    fun findEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val childMatch = findEditableNode(node.getChild(i))
            if (childMatch != null) return childMatch
        }
        return null
    }

    /**
     * Recursively walks tree collecting text, excluding editable fields so typed input isn't echoed.
     * Deduplicates text when a node's text/description is identical to text collected from descendants.
     */
    fun extractPromptText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val isEditableField = node.isEditable || node.className?.toString()?.contains("EditText", ignoreCase = true) == true

        var ownText = ""
        if (!isEditableField) {
            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            ownText = when {
                text.isNotBlank() && desc.isNotBlank() -> {
                    if (text.equals(desc, ignoreCase = true) || text.contains(desc, ignoreCase = true)) text
                    else if (desc.contains(text, ignoreCase = true)) desc
                    else "$text\n$desc"
                }
                text.isNotBlank() -> text
                desc.isNotBlank() -> desc
                else -> ""
            }
        }

        val childTexts = mutableListOf<String>()
        for (i in 0 until node.childCount) {
            val childText = extractPromptText(node.getChild(i))
            if (childText.isNotBlank()) {
                childTexts.add(childText)
            }
        }

        val combinedChildren = childTexts.joinToString("\n").trim()

        if (ownText.isBlank()) return combinedChildren
        if (combinedChildren.isBlank()) return ownText

        if (ownText.equals(combinedChildren, ignoreCase = true) || combinedChildren.contains(ownText, ignoreCase = true)) {
            return combinedChildren
        }
        if (ownText.contains(combinedChildren, ignoreCase = true)) {
            return ownText
        }

        val childLines = combinedChildren.split("\n").map { it.trim() }
        if (childLines.any { it.equals(ownText, ignoreCase = true) }) {
            return combinedChildren
        }

        return "$ownText\n$combinedChildren".trim()
    }

    /**
     * Injects text into editable field using ACTION_SET_TEXT with clipboard+paste fallback.
     * Uses a retry loop (~20ms up to 20 attempts).
     */
    suspend fun injectText(textToSend: String): Boolean {
        for (attempt in 1..20) {
            val root = getDialogRoot()
            val editableNode = findEditableNode(root)
            if (editableNode != null) {
                // Primary Method: ACTION_SET_TEXT
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToSend)
                }
                val setSuccess = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                if (setSuccess) {
                    return true
                }

                // Fallback: Clipboard + ACTION_PASTE
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("USSD_INPUT", textToSend)
                    clipboard.setPrimaryClip(clip)

                    editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    val pasteSuccess = editableNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (pasteSuccess) {
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            delay(25)
        }
        return false
    }

    /**
     * Searches node tree (and walks up) for a clickable node matching Send/OK keywords or view IDs.
     */
    suspend fun clickSendButton(): Boolean {
        for (attempt in 1..20) {
            val root = getDialogRoot()
            if (root != null) {
                val sendNode = findSendButtonNode(root)
                if (sendNode != null) {
                    var target: AccessibilityNodeInfo? = sendNode
                    while (target != null && !target.isClickable) {
                        target = target.parent
                    }
                    val clicked = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                    if (clicked) {
                        bridge?.onSendButtonClicked()
                        return true
                    }
                }
            }
            delay(25)
        }
        return false
    }

    private fun findSendButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isSendMatch = SEND_KEYWORDS.any { keyword ->
            text == keyword || desc == keyword || text.startsWith(keyword)
        } || SEND_VIEW_ID_KEYWORDS.any { idKw ->
            viewId.contains(idKw)
        }

        if (isSendMatch && (node.isClickable || node.parent?.isClickable == true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val match = findSendButtonNode(node.getChild(i))
            if (match != null) return match
        }
        return null
    }
}
