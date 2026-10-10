package com.example.ui.screens.settings

import android.widget.Toast
import com.example.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.ui.components.AnnouncementsSheet
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.RatingDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AppBottomNavigation
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.MainViewModel

import androidx.compose.foundation.layout.imePadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val securityIssues by viewModel.securityAuditIssues.collectAsStateWithLifecycle()

    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val appUpdateInfo by viewModel.appUpdateInfo.collectAsStateWithLifecycle()
    val showRatingDialog by viewModel.showRatingDialog.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()

    var showAnnouncementsSheet by remember { mutableStateOf(false) }
    var isLanguageDropdownExpanded by remember { mutableStateOf(false) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showContactDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportJsonText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(exportJsonText.toByteArray())
                }
                Toast.makeText(context, "Backup downloaded successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error downloading backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val fileContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                }
                if (fileContent != null) {
                    importJsonText = fileContent
                    Toast.makeText(context, "Backup file loaded! Press Import to save.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        bottomBar = {
            AppBottomNavigation(
                currentRoute = Screen.Settings.route,
                onNavigate = onNavigate
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
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Account
            SettingsSectionHeader(title = stringResource(R.string.settings_account), icon = Icons.Default.AccountCircle)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = authState.userName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(text = authState.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.logout()
                            onNavigate(Screen.Login.route)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("logout_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.sign_out),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showDeleteConfirmDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("delete_account_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = stringResource(R.string.delete_account),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Section 2: Security
            SettingsSectionHeader(title = stringResource(R.string.security_section), icon = Icons.Default.Shield)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFFAEB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCC00))
                    ) {
                        Text(
                            text = stringResource(R.string.security_notice),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF332700),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.accessibility_service_disclosure),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val isServiceActive by com.example.service.UssdAccessibilityService.isServiceActive.collectAsStateWithLifecycle()
                            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                        com.example.service.UssdAccessibilityService.refreshServiceState(context)
                                    }
                                }
                                lifecycleOwner.lifecycle.addObserver(observer)
                                onDispose {
                                    lifecycleOwner.lifecycle.removeObserver(observer)
                                }
                            }
                            Text(
                                text = if (isServiceActive) stringResource(R.string.accessibility_service_active) else stringResource(R.string.accessibility_service_inactive),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isServiceActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                        OutlinedButton(
                            onClick = { showAccessibilityDisclosure = true },
                            modifier = Modifier.testTag("view_accessibility_disclosure_button")
                        ) {
                            Text(stringResource(R.string.view_disclosure))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Execution Engine Tuning
            SettingsSectionHeader(title = stringResource(R.string.execution_tuning), icon = Icons.Default.Speed)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.automation_step_delay),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.step_delay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${userSettings.automationDelayMs} ms",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = userSettings.automationDelayMs.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.updateUserSettings(userSettings.copy(automationDelayMs = newValue.toLong()))
                        },
                        valueRange = 0f..1000f,
                        steps = 19,
                        modifier = Modifier.testTag("automation_delay_slider")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.ussd_response_timeout),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.ussd_timeout_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${userSettings.defaultTimeoutSeconds} s",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = userSettings.defaultTimeoutSeconds.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.updateUserSettings(userSettings.copy(defaultTimeoutSeconds = newValue.toInt()))
                        },
                        valueRange = 10f..60f,
                        steps = 9,
                        modifier = Modifier.testTag("default_timeout_slider")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Appearance
            SettingsSectionHeader(title = stringResource(R.string.appearance_section), icon = Icons.Default.Palette)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.dark_theme),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (!userSettings.useSystemTheme && userSettings.isDarkModeEnabled) stringResource(R.string.dark_theme_active)
                                else if (!userSettings.useSystemTheme && !userSettings.isDarkModeEnabled) stringResource(R.string.light_theme_active)
                                else stringResource(R.string.system_default_theme_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = userSettings.isDarkModeEnabled,
                            onCheckedChange = { isChecked ->
                                viewModel.updateUserSettings(
                                    userSettings.copy(
                                        isDarkModeEnabled = isChecked,
                                        useSystemTheme = false
                                    )
                                )
                            },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Language
            SettingsSectionHeader(title = stringResource(R.string.language_section), icon = Icons.Default.Language)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val isFrench = userSettings.language.equals("French", ignoreCase = true) || userSettings.language.equals("fr", ignoreCase = true)
                    val selectedLanguageDisplayName = if (isFrench) "Français 🇫🇷" else "English 🇬🇧"

                    ExposedDropdownMenuBox(
                        expanded = isLanguageDropdownExpanded,
                        onExpandedChange = { isLanguageDropdownExpanded = !isLanguageDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().testTag("language_dropdown_box")
                    ) {
                        OutlinedTextField(
                            value = selectedLanguageDisplayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.language_label)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isLanguageDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("language_dropdown_field")
                        )

                        ExposedDropdownMenu(
                            expanded = isLanguageDropdownExpanded,
                            onDismissRequest = { isLanguageDropdownExpanded = false }
                        ) {
                            // English Option
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "English 🇬🇧",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = if (!isFrench) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                            Text(
                                                text = if (!isFrench) "Default active language" else "Anglais (Par défaut)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isFrench) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setAppLanguage("English")
                                    isLanguageDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("language_option_english")
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                            // French Option
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Français 🇫🇷",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = if (isFrench) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                            Text(
                                                text = if (isFrench) "Langue active par défaut" else "French / Francophonie",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isFrench) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setAppLanguage("French")
                                    isLanguageDropdownExpanded = false
                                },
                                modifier = Modifier.testTag("language_option_french")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Country & Region
            SettingsSectionHeader(title = stringResource(R.string.country_region), icon = Icons.Default.SimCard)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.select_country_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    var countryDropdownExpanded by remember { mutableStateOf(false) }
                    val currentCountry = userSettings.userCountry.ifBlank { "Ghana" }

                    ExposedDropdownMenuBox(
                        expanded = countryDropdownExpanded,
                        onExpandedChange = { countryDropdownExpanded = !countryDropdownExpanded },
                        modifier = Modifier.fillMaxWidth().testTag("settings_country_dropdown_box")
                    ) {
                        OutlinedTextField(
                            value = currentCountry,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.country_region)) },
                            leadingIcon = {
                                Text(
                                    text = com.example.data.model.CountryConstants.getFlagEmoji(currentCountry),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("settings_country_dropdown_field")
                        )

                        ExposedDropdownMenu(
                            expanded = countryDropdownExpanded,
                            onDismissRequest = { countryDropdownExpanded = false }
                        ) {
                            com.example.data.model.CountryConstants.CENTRAL_COUNTRIES.forEach { c ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = c.flagEmoji, style = MaterialTheme.typography.titleMedium)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(text = c.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (c.name.equals(currentCountry, true)) FontWeight.Bold else FontWeight.Normal))
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateUserCountry(c.name)
                                        countryDropdownExpanded = false
                                        Toast.makeText(context, "Country updated to ${c.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("settings_country_option_${c.name}")
                                )
                            }
                        }
                    }
                }
            }



            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Backup & Portability
            SettingsSectionHeader(title = stringResource(R.string.backup_portability), icon = Icons.Default.LockReset)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backup_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.exportAllFlows { json ->
                                    exportJsonText = json
                                    showExportDialog = true
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_flows_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.export_flows))
                        }

                        OutlinedButton(
                            onClick = {
                                importJsonText = ""
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f).testTag("import_flows_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.import_flows))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 6: Support & Information
            SettingsSectionHeader(title = stringResource(R.string.support_info), icon = Icons.AutoMirrored.Filled.Help)

            val supportTextColor = MaterialTheme.colorScheme.onSurface

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    TextButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("help_center_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = supportTextColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.help_center), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    TextButton(
                        onClick = { showContactDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("contact_us_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null, tint = supportTextColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.contact_us), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    TextButton(
                        onClick = { viewModel.triggerRatingPrompt() },
                        modifier = Modifier.fillMaxWidth().testTag("rate_app_settings_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.rate_app_google_play), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    TextButton(
                        onClick = {
                            viewModel.checkForAppUpdate(forceCheck = true)
                            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("check_update_settings_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = Color(0xFF1976D2))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.check_for_updates), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    TextButton(
                        onClick = { showAnnouncementsSheet = true },
                        modifier = Modifier.fillMaxWidth().testTag("announcements_settings_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFF57C00))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.system_announcements), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    TextButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("about_app_button"),
                        colors = ButtonDefaults.textButtonColors(contentColor = supportTextColor)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = supportTextColor)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.about_app), style = MaterialTheme.typography.titleMedium, color = supportTextColor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(stringResource(R.string.set_master_pin)) },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pinInput = it },
                    label = { Text(stringResource(R.string.enter_pin)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("set_pin_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            viewModel.settingsRepository.savePin(pinInput)
                            Toast.makeText(context, "Master PIN updated cleanly!", Toast.LENGTH_SHORT).show()
                            showPinDialog = false
                        }
                    },
                    enabled = pinInput.length == 4
                ) {
                    Text(stringResource(R.string.save_pin))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
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
                TextButton(onClick = { showHelpDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            title = { Text(stringResource(R.string.contact_support)) },
            text = {
                Column {
                    Text(stringResource(R.string.contact_prompt), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.support_email), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.support_availability), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showContactDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_ussd_automation)) },
            text = {
                Text(stringResource(R.string.about_ussd_body))
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showAccessibilityDisclosure) {
        com.example.ui.components.AccessibilityDisclosureDialog(
            onDismiss = { showAccessibilityDisclosure = false }
        )
    }

    if (showExportDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.export_workflows)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.export_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            createDocumentLauncher.launch("ussd_workflows_backup.json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.download_json_file))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = exportJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(150.dp).testTag("export_json_field"),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(exportJsonText))
                        Toast.makeText(context, "Copied backup JSON to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.copy_code))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.got_it))
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_workflows)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.import_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            openDocumentLauncher.launch("application/json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD54F),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(stringResource(R.string.upload_json_file))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text(stringResource(R.string.paste_json_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().height(150.dp).testTag("import_json_field"),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isNotBlank()) {
                            viewModel.importAllFlowsFromJson(importJsonText) { success ->
                                if (success) {
                                    Toast.makeText(context, "Workflows imported successfully!", Toast.LENGTH_SHORT).show()
                                    showImportDialog = false
                                    onNavigate(com.example.ui.navigation.Screen.FlowLibrary.route)
                                } else {
                                    Toast.makeText(context, "Invalid backup data. Please check and try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = importJsonText.isNotBlank()
                ) {
                    Text(stringResource(R.string.import_button_text))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_confirmation)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.disableAccount {
                            Toast.makeText(context, "Account has been deleted", Toast.LENGTH_SHORT).show()
                            onNavigate(Screen.Login.route)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.delete), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
}

@Composable
fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
