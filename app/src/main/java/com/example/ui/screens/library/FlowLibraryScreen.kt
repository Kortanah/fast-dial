package com.example.ui.screens.library

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FlowCategory
import com.example.data.model.UssdFlow
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.FlowCard
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun FlowLibraryScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val flows by viewModel.allFlows.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var exportedJsonDialog by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val filters = remember {
        listOf("All") + com.example.data.model.FlowCategory.entries.map { it.name }
    }

    val filteredFlows = remember(flows, searchQuery, selectedFilter) {
        flows.filter { flow ->
            val localizedName = flow.getLocalizedName(userSettings.language)
            val localizedDesc = flow.getLocalizedDescription(userSettings.language)
            val matchesSearch = flow.name.contains(searchQuery, ignoreCase = true) ||
                    localizedName.contains(searchQuery, ignoreCase = true) ||
                    flow.entryUssdCode.contains(searchQuery, ignoreCase = true) ||
                    flow.description.contains(searchQuery, ignoreCase = true) ||
                    localizedDesc.contains(searchQuery, ignoreCase = true)

            val matchesFilter = if (selectedFilter == "All") true
            else flow.category.name == selectedFilter || flow.category.fallbackName.equals(selectedFilter, ignoreCase = true)

            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = Screen.FlowLibrary.route,
                onNavigate = onNavigate,
                language = userSettings.language
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Screen.CreateFlow.route) },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("create_flow_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = com.example.data.model.AppStrings.createFlow(userSettings.language))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Text(
                text = com.example.data.model.AppStrings.flowLibraryTitle(userSettings.language),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(com.example.data.model.AppStrings.search(userSettings.language), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("flow_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { filter ->
                    val catEnum = FlowCategory.entries.find { it.name == filter }
                    val filterLabel = if (filter == "All") com.example.data.model.AppStrings.filterAll(userSettings.language) else catEnum?.displayName ?: filter
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filterLabel) },
                        modifier = Modifier.testTag("filter_chip_$filter")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Non-intrusive Banner Ad
            com.example.ui.components.AppBannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredFlows.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(com.example.data.model.AppStrings.noFlowsFound(userSettings.language), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(com.example.data.model.AppStrings.noFlowsSubtitle(userSettings.language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredFlows, key = { it.id }) { flow ->
                        FlowCard(
                            flow = flow,
                            language = userSettings.language,
                            onOpen = { onNavigate(Screen.ExecuteFlow.createRoute(flow.id)) },
                            onEdit = { onNavigate(Screen.FlowBuilder.createRoute(flow.id)) },
                            onExecute = { onNavigate(Screen.ExecuteFlow.createRoute(flow.id)) },
                            onDuplicate = { viewModel.duplicateFlow(flow) },
                            onDelete = { viewModel.deleteFlow(flow.id) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(60.dp))
                    }
                }
            }
        }
    }

    exportedJsonDialog?.let { json ->
        AlertDialog(
            onDismissRequest = { exportedJsonDialog = null },
            title = { Text(stringResource(R.string.exported_flow_json)) },
            text = { Text(json, style = MaterialTheme.typography.bodySmall) },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(json))
                        Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                        exportedJsonDialog = null
                    }
                ) {
                    Text(stringResource(R.string.copy_to_clipboard))
                }
            },
            dismissButton = {
                TextButton(onClick = { exportedJsonDialog = null }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }
}
