package com.example.ui.screens.builder

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.example.data.model.NodeType
import com.example.data.model.StepInputMode
import com.example.data.model.VariableType
import com.example.ui.components.NodeCard
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.FlowBuilderViewModel
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowBuilderScreen(
    flowId: String?,
    mainViewModel: MainViewModel,
    builderViewModel: FlowBuilderViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by builderViewModel.uiState.collectAsStateWithLifecycle()
    val userSettings by mainViewModel.userSettings.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showPalette by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(flowId) {
        if (!flowId.isNullOrEmpty() && flowId != "new") {
            val flow = mainViewModel.repository.getFlowById(flowId)
            builderViewModel.initFlow(flow)
        }
    }

    val currentFlow = uiState.currentFlow
    val selectedNode = currentFlow.nodes.find { it.id == uiState.selectedNodeId }

    var flowNameField by remember(currentFlow.id) { mutableStateOf(currentFlow.name) }
    var ussdCodeField by remember(currentFlow.id) { mutableStateOf(currentFlow.entryUssdCode) }

    LaunchedEffect(flowNameField) {
        delay(400)
        builderViewModel.updateFlowName(flowNameField)
    }

    LaunchedEffect(ussdCodeField) {
        delay(400)
        builderViewModel.updateEntryUssdCode(ussdCodeField)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentFlow.getLocalizedName(userSettings.language),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Dashboard.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.testTag("flow_builder_info_icon_topbar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "How to Create Flow",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            com.example.util.AdsManager.showRewardedAd(context) { rewardEarned ->
                                if (rewardEarned) {
                                    coroutineScope.launch {
                                        builderViewModel.updateFlowName(flowNameField)
                                        builderViewModel.updateEntryUssdCode(ussdCodeField)
                                        mainViewModel.repository.saveFlow(builderViewModel.uiState.value.currentFlow)
                                        mainViewModel.runSecurityAudit()
                                        android.widget.Toast.makeText(context, "Flow saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        onNavigate(Screen.Dashboard.route)
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Please watch the full ad to save your flow", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("test_run_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.save_flow), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPalette = !showPalette },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_node_fab")
            ) {
                Icon(
                    imageVector = if (showPalette) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Add Node"
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Zoom/Pan Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            builderViewModel.updatePanAndZoom(zoom, pan.x, pan.y)
                        }
                    }
                    .graphicsLayer(
                        scaleX = uiState.zoomScale,
                        scaleY = uiState.zoomScale,
                        translationX = uiState.panOffsetX,
                        translationY = uiState.panOffsetY
                    )
            ) {
                val visibleNodes = currentFlow.nodes.filter { it.type != NodeType.START && it.type != NodeType.END }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.flow_step_sequence),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { showGuideDialog = true },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .testTag("flow_builder_info_icon")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "How to create flow",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.steps_count, visibleNodes.size),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // USSD Flow Details & Entry Code Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = stringResource(R.string.ussd_flow_configuration),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = flowNameField,
                                    onValueChange = { newName ->
                                        flowNameField = newName
                                    },
                                    label = { Text(stringResource(R.string.flow_name_label)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("flow_builder_name_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = ussdCodeField,
                                    onValueChange = { newCode ->
                                        ussdCodeField = newCode
                                    },
                                    label = { Text(stringResource(R.string.ussd_entry_code_readonly)) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("flow_builder_ussd_entry_code_input"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(visibleNodes, key = { _, node -> node.id }) { index, node ->
                        NodeCard(
                            node = node,
                            index = index,
                            isSelected = node.id == uiState.selectedNodeId,
                            isFirstNode = index == 0,
                            showSeparator = index < visibleNodes.size - 1,
                            onSelect = { builderViewModel.selectNode(node.id) },
                            onMoveUp = { builderViewModel.moveNodeUp(node.id) },
                            onMoveDown = { builderViewModel.moveNodeDown(node.id) },
                            onDelete = { builderViewModel.deleteNode(node.id) },
                            language = userSettings.language
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }

            // Bottom Zoom & Viewport Controls (Bottom Right Corner)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 80.dp, bottom = 20.dp)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = { builderViewModel.updatePanAndZoom(1.2f, 0f, 0f) }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                    }
                    IconButton(onClick = { builderViewModel.updatePanAndZoom(0.8f, 0f, 0f) }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                    }
                    IconButton(onClick = { builderViewModel.resetCanvasView() }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Canvas View")
                    }
                }
            }



            // Node Palette Overlay
            AnimatedVisibility(
                visible = showPalette,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.select_node_type),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(listOf(NodeType.MENU_SELECTION, NodeType.WAIT)) { type ->
                                Card(
                                    onClick = {
                                        builderViewModel.addNode(type)
                                        showPalette = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.testTag("add_node_type_${type.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(type.getIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(type.title, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Node Inspector Sheet
            if (selectedNode != null) {
                ModalBottomSheet(
                    onDismissRequest = { builderViewModel.selectNode(null) },
                    sheetState = sheetState
                ) {
                    NodeInspectorContent(
                        node = selectedNode,
                        allNodes = uiState.currentFlow.nodes,
                        onUpdate = { title, ussd, opt, input, sens, waitSec, cond, inputMode, varName, varType, manWaitSec ->
                            builderViewModel.updateSelectedNode(title, ussd, opt, input, sens, waitSec, cond, inputMode, varName, varType, manWaitSec)
                        },
                        onClose = { builderViewModel.selectNode(null) }
                    )
                }
            }

            if (showGuideDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showGuideDialog = false },
                    title = { Text(stringResource(R.string.how_to_build_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.how_to_build_intro), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.how_to_build_step1), style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.how_to_build_step2), style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.how_to_build_step3), style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.how_to_build_step4), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showGuideDialog = false }) {
                            Text(stringResource(R.string.got_it))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeInspectorContent(
    node: com.example.data.model.FlowNode,
    allNodes: List<com.example.data.model.FlowNode> = emptyList(),
    onUpdate: (
        title: String,
        ussdCode: String,
        optionIndex: String,
        inputValue: String,
        isSensitive: Boolean,
        waitSec: Int,
        condition: String,
        inputMode: com.example.data.model.StepInputMode,
        variableName: String,
        variableType: com.example.data.model.VariableType,
        manualWaitSeconds: Int
    ) -> Unit,
    onClose: () -> Unit
) {
    var title by remember(node.id) { mutableStateOf(node.title) }
    var ussdCode by remember(node.id) { mutableStateOf(node.ussdCode) }
    var optionIndex by remember(node.id) { mutableStateOf(node.optionIndex) }
    var inputValue by remember(node.id) { mutableStateOf(node.inputValue) }
    var isSensitive by remember(node.id) { mutableStateOf(node.isSensitive) }
    var waitSec by remember(node.id) { mutableStateOf(node.waitDurationSeconds.toString()) }
    var condition by remember(node.id) { mutableStateOf(node.conditionRegex) }

    var stepInputMode by remember(node.id) { mutableStateOf(node.stepInputMode) }
    var variableName by remember(node.id) { mutableStateOf(node.variableName) }
    var variableType by remember(node.id) { mutableStateOf(node.variableType) }
    var manualWaitSec by remember(node.id) { mutableStateOf(node.manualWaitSeconds.toString()) }

    var modeExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var validationError by remember(node.id) { mutableStateOf<String?>(null) }

    val errNodeTitleBlank = stringResource(R.string.err_node_title_blank)
    val errUssdCodeBlank = stringResource(R.string.err_ussd_code_blank)
    val errOptionIndexBlank = stringResource(R.string.err_option_index_blank)
    val errWaitDurationInteger = stringResource(R.string.err_wait_duration_integer)
    val errVariableNameBlank = stringResource(R.string.err_variable_name_blank)
    val errInputValueBlank = stringResource(R.string.err_input_value_blank)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.node_properties, node.type.title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_inspector))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.node_title)) },
            modifier = Modifier.fillMaxWidth().testTag("node_title_inspector_input"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        when (node.type) {
            NodeType.USSD_DIAL -> {
                OutlinedTextField(
                    value = ussdCode,
                    onValueChange = { ussdCode = it },
                    label = { Text(stringResource(R.string.ussd_dial_code)) },
                    placeholder = { Text("*170#", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth().testTag("node_ussd_inspector_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            NodeType.MENU_SELECTION, NodeType.INPUT_VALUE -> {
                // Step Input Mode Dropdown (Automatic / Input / Manual)
                Text(
                    text = stringResource(R.string.step_input_option),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = !modeExpanded }
                ) {
                    OutlinedTextField(
                        value = stepInputMode.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.input_mode)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .testTag("step_input_mode_dropdown"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        com.example.data.model.StepInputMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.displayName, style = MaterialTheme.typography.titleMedium)
                                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    stepInputMode = mode
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (stepInputMode) {
                    com.example.data.model.StepInputMode.AUTOMATIC -> {
                        if (node.type == NodeType.MENU_SELECTION) {
                            OutlinedTextField(
                                value = optionIndex,
                                onValueChange = { optionIndex = it },
                                label = { Text(stringResource(R.string.menu_option_number)) },
                                placeholder = { Text("value (eg. 1, 2 or text)", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth().testTag("node_option_inspector_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = inputValue,
                                onValueChange = { inputValue = it },
                                label = { Text(stringResource(R.string.input_value_label)) },
                                placeholder = { Text("e.g. 10 or 0241234567", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth().testTag("node_input_inspector_input"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                    com.example.data.model.StepInputMode.INPUT -> {
                        OutlinedTextField(
                            value = variableName,
                            onValueChange = { variableName = it },
                            label = { Text(stringResource(R.string.variable_name_label)) },
                            placeholder = { Text("e.g., Amount or Recipient Number", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("node_variable_name_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = !typeExpanded }
                        ) {
                            OutlinedTextField(
                                value = variableType.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.variable_input_type)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .testTag("variable_type_dropdown"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                com.example.data.model.VariableType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.displayName) },
                                        onClick = {
                                            variableType = type
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    com.example.data.model.StepInputMode.MANUAL -> {
                        OutlinedTextField(
                            value = manualWaitSec,
                            onValueChange = { manualWaitSec = it },
                            label = { Text(stringResource(R.string.manual_entry_timeout)) },
                            placeholder = { Text("10", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().testTag("node_manual_timeout_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
            NodeType.WAIT -> {
                OutlinedTextField(
                    value = waitSec,
                    onValueChange = { waitSec = it },
                    label = { Text(stringResource(R.string.wait_delay_label)) },
                    modifier = Modifier.fillMaxWidth().testTag("node_wait_inspector_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            NodeType.DECISION -> {
                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text(stringResource(R.string.condition_match_regex)) },
                    placeholder = { Text(".*success.*", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth().testTag("node_condition_inspector_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            else -> {}
        }

        if (validationError != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        Button(
            onClick = {
                // Perform validation on fields based on node type and input modes
                if (title.isBlank()) {
                    validationError = errNodeTitleBlank
                    return@Button
                }

                if (node.type == NodeType.USSD_DIAL && ussdCode.isBlank()) {
                    validationError = errUssdCodeBlank
                    return@Button
                }

                if (node.type == NodeType.MENU_SELECTION && stepInputMode == StepInputMode.AUTOMATIC && optionIndex.isBlank()) {
                    validationError = errOptionIndexBlank
                    return@Button
                }

                if (node.type == NodeType.WAIT && waitSec.toIntOrNull() == null) {
                    validationError = errWaitDurationInteger
                    return@Button
                }

                if (stepInputMode == StepInputMode.INPUT && variableName.isBlank()) {
                    validationError = errVariableNameBlank
                    return@Button
                }

                if (node.type == NodeType.INPUT_VALUE && stepInputMode == StepInputMode.AUTOMATIC && inputValue.isBlank()) {
                    validationError = errInputValueBlank
                    return@Button
                }

                // Type-mismatch validation on save
                if (stepInputMode == StepInputMode.INPUT || variableName.isNotBlank()) {
                    val normName = variableName.trim().lowercase()
                    if (normName.isNotBlank()) {
                        val existingMatch = allNodes.firstOrNull { other ->
                            other.id != node.id &&
                            (other.stepInputMode == StepInputMode.INPUT || other.variableName.isNotBlank()) &&
                            other.variableName.trim().lowercase() == normName
                        }
                        if (existingMatch != null && existingMatch.variableType != variableType) {
                            validationError = "A variable named '${variableName.trim()}' already exists with type ${existingMatch.variableType.fallbackName}. Please match the type or choose a different name."
                            return@Button
                        }
                    }
                }

                validationError = null
                val waitInt = waitSec.toIntOrNull() ?: 2
                val manWaitInt = manualWaitSec.toIntOrNull() ?: 40
                onUpdate(
                    title,
                    ussdCode,
                    optionIndex,
                    inputValue,
                    isSensitive,
                    waitInt,
                    condition,
                    stepInputMode,
                    variableName,
                    variableType,
                    manWaitInt
                )
                onClose()
            },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_node_properties_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.apply_properties))
        }
    }
}
