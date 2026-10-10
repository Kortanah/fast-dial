package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

enum class NodeType(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val category: String,
    val title: String,
    val description: String
) {
    START(R.string.node_start_title, R.string.node_start_desc, "Control", "Start Node", "Initiates the USSD workflow execution"),
    USSD_DIAL(R.string.node_ussd_dial_title, R.string.node_ussd_dial_desc, "Action", "USSD Dial", "Dials a USSD code (e.g., *170#)"),
    MENU_SELECTION(R.string.node_menu_selection_title, R.string.node_menu_selection_desc, "Action", "Menu Selection", "Selects an option from USSD menu"),
    INPUT_VALUE(R.string.node_input_value_title, R.string.node_input_value_desc, "Input", "Input Value", "Enters phone number, amount, or PIN"),
    WAIT(R.string.node_wait_title, R.string.node_wait_desc, "Control", "Wait Delay", "Pauses execution for a set duration"),
    DECISION(R.string.node_decision_title, R.string.node_decision_desc, "Logic", "Decision Rule", "Branches flow based on response text"),
    END(R.string.node_end_title, R.string.node_end_desc, "Control", "End Node", "Terminates the USSD session");

    @Composable
    fun getLocalizedTitle(): String = stringResource(titleRes)

    @Composable
    fun getLocalizedDescription(): String = stringResource(descRes)

    fun getIcon(): ImageVector = when (this) {
        START -> Icons.Default.PlayArrow
        USSD_DIAL -> Icons.Default.Call
        MENU_SELECTION -> Icons.AutoMirrored.Filled.List
        INPUT_VALUE -> Icons.Default.EditNote
        WAIT -> Icons.Default.HourglassTop
        DECISION -> Icons.AutoMirrored.Filled.AltRoute
        END -> Icons.Default.Stop
    }
}

enum class StepInputMode(@StringRes val nameRes: Int, @StringRes val descRes: Int, val fallbackName: String, val fallbackDesc: String) {
    AUTOMATIC(R.string.mode_automatic, R.string.mode_automatic_desc, "Automatic", "Fixed auto-entered value"),
    INPUT(R.string.mode_input, R.string.mode_input_desc, "Input", "Prompt user for input before run"),
    MANUAL(R.string.mode_manual, R.string.mode_manual_desc, "Manual", "Wait for user to manually enter in time");

    val displayName: String
        @Composable
        get() = stringResource(nameRes)

    val description: String
        @Composable
        get() = stringResource(descRes)

    @Composable
    fun getLocalizedName(): String = stringResource(nameRes)

    @Composable
    fun getLocalizedDescription(): String = stringResource(descRes)
}

enum class VariableType(@StringRes val nameRes: Int, val fallbackName: String) {
    NUMBER(R.string.var_number, "Number"),
    TEXT(R.string.var_text, "Letter / Text"),
    PHONE_CONTACT(R.string.var_phone_contact, "Phone Contact");

    val displayName: String
        @Composable
        get() = stringResource(nameRes)

    @Composable
    fun getLocalizedName(): String = stringResource(nameRes)
}

data class FlowNode(
    val id: String,
    val type: NodeType,
    val title: String,
    val ussdCode: String = "",
    val optionIndex: String = "",
    val inputValue: String = "",
    val isSensitive: Boolean = false,
    val waitDurationSeconds: Int = 2,
    val conditionRegex: String = "",
    val nextNodeId: String? = null,
    val alternativeNodeId: String? = null,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val stepInputMode: StepInputMode = StepInputMode.AUTOMATIC,
    val variableName: String = "",
    val variableType: VariableType = VariableType.NUMBER,
    val manualWaitSeconds: Int = 40,
    val variableKey: String = ""
) {
    val normalizedVariableKey: String
        get() = if (variableKey.isNotBlank()) {
            variableKey.trim().lowercase()
        } else {
            variableName.ifBlank { title }.trim().lowercase()
        }

    fun getLocalizedTitle(language: String = ""): String {
        return com.example.util.FlowTranslator.translate(title, language)
    }

    fun getLocalizedVariableName(language: String = ""): String {
        return if (variableName.isNotBlank()) com.example.util.FlowTranslator.translate(variableName, language) else ""
    }
}
