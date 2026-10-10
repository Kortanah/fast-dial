package com.example.data.model

enum class StepType {
    AUTO,
    INPUT,
    MANUAL
}

data class Step(
    val type: StepType = StepType.AUTO,
    val actionValue: String? = null,
    val variableKey: String? = null,
    val waitTime: Long? = 40000L
)

data class FlowPayload(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "USSD Flow",
    val ussdCode: String = "*170#",
    val steps: List<Step> = emptyList(),
    val userInputs: Map<String, String> = emptyMap(),
    val simSlot: Int = 1,
    val timeoutSeconds: Int = 40,
    val automationDelayMs: Long = 50L
)

enum class EngineState {
    IDLE,
    DIALING,
    WAITING_FOR_CALL_OR_USSD,
    WAITING_FOR_USSD_DIALOG,
    PROCESSING_USSD,
    WAITING_FOR_PROMPT,
    EXECUTING_STEP,
    WAITING_FOR_MANUAL,
    DONE,
    ERROR
}

data class FlowEngineStatus(
    val state: EngineState = EngineState.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentPromptText: String = "",
    val message: String = "Engine Idle",
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)

fun UssdFlow.toFlowPayload(
    userInputs: Map<String, String> = emptyMap(),
    simSlot: Int = 1,
    timeoutSeconds: Int = 40,
    automationDelayMs: Long = 50L
): FlowPayload {
    val flow = this.ensureVariableKeys()
    val stepsList = mutableListOf<Step>()

    for (node in flow.nodes) {
        val normKey = node.normalizedVariableKey
        val typedInput = (userInputs[node.id]
            ?: userInputs[normKey]
            ?: userInputs[node.variableName]
            ?: userInputs[node.title]
            ?: userInputs.entries.firstOrNull { it.key.equals(normKey, ignoreCase = true) }?.value
            ?: userInputs.entries.firstOrNull { it.key.equals(node.variableName, ignoreCase = true) }?.value
            ?: userInputs.entries.firstOrNull { it.key.equals(node.title, ignoreCase = true) }?.value
            ?: node.inputValue).trim()

        when (node.stepInputMode) {
            StepInputMode.MANUAL -> {
                stepsList.add(
                    Step(
                        type = StepType.MANUAL,
                        waitTime = (if (node.manualWaitSeconds > 0) node.manualWaitSeconds else 40) * 1000L
                    )
                )
            }
            StepInputMode.INPUT -> {
                stepsList.add(
                    Step(
                        type = StepType.INPUT,
                        variableKey = normKey,
                        actionValue = typedInput.ifBlank { node.inputValue }
                    )
                )
            }
            StepInputMode.AUTOMATIC -> {
                val actionVal = when (node.type) {
                    NodeType.START -> null // Skip sending input for start node
                    NodeType.USSD_DIAL -> null // Initial dial handled in entryUssdCode
                    NodeType.MENU_SELECTION -> node.optionIndex.ifBlank { "1" }
                    NodeType.INPUT_VALUE -> typedInput.ifBlank { node.inputValue }
                    NodeType.WAIT -> null
                    NodeType.DECISION -> null
                    NodeType.END -> null
                }
                if (actionVal != null) {
                    stepsList.add(
                        Step(
                            type = StepType.AUTO,
                            actionValue = actionVal
                        )
                    )
                }
            }
        }
    }

    return FlowPayload(
        id = this.id,
        name = this.name,
        ussdCode = this.entryUssdCode.ifBlank { "*170#" },
        steps = stepsList,
        userInputs = userInputs,
        simSlot = simSlot,
        timeoutSeconds = timeoutSeconds,
        automationDelayMs = automationDelayMs
    )
}
