package com.example.ui.screens.execute

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import com.example.data.model.FlowCategory
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ExecutionState
import com.example.data.model.FlowNode
import com.example.data.model.StepInputMode
import com.example.data.model.UssdFlow
import com.example.data.model.VariableType
import com.example.data.model.PhoneContact
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.ProviderLogo
import com.example.ui.components.TerminalView
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.ExecutionViewModel
import com.example.ui.viewmodel.MainViewModel

data class DistinctVariableInput(
    val key: String,
    val label: String,
    val variableType: VariableType,
    val associatedNodeIds: List<String>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExecuteFlowScreen(
    initialFlowId: String?,
    mainViewModel: MainViewModel,
    executionViewModel: ExecutionViewModel = viewModel(),
    onNavigate: (String) -> Unit
) {
    val flows by mainViewModel.allFlows.collectAsStateWithLifecycle()
    val defaultTemplates by mainViewModel.templates.collectAsStateWithLifecycle()
    val directActiveFlow by mainViewModel.activeExecutionFlow.collectAsStateWithLifecycle()
    val logsHistory by mainViewModel.executionLogs.collectAsStateWithLifecycle()
    val engineState by executionViewModel.engineState.collectAsStateWithLifecycle()
    val userSettings by mainViewModel.userSettings.collectAsStateWithLifecycle()

    var selectedFlow by remember {
        mutableStateOf(
            if (mainViewModel.activeExecutionFlow.value != null && (initialFlowId.isNullOrEmpty() || mainViewModel.activeExecutionFlow.value?.id == initialFlowId)) {
                mainViewModel.activeExecutionFlow.value
            } else {
                null
            }
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showExecuteGuideDialog by remember { mutableStateOf(false) }
    var showAccessibilityDisclosureDialog by remember { mutableStateOf(false) }

    // Map storing inline variable field values keyed by normalized key
    val inlineVariableValues = remember { mutableStateMapOf<String, String>() }
    var activeContactPickerKey by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var hasPhoneStatePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }

    val activeSimState = remember(hasPhoneStatePermission, context) {
        if (!hasPhoneStatePermission) {
            "Tap to check SIM Status"
        } else {
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                val activeList = subscriptionManager?.activeSubscriptionInfoList ?: emptyList()

                if (activeList.isEmpty()) {
                    "No Active SIM"
                } else if (activeList.size == 1) {
                    val simInfo = activeList[0]
                    val slotNum = simInfo.simSlotIndex + 1
                    val displayName = simInfo.displayName?.toString() ?: ""
                    if (displayName.isNotBlank() && displayName != "SIM $slotNum" && displayName != "Card $slotNum") {
                        "SIM $slotNum ($displayName)"
                    } else {
                        "SIM $slotNum"
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val defaultHandle = telecomManager?.userSelectedOutgoingPhoneAccount
                        if (defaultHandle != null) {
                            val phoneAccount = telecomManager.getPhoneAccount(defaultHandle)
                            val label = phoneAccount?.label?.toString() ?: ""
                            if (label.isNotBlank()) {
                                label
                            } else {
                                val id = defaultHandle.id ?: ""
                                if (id.contains("0") || id.contains("sub1")) {
                                    "SIM 1"
                                } else if (id.contains("1") || id.contains("sub2")) {
                                    "SIM 2"
                                } else {
                                    "Default SIM"
                                }
                            }
                        } else {
                            "Ask Every Time"
                        }
                    } else {
                        "Ask Every Time"
                    }
                }
            } catch (e: SecurityException) {
                "Permission Required"
            } catch (e: Exception) {
                "Ask Every Time"
            }
        }
    }

    val triggerFlowExecution = { flow: UssdFlow, userInputs: Map<String, String> ->
        mainViewModel.recordFlowExecuted()
        executionViewModel.startFlowExecution(
            flow = flow,
            userInputs = userInputs,
            repository = mainViewModel.repository,
            simSlot = 1,
            automationDelayMs = userSettings.automationDelayMs
        )
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val callGranted = permissions[Manifest.permission.CALL_PHONE] ?: false
        val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
        hasPhoneStatePermission = phoneStateGranted
        if (callGranted && selectedFlow != null) {
            val userInputsMap = mutableMapOf<String, String>()
            inlineVariableValues.forEach { (k, v) ->
                if (v.isNotBlank()) userInputsMap[k] = v
            }
            triggerFlowExecution(selectedFlow!!, userInputsMap)
        }
    }

    LaunchedEffect(directActiveFlow, flows, defaultTemplates, initialFlowId) {
        val matched = when {
            directActiveFlow != null && (initialFlowId.isNullOrEmpty() || directActiveFlow?.id == initialFlowId) -> {
                directActiveFlow
            }
            !initialFlowId.isNullOrEmpty() -> {
                flows.find { it.id == initialFlowId }
                    ?: defaultTemplates.find { it.id == initialFlowId }
                    ?: directActiveFlow
                    ?: flows.firstOrNull()
            }
            directActiveFlow != null -> directActiveFlow
            flows.isNotEmpty() -> flows.first()
            else -> defaultTemplates.firstOrNull()
        }
        if (matched != null && (selectedFlow == null || selectedFlow?.id != matched.id)) {
            selectedFlow = matched
        }
    }

    var executionStartTimeMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(engineState.state) {
        if (engineState.state == ExecutionState.RUNNING) {
            if (executionStartTimeMs == null) {
                executionStartTimeMs = System.currentTimeMillis()
            }
        } else {
            executionStartTimeMs = null
        }
    }

    val isAccessibilityActive by com.example.service.UssdAccessibilityService.isServiceActive.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                com.example.service.UssdAccessibilityService.refreshServiceState(context)
                if (engineState.state == ExecutionState.RUNNING) {
                    val startTime = executionStartTimeMs
                    val timeoutSec = userSettings.defaultTimeoutSeconds + 10
                    if (startTime != null && (System.currentTimeMillis() - startTime) > timeoutSec * 1000L) {
                        executionViewModel.stop()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val activeFlow = selectedFlow

    // Distinct variable inputs deduplicated across steps by normalized variable key
    val distinctVariableInputs = remember(activeFlow?.id) {
        val current = activeFlow
        if (current == null) emptyList()
        else {
            val inputNodes = current.nodes.filter {
                it.stepInputMode == StepInputMode.INPUT || it.variableName.isNotBlank()
            }
            val grouped = inputNodes.groupBy { node ->
                node.normalizedVariableKey
            }
            grouped.map { (normKey, nodes) ->
                val first = nodes.first()
                val label = first.variableName.ifBlank { first.title }.ifBlank {
                    normKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                DistinctVariableInput(
                    key = normKey,
                    label = label,
                    variableType = first.variableType,
                    associatedNodeIds = nodes.map { it.id }
                )
            }
        }
    }

    var initializedFlowId by rememberSaveable { mutableStateOf<String?>(null) }

    // Initialize default variable values only when switching to a NEW flow ID
    LaunchedEffect(activeFlow?.id) {
        val current = activeFlow ?: return@LaunchedEffect
        validationError = null
        if (initializedFlowId != current.id) {
            initializedFlowId = current.id
            inlineVariableValues.clear()
            current.nodes.forEach { node ->
                val normKey = node.normalizedVariableKey
                if (normKey.isNotBlank()) {
                    if (!inlineVariableValues.containsKey(normKey) || inlineVariableValues[normKey].isNullOrBlank()) {
                        if (node.inputValue.isNotBlank()) {
                            inlineVariableValues[normKey] = node.inputValue
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.execute_flow),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onNavigate(Screen.Dashboard.route) },
                        modifier = Modifier.testTag("execute_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showExecuteGuideDialog = true },
                        modifier = Modifier.testTag("execute_guide_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "How to Create Flow Guide",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Accessibility Service Warning Banner if Service Inactive
            if (!isAccessibilityActive) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("accessibility_warning_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.exec_accessibility_inactive),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = stringResource(R.string.exec_accessibility_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showAccessibilityDisclosureDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.enable_button),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Custom USSD Flow Header with logo and integrated details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                ProviderLogo(
                    flowName = activeFlow?.name ?: "USSD Automation",
                    category = activeFlow?.category ?: FlowCategory.CUSTOM,
                    containerSize = 48.dp,
                    iconSize = 26.dp,
                    shapeRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeFlow?.getLocalizedName(userSettings.language) ?: "USSD Automation",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (activeFlow != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.entry_code_label, activeFlow.entryUssdCode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.steps_count, activeFlow.nodes.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeFlow != null) {

                // INLINE Deduplicated Variable Input Fields (Directly on Page - No Modal Popup)
                if (distinctVariableInputs.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("inline_variable_inputs_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.required_flow_inputs),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.provide_values_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            distinctVariableInputs.forEachIndexed { index, variable ->
                                key(variable.key) {
                                    val rawLabel = variable.label
                                    val labelName = com.example.util.FlowTranslator.translate(rawLabel, userSettings.language)
                                    val currentValue = inlineVariableValues[variable.key] ?: ""
                                    val dynamicPlaceholder = if (labelName.startsWith("Enter ", ignoreCase = true)) labelName else "Enter $labelName"

                                    val requester = remember { BringIntoViewRequester() }
                                    var containerHeightPx by remember { mutableStateOf(0f) }
                                    val extraOffsetPx = with(density) { 120.dp.toPx() }

                                    Column(
                                        modifier = Modifier
                                            .bringIntoViewRequester(requester)
                                            .onSizeChanged { size ->
                                                containerHeightPx = size.height.toFloat()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = labelName,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                            ) {
                                                Text(
                                                    text = variable.variableType.displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = currentValue,
                                            onValueChange = { inlineVariableValues[variable.key] = it },
                                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            ),
                                            placeholder = {
                                                Text(
                                                    text = dynamicPlaceholder,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            },
                                            trailingIcon = if (variable.variableType == VariableType.PHONE_CONTACT) {
                                                {
                                                    IconButton(onClick = { activeContactPickerKey = variable.key }) {
                                                        Icon(
                                                            Icons.Default.ContactPage,
                                                            contentDescription = "Select Contact",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            } else null,
                                            singleLine = true,
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                keyboardType = when (variable.variableType) {
                                                    VariableType.NUMBER -> androidx.compose.ui.text.input.KeyboardType.Number
                                                    VariableType.PHONE_CONTACT -> androidx.compose.ui.text.input.KeyboardType.Phone
                                                    VariableType.TEXT -> androidx.compose.ui.text.input.KeyboardType.Text
                                                },
                                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        coroutineScope.launch {
                                                            requester.bringIntoView(
                                                                Rect(
                                                                    left = 0f,
                                                                    top = 0f,
                                                                    right = 0f,
                                                                    bottom = containerHeightPx + extraOffsetPx
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                .testTag("inline_input_field_${variable.key}"),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
    
                                    if (index < distinctVariableInputs.size - 1) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Show validation errors inline, if any, when user tries to run without all inputs
                if (validationError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
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
                }

                // Control Buttons - Full Width Run / Running Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (engineState.state == ExecutionState.RUNNING) {
                        Button(
                            onClick = { /* Running state indicator */ },
                            modifier = Modifier.weight(1f).height(50.dp).testTag("run_flow_button_running"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.flow_running), fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        val errFillRequired = stringResource(R.string.error_fill_required_inputs)

                        Button(
                            onClick = { executionViewModel.stop() },
                            modifier = Modifier.height(50.dp).testTag("stop_flow_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.stop_button), color = Color.White)
                        }
                    } else {
                        val errFillRequired = stringResource(R.string.error_fill_required_inputs)

                        Button(
                            onClick = {
                                val active = selectedFlow ?: return@Button

                                // Perform data validation check prior to flow start
                                val emptyFields = distinctVariableInputs.filter {
                                    inlineVariableValues[it.key].isNullOrBlank()
                                }
                                if (emptyFields.isNotEmpty()) {
                                    validationError = errFillRequired.format(emptyFields.joinToString { it.label })
                                    return@Button
                                } else {
                                    validationError = null
                                }

                                // Dismiss keyboard and clear focus instantly on button tap
                                keyboardController?.hide()
                                focusManager.clearFocus()

                                // Substitute flow nodes with user entered variable values from inline inputs
                                val updatedNodes = active.nodes.map { node ->
                                    val normKey = node.normalizedVariableKey
                                    val varVal = inlineVariableValues[normKey]
                                    if (varVal != null && varVal.isNotBlank()) {
                                        node.copy(inputValue = varVal, optionIndex = varVal)
                                    } else node
                                }
                                val runtimeFlow = active.copy(nodes = updatedNodes)

                                val userInputsMap = mutableMapOf<String, String>()
                                inlineVariableValues.forEach { (k, v) ->
                                    if (v.isNotBlank()) userInputsMap[k] = v
                                }
                                runtimeFlow.nodes.forEach { node ->
                                    val valVal = node.inputValue
                                    if (valVal.isNotBlank()) {
                                        userInputsMap[node.id] = valVal
                                        val normKey = node.normalizedVariableKey
                                        userInputsMap[normKey] = valVal
                                        if (node.variableName.isNotBlank()) {
                                            userInputsMap[node.variableName.trim().lowercase()] = valVal
                                            userInputsMap[node.variableName] = valVal
                                        }
                                        if (node.title.isNotBlank()) {
                                            userInputsMap[node.title.trim().lowercase()] = valVal
                                            userInputsMap[node.title] = valVal
                                        }
                                    }
                                }

                                val hasCallPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CALL_PHONE
                                ) == PackageManager.PERMISSION_GRANTED
                                val hasPhoneStatePerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_PHONE_STATE
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasCallPermission || !hasPhoneStatePerm) {
                                    callPermissionLauncher.launch(
                                        arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
                                    )
                                } else {
                                    triggerFlowExecution(runtimeFlow, userInputsMap)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("run_flow_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.run_flow_button), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Non-intrusive Ad Banner before Execution Console Box
                com.example.ui.components.AppBannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Clean Execution Status Log Console
                TerminalView(
                    state = engineState,
                    entryUssdCode = activeFlow.entryUssdCode.ifBlank { "*170#" }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Contact Selection Overlay / Dialog
    if (activeContactPickerKey != null) {
        val targetKey = activeContactPickerKey!!
        val recentContacts by mainViewModel.recentContacts.collectAsStateWithLifecycle()

        PhoneContactsPickerDialog(
            recentContacts = recentContacts,
            onDismiss = { activeContactPickerKey = null },
            onSelectContact = { contact ->
                mainViewModel.addRecentContact(contact)
                inlineVariableValues[targetKey] = contact.phoneNumber
                activeContactPickerKey = null
            }
        )
    }

    if (showExecuteGuideDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExecuteGuideDialog = false },
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
                androidx.compose.material3.TextButton(onClick = { showExecuteGuideDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showAccessibilityDisclosureDialog) {
        com.example.ui.components.AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDisclosureDialog = false }
        )
    }


}

@Composable
fun PhoneContactsPickerDialog(
    recentContacts: List<PhoneContact>,
    onDismiss: () -> Unit,
    onSelectContact: (PhoneContact) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    var deviceContacts by remember { mutableStateOf<List<PhoneContact>>(emptyList()) }
    var isLoadingContacts by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission, searchQuery) {
        if (hasPermission) {
            isLoadingContacts = true
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getDeviceContacts(context, searchQuery)
            }
            deviceContacts = loaded
            isLoadingContacts = false
        } else {
            deviceContacts = emptyList()
            isLoadingContacts = false
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.phone_contacts_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (!hasPermission) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.contacts_permission_required),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.contacts_permission_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Contacts, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.grant_contacts_permission))
                        }
                    }
                } else {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_contact_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("contact_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (recentContacts.isNotEmpty() && searchQuery.isBlank()) {
                        Text(
                            text = stringResource(R.string.recent_contacts),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            recentContacts.take(3).forEach { contact ->
                                Card(
                                    onClick = { onSelectContact(contact) },
                                    modifier = Modifier.fillMaxWidth().testTag("recent_contact_${contact.phoneNumber}"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Contacts,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = contact.phoneNumber,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.all_contacts),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (isLoadingContacts) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    } else if (deviceContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) stringResource(R.string.no_contacts_matching, searchQuery) else stringResource(R.string.no_contacts_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                deviceContacts.forEach { contact ->
                                    Card(
                                        onClick = { onSelectContact(contact) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("contact_item_${contact.phoneNumber}"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = contact.name.take(1).uppercase(),
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = contact.name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${contact.phoneNumber} (${contact.category})",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

private fun getDeviceContacts(context: android.content.Context, searchQuery: String): List<PhoneContact> {
    val list = mutableListOf<PhoneContact>()
    try {
        val resolver = context.contentResolver
        val projection = arrayOf(
            android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE
        )

        val selection = if (searchQuery.isNotBlank()) {
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        } else null

        val selectionArgs = if (searchQuery.isNotBlank()) {
            arrayOf("%$searchQuery%", "%$searchQuery%")
        } else null

        val cursor = resolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )

        cursor?.use { c ->
            val nameIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE)

            while (c.moveToNext() && list.size < 100) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "Unknown" else "Unknown"
                val rawNumber = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                val type = if (typeIdx >= 0) {
                    when (c.getInt(typeIdx)) {
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
                        android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
                        else -> "Phone"
                    }
                } else "Phone"

                val cleanNum = rawNumber.replace(" ", "").replace("-", "")
                if (cleanNum.isNotBlank()) {
                    list.add(PhoneContact(name, cleanNum, type))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list.distinctBy { it.phoneNumber }
}

