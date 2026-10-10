package com.example.engine

import com.example.data.model.ExecutionState
import com.example.data.model.ExecutionStepResult
import com.example.data.model.FlowNode
import com.example.data.model.UssdFlow

data class UssdScreenPrompt(
    val title: String,
    val message: String,
    val enteredInput: String = "",
    val isFinalScreen: Boolean = false
)

data class ExecutionEngineState(
    val flow: UssdFlow? = null,
    val state: ExecutionState = ExecutionState.IDLE,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentActiveNode: FlowNode? = null,
    val currentScreenPrompt: UssdScreenPrompt? = null,
    val stepResults: List<ExecutionStepResult> = emptyList(),
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null
)
