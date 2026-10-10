package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.EngineState
import com.example.data.model.ExecutionLog
import com.example.data.model.ExecutionState
import com.example.data.model.ExecutionStepResult
import com.example.data.model.NodeType
import com.example.data.model.UssdFlow
import com.example.data.model.toFlowPayload
import com.example.data.repository.FlowRepository
import com.example.engine.ExecutionEngineState
import com.example.engine.UssdFlowEngine
import com.example.engine.UssdScreenPrompt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExecutionViewModel(application: Application) : AndroidViewModel(application) {

    private val flowEngine = UssdFlowEngine(application.applicationContext)

    val flowStatus = flowEngine.status

    val engineState: StateFlow<ExecutionEngineState> = flowStatus.map { status ->
        val mappedState = when (status.state) {
            EngineState.IDLE -> ExecutionState.IDLE
            EngineState.DIALING, EngineState.WAITING_FOR_CALL_OR_USSD, EngineState.WAITING_FOR_USSD_DIALOG, EngineState.PROCESSING_USSD, EngineState.WAITING_FOR_PROMPT, EngineState.EXECUTING_STEP -> ExecutionState.RUNNING
            EngineState.WAITING_FOR_MANUAL -> ExecutionState.PAUSED
            EngineState.DONE -> ExecutionState.COMPLETED
            EngineState.ERROR -> ExecutionState.FAILED
        }

        val prompt = if (status.currentPromptText.isNotBlank()) {
            UssdScreenPrompt(
                title = status.message,
                message = status.currentPromptText,
                isFinalScreen = status.state == EngineState.DONE
            )
        } else null

        val stepResults = if (status.currentStepIndex > 0) {
            (1..status.currentStepIndex).map { stepIdx ->
                ExecutionStepResult(
                    stepIndex = stepIdx,
                    nodeTitle = "Step $stepIdx",
                    nodeType = NodeType.INPUT_VALUE,
                    sentPayload = "",
                    receivedResponse = status.currentPromptText,
                    status = if (stepIdx < status.currentStepIndex || status.state == EngineState.DONE) ExecutionState.COMPLETED else ExecutionState.RUNNING
                )
            }
        } else emptyList()

        ExecutionEngineState(
            state = mappedState,
            currentStepIndex = status.currentStepIndex,
            totalSteps = status.totalSteps,
            currentScreenPrompt = prompt,
            stepResults = stepResults,
            logs = status.logs,
            errorMessage = status.errorMessage
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExecutionEngineState())

    fun startFlowExecution(
        flow: UssdFlow,
        userInputs: Map<String, String> = emptyMap(),
        repository: FlowRepository? = null,
        simSlot: Int = 1,
        automationDelayMs: Long = 50L,
        timeoutSeconds: Int = 40
    ) {
        viewModelScope.launch {
            val payload = flow.toFlowPayload(
                userInputs = userInputs,
                simSlot = simSlot,
                timeoutSeconds = timeoutSeconds,
                automationDelayMs = automationDelayMs
            )
            val success = flowEngine.start(payload)
            if (success && repository != null) {
                val log = ExecutionLog(
                    id = java.util.UUID.randomUUID().toString(),
                    flowId = flow.id,
                    flowName = flow.name,
                    durationMs = 3000L,
                    status = ExecutionState.COMPLETED,
                    simSlot = simSlot
                )
                repository.recordExecutionLog(log)
            }
        }
    }

    fun pause() {
        // Pausing manual wait
    }

    fun resume() {
        // Resuming manual step
    }

    fun stop() {
        flowEngine.stop()
    }
}
