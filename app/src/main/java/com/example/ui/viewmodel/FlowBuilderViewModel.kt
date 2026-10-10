package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.data.model.FlowCategory
import com.example.data.model.FlowNode
import com.example.data.model.NodeType
import com.example.data.model.SecurityLevel
import com.example.data.model.UssdFlow
import com.example.data.model.ensureVariableKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class BuilderUiState(
    val currentFlow: UssdFlow = UssdFlow(
        id = UUID.randomUUID().toString(),
        name = "New Automated Flow",
        description = "Custom USSD workflow",
        entryUssdCode = "*170#",
        category = FlowCategory.MOBILE_MONEY,
        securityLevel = SecurityLevel.STANDARD,
        nodes = listOf(
            FlowNode("start_1", NodeType.START, "Start Flow", positionX = 100f, positionY = 100f, nextNodeId = "dial_1"),
            FlowNode("dial_1", NodeType.USSD_DIAL, "Dial *170#", ussdCode = "*170#", positionX = 100f, positionY = 220f, nextNodeId = "end_1"),
            FlowNode("end_1", NodeType.END, "End Session", positionX = 100f, positionY = 340f)
        )
    ).ensureVariableKeys(),
    val selectedNodeId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isAutoSaved: Boolean = true,
    val zoomScale: Float = 1.0f,
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val validationError: String? = null
)

class FlowBuilderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BuilderUiState())
    val uiState: StateFlow<BuilderUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<List<FlowNode>>()
    private val redoStack = mutableListOf<List<FlowNode>>()

    fun initFlow(flow: UssdFlow?) {
        if (flow != null) {
            _uiState.value = BuilderUiState(currentFlow = flow.ensureVariableKeys())
            undoStack.clear()
            redoStack.clear()
        }
    }

    private fun pushUndoState(nodes: List<FlowNode>) {
        undoStack.add(nodes)
        redoStack.clear()
        _uiState.value = _uiState.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = false,
            isAutoSaved = false
        )
    }

    fun selectNode(nodeId: String?) {
        _uiState.value = _uiState.value.copy(selectedNodeId = nodeId)
    }

    fun addNode(type: NodeType) {
        val currentNodes = _uiState.value.currentFlow.nodes
        pushUndoState(currentNodes)

        val newNodeId = "node_" + UUID.randomUUID().toString().take(6)
        val lastNode = currentNodes.lastOrNull()
        val posY = (lastNode?.positionY ?: 100f) + 120f

        val newNode = FlowNode(
            id = newNodeId,
            type = type,
            title = type.title,
            ussdCode = if (type == NodeType.USSD_DIAL) "*100#" else "",
            positionX = 100f,
            positionY = posY,
            variableKey = "" // Defer variableKey assignment to save/confirm time
        )

        val updatedNodes = currentNodes.toMutableList()
        // Link previous end/last node if applicable
        if (updatedNodes.isNotEmpty() && updatedNodes.last().type != NodeType.END) {
            val last = updatedNodes.removeAt(updatedNodes.size - 1)
            updatedNodes.add(last.copy(nextNodeId = newNodeId))
        }
        updatedNodes.add(newNode)

        val updatedFlow = _uiState.value.currentFlow.copy(
            nodes = updatedNodes,
            lastModified = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            currentFlow = updatedFlow,
            selectedNodeId = newNodeId
        )
    }

    fun updateSelectedNode(
        title: String,
        ussdCode: String,
        optionIndex: String,
        inputValue: String,
        isSensitive: Boolean,
        waitSec: Int,
        condition: String,
        stepInputMode: com.example.data.model.StepInputMode = com.example.data.model.StepInputMode.AUTOMATIC,
        variableName: String = "",
        variableType: com.example.data.model.VariableType = com.example.data.model.VariableType.NUMBER,
        manualWaitSeconds: Int = 40
    ) {
        val selectedId = _uiState.value.selectedNodeId ?: return
        val currentNodes = _uiState.value.currentFlow.nodes
        val selectedNode = currentNodes.find { it.id == selectedId } ?: return

        pushUndoState(currentNodes)

        val normVarName = variableName.trim().lowercase()
        val keyToUse = if (stepInputMode == com.example.data.model.StepInputMode.INPUT || variableName.isNotBlank()) {
            if (normVarName.isNotBlank()) {
                val matchingNode = currentNodes.firstOrNull { other ->
                    other.id != selectedId &&
                    (other.stepInputMode == com.example.data.model.StepInputMode.INPUT || other.variableName.isNotBlank()) &&
                    other.variableName.trim().lowercase() == normVarName
                }
                if (matchingNode != null && matchingNode.variableKey.isNotBlank()) {
                    matchingNode.variableKey
                } else if (selectedNode.variableName.trim().lowercase() == normVarName && selectedNode.variableKey.isNotBlank()) {
                    selectedNode.variableKey
                } else {
                    "var_${UUID.randomUUID().toString().take(8)}"
                }
            } else {
                if (selectedNode.variableKey.isNotBlank()) selectedNode.variableKey else "var_${UUID.randomUUID().toString().take(8)}"
            }
        } else {
            ""
        }

        val updatedNodes = currentNodes.map { node ->
            if (node.id == selectedId) {
                node.copy(
                    title = title,
                    ussdCode = ussdCode,
                    optionIndex = optionIndex,
                    inputValue = inputValue,
                    isSensitive = isSensitive,
                    waitDurationSeconds = waitSec,
                    conditionRegex = condition,
                    stepInputMode = stepInputMode,
                    variableName = variableName,
                    variableType = variableType,
                    manualWaitSeconds = manualWaitSeconds,
                    variableKey = keyToUse
                )
            } else node
        }

        val newEntryCode = if (selectedNode?.type == NodeType.USSD_DIAL && ussdCode.isNotBlank()) ussdCode else _uiState.value.currentFlow.entryUssdCode

        val updatedFlow = _uiState.value.currentFlow.copy(
            entryUssdCode = newEntryCode,
            nodes = updatedNodes,
            lastModified = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(currentFlow = updatedFlow)
    }

    fun deleteNode(nodeId: String) {
        val currentNodes = _uiState.value.currentFlow.nodes
        if (currentNodes.size <= 1) return // Keep at least one node

        pushUndoState(currentNodes)

        val updatedNodes = currentNodes.filterNot { it.id == nodeId }
        val updatedFlow = _uiState.value.currentFlow.copy(
            nodes = updatedNodes,
            lastModified = System.currentTimeMillis()
        )

        _uiState.value = _uiState.value.copy(
            currentFlow = updatedFlow,
            selectedNodeId = if (_uiState.value.selectedNodeId == nodeId) null else _uiState.value.selectedNodeId
        )
    }

    fun moveNodeUp(nodeId: String) {
        val currentNodes = _uiState.value.currentFlow.nodes.toMutableList()
        val index = currentNodes.indexOfFirst { it.id == nodeId }
        if (index > 0) {
            pushUndoState(currentNodes)
            val item = currentNodes.removeAt(index)
            currentNodes.add(index - 1, item)
            _uiState.value = _uiState.value.copy(
                currentFlow = _uiState.value.currentFlow.copy(nodes = currentNodes)
            )
        }
    }

    fun moveNodeDown(nodeId: String) {
        val currentNodes = _uiState.value.currentFlow.nodes.toMutableList()
        val index = currentNodes.indexOfFirst { it.id == nodeId }
        if (index >= 0 && index < currentNodes.size - 1) {
            pushUndoState(currentNodes)
            val item = currentNodes.removeAt(index)
            currentNodes.add(index + 1, item)
            _uiState.value = _uiState.value.copy(
                currentFlow = _uiState.value.currentFlow.copy(nodes = currentNodes)
            )
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_uiState.value.currentFlow.nodes)
            _uiState.value = _uiState.value.copy(
                currentFlow = _uiState.value.currentFlow.copy(nodes = previous),
                canUndo = undoStack.isNotEmpty(),
                canRedo = true
            )
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_uiState.value.currentFlow.nodes)
            _uiState.value = _uiState.value.copy(
                currentFlow = _uiState.value.currentFlow.copy(nodes = next),
                canUndo = true,
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun updateFlowName(name: String) {
        val updatedFlow = _uiState.value.currentFlow.copy(
            name = name,
            lastModified = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(currentFlow = updatedFlow)
    }

    fun updateEntryUssdCode(ussd: String) {
        val currentNodes = _uiState.value.currentFlow.nodes.map { node ->
            if (node.type == NodeType.USSD_DIAL) {
                node.copy(ussdCode = ussd, title = "Dial $ussd")
            } else node
        }
        val updatedFlow = _uiState.value.currentFlow.copy(
            entryUssdCode = ussd,
            nodes = currentNodes,
            lastModified = System.currentTimeMillis()
        )
        _uiState.value = _uiState.value.copy(currentFlow = updatedFlow)
    }

    fun updateFlowMetadata(name: String, desc: String, ussd: String, category: FlowCategory, security: SecurityLevel) {
        _uiState.value = _uiState.value.copy(
            currentFlow = _uiState.value.currentFlow.copy(
                name = name,
                description = desc,
                entryUssdCode = ussd,
                category = category,
                securityLevel = security,
                lastModified = System.currentTimeMillis()
            )
        )
    }

    fun updatePanAndZoom(scaleDelta: Float, panX: Float, panY: Float) {
        val newScale = (_uiState.value.zoomScale * scaleDelta).coerceIn(0.5f, 2.0f)
        _uiState.value = _uiState.value.copy(
            zoomScale = newScale,
            panOffsetX = _uiState.value.panOffsetX + panX,
            panOffsetY = _uiState.value.panOffsetY + panY
        )
    }

    fun resetCanvasView() {
        _uiState.value = _uiState.value.copy(zoomScale = 1.0f, panOffsetX = 0f, panOffsetY = 0f)
    }

    fun validateFlow(): Boolean {
        val nodes = _uiState.value.currentFlow.nodes
        if (nodes.isEmpty()) {
            _uiState.value = _uiState.value.copy(validationError = "Flow has no nodes.")
            return false
        }
        val hasStart = nodes.any { it.type == NodeType.START }
        if (!hasStart) {
            _uiState.value = _uiState.value.copy(validationError = "Flow missing Start Node.")
            return false
        }
        _uiState.value = _uiState.value.copy(validationError = null)
        return true
    }
}
