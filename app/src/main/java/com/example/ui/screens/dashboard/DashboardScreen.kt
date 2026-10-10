package com.example.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.example.ui.components.AnnouncementsSheet
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.RatingDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UssdFlow
import com.example.data.model.ensureVariableKeys
import kotlinx.coroutines.launch
import com.example.ui.components.AppBottomNavigation
import com.example.ui.components.FlowCard
import com.example.ui.components.ImportFlowDialog
import com.example.ui.components.NetworkLogo
import com.example.ui.components.StatCard
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.MainViewModel
import com.example.util.FlowIconMapper

import com.example.ui.theme.BentoBlueContainer
import com.example.ui.theme.BentoNeutralContainer
import com.example.ui.theme.BentoOnBlueContainer
import com.example.ui.theme.BentoOnNeutralContainer
import com.example.ui.theme.BentoOnPurpleContainer
import com.example.ui.theme.BentoPurpleContainer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.AutoAwesome

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION")
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val flows by viewModel.allFlows.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()

    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadAnnouncementCount.collectAsStateWithLifecycle()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val showRatingDialog by viewModel.showRatingDialog.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()

    var showAnnouncementsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchAnnouncements(forceRefresh = false)
        viewModel.checkForAppUpdate(forceCheck = false)
    }

    val context = LocalContext.current
    var backPressedTime by remember { mutableStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, context.getString(R.string.press_back_exit), Toast.LENGTH_SHORT).show()
        }
    }

    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var templateToPreview by remember { mutableStateOf<UssdFlow?>(null) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val defaultTemplates by viewModel.templates.collectAsStateWithLifecycle()

    val view = androidx.compose.ui.platform.LocalView.current
    val window = (context as? android.app.Activity)?.window
    val originalStatusBarColor = remember { window?.statusBarColor }
    androidx.compose.runtime.DisposableEffect(view) {
        window?.statusBarColor = android.graphics.Color.parseColor("#FFFFCC00")
        onDispose {
            originalStatusBarColor?.let { window?.statusBarColor = it }
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = Screen.Dashboard.route,
                onNavigate = onNavigate,
                language = userSettings.language
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val userAvatarColor = remember(authState.userName) { 
                listOf(Color.Red, Color.Blue, Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF009688), Color(0xFFFF5722)).random() 
            }
            
            val activeRole = remember(authState.userRole, userSettings.userRole) {
                if (authState.userRole.isNotBlank()) authState.userRole else userSettings.userRole.ifBlank { "Default" }
            }
            val isAgent = activeRole.equals("Agent", ignoreCase = true)
            val isSubscriber = activeRole.equals("Subscriber", ignoreCase = true)
            val isRoleBased = isAgent || isSubscriber
            var selectedNetwork by remember { mutableStateOf(userSettings.selectedNetwork) }

            val userCountry = remember(userSettings.userCountry, authState.userCountry) {
                if (userSettings.userCountry.isNotBlank()) userSettings.userCountry
                else if (authState.userCountry.isNotBlank()) authState.userCountry
                else "Ghana"
            }

            // Extract distinct networks strictly from role-matching, user country-matching, and grid_btn templates
            val networkList = remember(defaultTemplates, activeRole, userCountry) {
                val roleTemplates = defaultTemplates.filter { template ->
                    val isGridButton = template.gridBtn
                    val matchesRole = template.userRole.equals(activeRole, ignoreCase = true)
                    val matchesCountry = userCountry.isBlank() ||
                            userCountry.equals("All", ignoreCase = true) ||
                            userCountry.equals("Global", ignoreCase = true) ||
                            template.country.isBlank() ||
                            template.country.equals("Global", ignoreCase = true) ||
                            template.country.equals(userCountry, ignoreCase = true) ||
                            com.example.data.model.CountryConstants.getFlagEmoji(template.country) == com.example.data.model.CountryConstants.getFlagEmoji(userCountry)
                    isGridButton && matchesRole && matchesCountry
                }
                roleTemplates
                    .map { it.network.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            }

            LaunchedEffect(networkList, userSettings.selectedNetwork) {
                if (networkList.isNotEmpty()) {
                    val savedNet = userSettings.selectedNetwork
                    val savedMatch = networkList.find { it.equals(savedNet, ignoreCase = true) }
                    if (savedMatch != null) {
                        selectedNetwork = savedMatch
                    } else if (selectedNetwork.isBlank() || !networkList.any { it.equals(selectedNetwork, ignoreCase = true) }) {
                        selectedNetwork = networkList.first()
                        viewModel.selectNetwork(selectedNetwork)
                    }
                } else {
                    selectedNetwork = ""
                }
            }

            // Operations grid templates filtered by role, user country, grid_btn is true (whether isTemplate is true or false), and exact network match
            val filteredRoleTemplates = remember(defaultTemplates, activeRole, selectedNetwork, userCountry) {
                val roleMatching = defaultTemplates.filter { template ->
                    val isGridButton = template.gridBtn
                    val matchesRole = template.userRole.equals(activeRole, ignoreCase = true)
                    val matchesCountry = userCountry.isBlank() ||
                            userCountry.equals("All", ignoreCase = true) ||
                            userCountry.equals("Global", ignoreCase = true) ||
                            template.country.isBlank() ||
                            template.country.equals("Global", ignoreCase = true) ||
                            template.country.equals(userCountry, ignoreCase = true) ||
                            com.example.data.model.CountryConstants.getFlagEmoji(template.country) == com.example.data.model.CountryConstants.getFlagEmoji(userCountry)
                    isGridButton && matchesRole && matchesCountry
                }
                if (selectedNetwork.isNotBlank()) {
                    roleMatching.filter { it.network.trim().equals(selectedNetwork.trim(), ignoreCase = true) }
                } else {
                    emptyList()
                }
            }

            // Stylish Top Banner Container (Static at top)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = if (isRoleBased) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                color = com.example.ui.theme.MtnYellow
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = authState.userName.take(2).uppercase().ifBlank { "US" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = com.example.data.model.AppStrings.welcomeBack(userSettings.language),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    var roleDropdownExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black,
                                            modifier = Modifier
                                                .clickable { roleDropdownExpanded = true }
                                                .testTag("dashboard_role_tag")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = activeRole.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                                                    color = com.example.ui.theme.MtnYellow
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = "Change Role",
                                                    tint = com.example.ui.theme.MtnYellow,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }

                                        androidx.compose.material3.DropdownMenu(
                                            expanded = roleDropdownExpanded,
                                            onDismissRequest = { roleDropdownExpanded = false }
                                        ) {
                                            listOf("Subscriber", "Agent", "Default").forEach { roleOption ->
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(roleOption, fontWeight = if (activeRole.equals(roleOption, true)) FontWeight.Bold else FontWeight.Normal) },
                                                    onClick = {
                                                        roleDropdownExpanded = false
                                                        viewModel.updateUserRole(roleOption)
                                                        Toast.makeText(context, "Role switched to $roleOption", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.testTag("role_menu_item_$roleOption")
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = authState.userName.ifBlank { "User" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.Black,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { showAnnouncementsSheet = true },
                                modifier = Modifier.testTag("notifications_icon")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) {
                                            Badge { Text(unreadCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Announcements",
                                        tint = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    if (!isRoleBased) {
                        Spacer(modifier = Modifier.height(18.dp))

                        // Bento Grid Overview Section
                        Text(
                            text = com.example.data.model.AppStrings.overviewGrid(userSettings.language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Asymmetric Bento Cards Grid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Left Bento Hero Card (Active Flows)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .testTag("bento_hero_card"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MtnYellowContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (if (isRoleBased) filteredRoleTemplates.size else stats.totalFlows).toString(),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.AccountTree,
                                            contentDescription = null,
                                            tint = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = com.example.data.model.AppStrings.activeFlows(userSettings.language),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Right Card (Today's Runs)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .testTag("bento_today_runs_card"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.MtnYellowContainer),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stats.todayExecutions.toString(),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Today,
                                            contentDescription = null,
                                            tint = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = com.example.data.model.AppStrings.todayRuns(userSettings.language),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Scrollable Content Column below static header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                if (isRoleBased) {
                    // ==========================================
                    // AGENT / SUBSCRIBER ROLE-BASED DASHBOARD
                    // ==========================================

                    // 1. Select Network Section (Category style filter chips)
                    val networkHeaderTitle = if (networkList.isEmpty()) {
                        com.example.data.model.AppStrings.noNetworkAvailable(userSettings.language)
                    } else {
                        com.example.data.model.AppStrings.selectNetwork(userSettings.language)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = networkHeaderTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (networkList.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (networkList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(networkList) { net ->
                                val isSelected = selectedNetwork.equals(net, ignoreCase = true)
                                androidx.compose.material3.FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedNetwork = net
                                        viewModel.selectNetwork(net)
                                    },
                                    label = {
                                        Text(
                                            text = net,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = com.example.ui.theme.MtnYellow,
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color.Transparent,
                                        labelColor = MaterialTheme.colorScheme.onBackground
                                    ),
                                    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = Color.Black,
                                        selectedBorderColor = Color.Transparent,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 0.dp
                                    ),
                                    modifier = Modifier.testTag("network_chip_$net")
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Operations Section (Driven by templates matching active role & network)
                    val operationsTitle = if (isAgent) {
                        com.example.data.model.AppStrings.agentOperations(userSettings.language)
                    } else {
                        com.example.data.model.AppStrings.subscriberOperations(userSettings.language)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (selectedNetwork.isNotBlank()) {
                            NetworkLogo(
                                networkName = selectedNetwork,
                                size = 30.dp,
                                modifier = Modifier.testTag("selected_network_logo")
                            )
                        }
                        Text(
                            text = operationsTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredRoleTemplates.isEmpty()) {
                        val roleDisplay = if (isAgent) stringResource(R.string.role_agent) else stringResource(R.string.role_subscriber)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .testTag("empty_role_operations_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.no_role_flows_found, roleDisplay),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.no_role_flows_subtitle, roleDisplay),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("operations_grid")
                        ) {
                            filteredRoleTemplates.chunked(3).forEachIndexed { rowIndex, rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEachIndexed { colIndex, template ->
                                        val index = rowIndex * 3 + colIndex
                                        
                                        val networkPrefix = if (template.network.isNotBlank()) {
                                            template.network.trim()
                                        } else if (selectedNetwork.isNotBlank()) {
                                            selectedNetwork.trim()
                                        } else {
                                            ""
                                        }
                                        val formattedName = if (networkPrefix.isNotEmpty() && !template.name.startsWith(networkPrefix, ignoreCase = true)) {
                                            "$networkPrefix - ${template.name}"
                                        } else {
                                            template.name
                                        }

                                        // Strict matching only: by exact template ID
                                        val matchingSavedFlow = flows.find { saved ->
                                            !saved.isTemplate && template.id.isNotBlank() && saved.id == template.id
                                        }

                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(140.dp)
                                                .clickable {
                                                    val flowToRun = matchingSavedFlow ?: template.copy(
                                                        id = template.id.ifBlank { java.util.UUID.randomUUID().toString() },
                                                        name = formattedName,
                                                        isTemplate = false,
                                                        userRole = activeRole,
                                                        network = if (template.network.isNotBlank()) template.network else selectedNetwork,
                                                        lastModified = System.currentTimeMillis()
                                                    ).ensureVariableKeys()

                                                    // Load directly into memory for execution without saving to flow library
                                                    viewModel.setActiveExecutionFlow(flowToRun)
                                                    onNavigate(Screen.ExecuteFlow.createRoute(flowToRun.id))
                                                }
                                                .testTag("operation_grid_item_$index"),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (matchingSavedFlow != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                            ),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (matchingSavedFlow != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.SpaceEvenly
                                            ) {
                                                val gridIcon = FlowIconMapper.getIcon(template.icon, template.category)
                                                Box(
                                                    modifier = Modifier
                                                        .size(58.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = gridIcon,
                                                        contentDescription = template.name,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(34.dp)
                                                    )
                                                }

                                                Text(
                                                    text = template.getLocalizedName(userSettings.language),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    // Pad empty spaces in row if rowItems.size < 3
                                    for (emptyIndex in 0 until (3 - rowItems.size)) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // ==========================================
                    // DEFAULT ROLE DASHBOARD
                    // ==========================================

                    if (flows.isNotEmpty()) {
                        val mostUsedFlows = remember(flows) { flows.sortedByDescending { it.executionCount }.take(4) }
                        
                        Text(
                            text = com.example.data.model.AppStrings.mostUsedFlows(userSettings.language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(mostUsedFlows, key = { "top_used_${it.id}" }) { flow ->
                                Card(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .height(135.dp)
                                        .clickable { onNavigate(Screen.ExecuteFlow.createRoute(flow.id)) }
                                        .testTag("top_used_flow_card_${flow.id}"),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = flow.entryUssdCode,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            com.example.ui.components.ProviderLogo(
                                                flowName = flow.name,
                                                category = flow.category,
                                                containerSize = 40.dp,
                                                iconSize = 22.dp,
                                                shapeRadius = 10.dp
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = flow.getLocalizedName(userSettings.language),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${flow.executionCount} runs • ${flow.category.displayName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Recent Flows Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = com.example.data.model.AppStrings.recentFlows(userSettings.language),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        TextButton(onClick = { onNavigate(Screen.FlowLibrary.route) }) {
                            Text(
                                text = com.example.data.model.AppStrings.seeAll(userSettings.language, flows.size),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (flows.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.no_flows_found),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.no_flows_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { onNavigate(Screen.Templates.route) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(com.example.data.model.AppStrings.browseTemplates(userSettings.language), style = MaterialTheme.typography.labelMedium)
                                    }
                                    OutlinedButton(
                                        onClick = { onNavigate(Screen.CreateFlow.route) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(com.example.data.model.AppStrings.createFlow(userSettings.language), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            flows.take(5).forEach { flow ->
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
                        }
                    }
                }

            Spacer(modifier = Modifier.height(24.dp))

            // Predefined Flow Templates Section
            Text(
                text = com.example.data.model.AppStrings.predefinedTemplates(userSettings.language),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(10.dp))

            val countryFilteredTemplates = remember(defaultTemplates, userCountry) {
                val matches = defaultTemplates.filter { template ->
                    template.isTemplate && (
                        userCountry.isBlank() ||
                                userCountry.equals("All", ignoreCase = true) ||
                                userCountry.equals("Global", ignoreCase = true) ||
                                template.country.isBlank() ||
                                template.country.equals("Global", ignoreCase = true) ||
                                template.country.equals(userCountry, ignoreCase = true) ||
                                com.example.data.model.CountryConstants.getFlagEmoji(template.country) == com.example.data.model.CountryConstants.getFlagEmoji(userCountry)
                    )
                }
                if (matches.isNotEmpty()) matches else defaultTemplates.filter { it.isTemplate }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 16.dp)
            ) {
                items(countryFilteredTemplates) { template ->
                    Card(
                        modifier = Modifier
                            .width(220.dp)
                            .testTag("template_card_${template.id}"),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = null,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = template.entryUssdCode,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = template.getLocalizedName(userSettings.language),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = template.getLocalizedDescription(userSettings.language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.height(36.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { templateToPreview = template }) {
                                    Text(stringResource(R.string.preview), style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = { viewModel.duplicateFlow(template) },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text(stringResource(R.string.use_template), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            com.example.ui.components.AppBannerAd(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAnnouncementsSheet) {
        AnnouncementsSheet(
            announcements = announcements,
            onDismiss = { showAnnouncementsSheet = false },
            onMarkAsRead = { id -> viewModel.markAnnouncementAsRead(id) },
            onMarkAllAsRead = { viewModel.markAllAnnouncementsAsRead() },
            onOpenUrl = { url -> viewModel.openPlayStore(url) },
            onRefreshTemplates = {
                viewModel.refreshTemplates(forceServer = true) { count ->
                    Toast.makeText(context, "Flow templates updated ($count available)", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showRatingDialog) {
        RatingDialog(
            onRateOnPlayStore = { viewModel.onUserRatedApp() },
            onDismiss = { neverAskAgain -> viewModel.dismissRatingDialog(neverAskAgain) }
        )
    }

    if (showUpdateDialog && appUpdateInfo.isUpdateAvailable) {
        AppUpdateDialog(
            updateInfo = appUpdateInfo,
            onUpdateNow = { viewModel.openPlayStore(appUpdateInfo.updateUrl) },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    if (showImportDialog) {
        ImportFlowDialog(
            onDismiss = { showImportDialog = false },
            onImportJson = { json ->
                viewModel.importFlowFromJson(json) { success ->
                    showImportDialog = false
                }
            }
        )
    }

    templateToPreview?.let { tpl ->
        AlertDialog(
            onDismissRequest = { templateToPreview = null },
            title = { Text(stringResource(R.string.template_preview_title, tpl.getLocalizedName(userSettings.language))) },
            text = {
                Column {
                    Text(stringResource(R.string.ussd_entry_code_format, tpl.entryUssdCode), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.workflow_steps_format, tpl.nodes.size), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    tpl.nodes.forEachIndexed { idx, node ->
                        Text("${idx + 1}. [${node.type.getLocalizedTitle()}] ${node.getLocalizedTitle(userSettings.language)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.duplicateFlow(tpl)
                        templateToPreview = null
                    }
                ) {
                    Text(stringResource(R.string.clone_use_template))
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToPreview = null }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }
}
