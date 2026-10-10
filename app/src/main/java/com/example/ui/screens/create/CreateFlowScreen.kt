package com.example.ui.screens.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.example.data.model.FlowCategory
import com.example.data.model.FlowNode
import com.example.data.model.NodeType
import com.example.data.model.SecurityLevel
import com.example.data.model.UssdFlow
import com.example.data.model.ensureVariableKeys
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.util.UUID

import androidx.compose.foundation.layout.imePadding

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Shield

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFlowScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val defaultRole = remember(authState.userRole, userSettings.userRole) {
        authState.userRole.ifBlank { userSettings.userRole }.ifBlank { "Default" }
    }

    var name by remember { mutableStateOf("") }
    var ussdCode by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FlowCategory.MOBILE_MONEY) }
    var customCategoryName by remember { mutableStateOf("") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.create_new_flow_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = { onNavigate(Screen.Dashboard.route) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.testTag("create_flow_info_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.how_to_build_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                label = { Text(stringResource(R.string.flow_name_label)) },
                placeholder = { Text(stringResource(R.string.flow_name_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_flow_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ussdCode,
                onValueChange = { ussdCode = it; errorMessage = null },
                label = { Text(stringResource(R.string.ussd_entry_code_label)) },
                placeholder = { Text(stringResource(R.string.ussd_entry_code_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_flow_ussd_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Dropdown
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = if (selectedCategory == FlowCategory.CUSTOM && customCategoryName.isNotBlank()) customCategoryName else selectedCategory.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_label)) },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .testTag("create_flow_category_dropdown"),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    FlowCategory.values().forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                selectedCategory = category
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedCategory == FlowCategory.CUSTOM) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = customCategoryName,
                    onValueChange = { customCategoryName = it },
                    label = { Text(stringResource(R.string.custom_category_name_label)) },
                    placeholder = { Text(stringResource(R.string.custom_category_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = context.getString(R.string.error_empty_flow_name)
                            return@OutlinedButton
                        }
                        val flow = createNewFlowObject(name, ussdCode, selectedCategory, userRole = defaultRole)
                        com.example.util.AdsManager.showRewardedAd(context) { rewardEarned ->
                            if (rewardEarned) {
                                coroutineScope.launch {
                                    viewModel.repository.saveFlow(flow)
                                    android.widget.Toast.makeText(context, "Draft saved successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    onNavigate(Screen.Dashboard.route)
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Please watch the full ad to save your flow", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_draft_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.save_draft_button))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorMessage = context.getString(R.string.error_empty_flow_name)
                            return@Button
                        }
                        val flow = createNewFlowObject(name, ussdCode, selectedCategory, userRole = defaultRole)
                        coroutineScope.launch {
                            viewModel.repository.saveFlow(flow)
                            onNavigate(Screen.FlowBuilder.createRoute(flow.id))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("continue_building_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.continue_button), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showGuideDialog) {
        AlertDialog(
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

private fun createNewFlowObject(
    name: String,
    ussdCode: String,
    category: FlowCategory,
    network: String = "",
    userRole: String = "Default"
): UssdFlow {
    val flowId = UUID.randomUUID().toString()
    return UssdFlow(
        id = flowId,
        name = name,
        description = "",
        entryUssdCode = ussdCode.ifBlank { "*170#" },
        category = category,
        network = network.trim(),
        userRole = userRole.trim(),
        securityLevel = SecurityLevel.STANDARD,
        lastModified = System.currentTimeMillis(),
        nodes = listOf(
            FlowNode("start_1", NodeType.START, "Start Flow", positionX = 100f, positionY = 100f, nextNodeId = "dial_1"),
            FlowNode("dial_1", NodeType.USSD_DIAL, "Dial USSD Code", ussdCode = ussdCode.ifBlank { "*170#" }, positionX = 100f, positionY = 220f, nextNodeId = "end_1"),
            FlowNode("end_1", NodeType.END, "End Session", positionX = 100f, positionY = 340f)
        )
    ).ensureVariableKeys()
}
