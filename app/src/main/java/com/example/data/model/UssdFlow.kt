package com.example.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

enum class FlowCategory(@StringRes val nameRes: Int, val fallbackName: String) {
    MOBILE_MONEY(R.string.category_mobile_money, "Mobile Money"),
    BANK(R.string.category_bank, "Bank"),
    AIRTIME(R.string.category_airtime, "Airtime"),
    DATA(R.string.category_data, "Data Bundles"),
    UTILITIES(R.string.category_utilities, "Utilities"),
    CUSTOM(R.string.category_custom, "Custom");

    val displayName: String
        @Composable
        get() = stringResource(nameRes)

    @Composable
    fun getLocalizedName(): String = stringResource(nameRes)
}

enum class FlowStatus {
    ACTIVE,
    DRAFT,
    ARCHIVED
}

enum class SecurityLevel(val label: String, val description: String) {
    STANDARD("Standard", "Normal flow execution"),
    PIN_PROTECTED("PIN Protected", "Requires PIN before execution"),
    VAULT_ENCRYPTED("Vault Encrypted", "256-bit AES encrypted sensitive nodes")
}

data class UssdFlow(
    val id: String,
    val name: String,
    val description: String,
    val entryUssdCode: String,
    val category: FlowCategory,
    val country: String = "Global",
    val status: FlowStatus = FlowStatus.ACTIVE,
    val securityLevel: SecurityLevel = SecurityLevel.STANDARD,
    val lastModified: Long = System.currentTimeMillis(),
    val lastModifiedDate: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(if (lastModified > 0) lastModified else System.currentTimeMillis())),
    val executionCount: Int = 0,
    val successfulCount: Int = 0,
    val failedCount: Int = 0,
    val nodes: List<FlowNode> = emptyList(),
    val isTemplate: Boolean = false,
    val userRole: String = "",
    val network: String = "",
    val icon: String = "",
    @com.squareup.moshi.Json(name = "grid_btn") val gridBtn: Boolean = false
) {
    fun getFormattedLastModified(): String {
        if (lastModifiedDate.isNotBlank()) return lastModifiedDate
        val date = java.util.Date(if (lastModified > 0) lastModified else System.currentTimeMillis())
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(date)
    }

    fun getLocalizedName(language: String = ""): String {
        return com.example.util.FlowTranslator.translate(name, language)
    }

    fun getLocalizedDescription(language: String = ""): String {
        return com.example.util.FlowTranslator.translate(description, language)
    }
}

enum class ExecutionState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class ExecutionStepResult(
    val stepIndex: Int,
    val nodeTitle: String,
    val nodeType: NodeType,
    val sentPayload: String,
    val receivedResponse: String,
    val status: ExecutionState,
    val timestamp: Long = System.currentTimeMillis()
)

data class ExecutionLog(
    val id: String,
    val flowId: String,
    val flowName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val status: ExecutionState,
    val simSlot: Int = 1,
    val errorMessage: String? = null,
    val stepResults: List<ExecutionStepResult> = emptyList()
)

data class UserSettings(
    val isDarkModeEnabled: Boolean = true,
    val useSystemTheme: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isPinLockEnabled: Boolean = false,
    val pinHash: String = "",
    val autoLockTimeoutMinutes: Int = 5,
    val defaultTimeoutSeconds: Int = 40,
    val retryAttempts: Int = 1,
    val automationDelayMs: Long = 50L,
    val simSlot: Int = 1,
    val autoVaultSensitiveNodes: Boolean = true,
    val isAccessibilityPermissionEnabled: Boolean = true,
    val isContactsPermissionEnabled: Boolean = true,
    val isAccountDeleted: Boolean = false,
    val language: String = "English",
    val userCountry: String = "Ghana",
    val userRole: String = "Default",
    val selectedNetwork: String = ""
)

fun UssdFlow.ensureVariableKeys(): UssdFlow {
    if (nodes.isEmpty()) return this

    val keyMap = mutableMapOf<String, String>()
    for (node in nodes) {
        val normVarName = node.variableName.trim().lowercase()
        if (normVarName.isNotBlank() && node.variableKey.isNotBlank()) {
            keyMap.putIfAbsent(normVarName, node.variableKey)
        }
    }

    val updatedNodes = nodes.map { node ->
        val normVarName = node.variableName.trim().lowercase()
        if (normVarName.isNotBlank() || node.stepInputMode == StepInputMode.INPUT) {
            val key = if (normVarName.isNotBlank()) {
                keyMap.getOrPut(normVarName) {
                    if (node.variableKey.isNotBlank()) node.variableKey else "var_${node.id}"
                }
            } else {
                if (node.variableKey.isNotBlank()) node.variableKey else "var_${node.id}"
            }
            node.copy(variableKey = key)
        } else {
            node
        }
    }
    return copy(nodes = updatedNodes)
}

data class SecurityAuditIssue(
    val flowId: String,
    val flowName: String,
    val nodeId: String,
    val issueType: String,
    val recommendation: String,
    val isResolved: Boolean = false
)
