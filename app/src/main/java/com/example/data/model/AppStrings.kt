package com.example.data.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.R

object AppStrings {
    // Navigation
    @get:StringRes val navHomeRes = R.string.nav_home
    @get:StringRes val navLibraryRes = R.string.nav_library
    @get:StringRes val navTemplatesRes = R.string.nav_templates
    @get:StringRes val navSettingsRes = R.string.nav_settings

    // Dashboard
    @get:StringRes val welcomeBackRes = R.string.welcome_back
    @get:StringRes val overviewGridRes = R.string.overview_grid
    @get:StringRes val activeFlowsRes = R.string.active_flows
    @get:StringRes val todayRunsRes = R.string.todays_runs
    @get:StringRes val mostUsedFlowsRes = R.string.most_used_flows
    @get:StringRes val recentFlowsRes = R.string.recent_flows
    @get:StringRes val predefinedTemplatesRes = R.string.predefined_templates
    @get:StringRes val browseTemplatesRes = R.string.browse_templates
    @get:StringRes val createFlowRes = R.string.create_flow

    // Flow Library
    @get:StringRes val flowLibraryTitleRes = R.string.flow_library_title
    @get:StringRes val searchRes = R.string.search_placeholder
    @get:StringRes val filterAllRes = R.string.filter_all
    @get:StringRes val noFlowsFoundRes = R.string.no_flows_found
    @get:StringRes val noFlowsSubtitleRes = R.string.no_flows_subtitle

    // Templates
    @get:StringRes val flowTemplatesTitleRes = R.string.flow_templates_title
    @get:StringRes val preconfiguredWorkflowsRes = R.string.preconfigured_workflows
    @get:StringRes val countryRegionRes = R.string.country_region
    @get:StringRes val previewRes = R.string.preview
    @get:StringRes val useTemplateRes = R.string.use_template
    @get:StringRes val searchTemplateRes = R.string.search_template
    @get:StringRes val closeRes = R.string.close

    // Actions & Options
    @get:StringRes val executeFlowRes = R.string.execute_flow
    @get:StringRes val editBuilderRes = R.string.edit_builder
    @get:StringRes val duplicateRes = R.string.duplicate
    @get:StringRes val deleteFlowRes = R.string.delete_flow

    // Settings
    @get:StringRes val settingsTitleRes = R.string.settings_title
    @get:StringRes val languageLabelRes = R.string.language_label
    @get:StringRes val languageSubtitleRes = R.string.language_subtitle
    @get:StringRes val securityTitleRes = R.string.security_title
    @get:StringRes val autoVaultLabelRes = R.string.auto_vault_label
    @get:StringRes val appearanceTitleRes = R.string.appearance_title
    @get:StringRes val darkModeLabelRes = R.string.dark_mode_label

    // Composable helpers leveraging Android's native resource localization system
    @Composable
    private fun getLocalizedResource(id: Int, language: String, vararg formatArgs: Any): String {
        val context = androidx.compose.ui.platform.LocalContext.current
        val isFrench = if (language.isNotBlank()) {
            language.equals("French", ignoreCase = true) || language.equals("fr", ignoreCase = true)
        } else {
            java.util.Locale.getDefault().language.lowercase().startsWith("fr")
        }
        val locale = if (isFrench) java.util.Locale.FRENCH else java.util.Locale.ENGLISH
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        val localizedContext = context.createConfigurationContext(configuration)
        return if (formatArgs.isNotEmpty()) {
            localizedContext.resources.getString(id, *formatArgs)
        } else {
            localizedContext.resources.getString(id)
        }
    }

    @Composable
    fun navHome(language: String = ""): String = getLocalizedResource(R.string.nav_home, language)
    @Composable
    fun navLibrary(language: String = ""): String = getLocalizedResource(R.string.nav_library, language)
    @Composable
    fun navTemplates(language: String = ""): String = getLocalizedResource(R.string.nav_templates, language)
    @Composable
    fun navSettings(language: String = ""): String = getLocalizedResource(R.string.nav_settings, language)

    @Composable
    fun welcomeBack(language: String = ""): String = getLocalizedResource(R.string.welcome_back, language)
    @Composable
    fun overviewGrid(language: String = ""): String = getLocalizedResource(R.string.overview_grid, language)
    @Composable
    fun activeFlows(language: String = ""): String = getLocalizedResource(R.string.active_flows, language)
    @Composable
    fun todayRuns(language: String = ""): String = getLocalizedResource(R.string.todays_runs, language)
    @Composable
    fun mostUsedFlows(language: String = ""): String = getLocalizedResource(R.string.most_used_flows, language)
    @Composable
    fun recentFlows(language: String = ""): String = getLocalizedResource(R.string.recent_flows, language)
    @Composable
    fun seeAll(language: String = "", count: Int = 0): String = getLocalizedResource(R.string.see_all, language, count)
    @Composable
    fun predefinedTemplates(language: String = ""): String = getLocalizedResource(R.string.predefined_templates, language)
    @Composable
    fun browseTemplates(language: String = ""): String = getLocalizedResource(R.string.browse_templates, language)
    @Composable
    fun createFlow(language: String = ""): String = getLocalizedResource(R.string.create_flow, language)
    @Composable
    fun selectNetwork(language: String = ""): String = getLocalizedResource(R.string.select_network, language)
    @Composable
    fun noNetworkAvailable(language: String = ""): String = getLocalizedResource(R.string.no_network_available, language)
    @Composable
    fun agentOperations(language: String = ""): String = getLocalizedResource(R.string.agent_operations, language)
    @Composable
    fun subscriberOperations(language: String = ""): String = getLocalizedResource(R.string.subscriber_operations, language)
    @Composable
    fun roleAgent(language: String = ""): String = getLocalizedResource(R.string.role_agent, language)
    @Composable
    fun roleSubscriber(language: String = ""): String = getLocalizedResource(R.string.role_subscriber, language)
    @Composable
    fun roleDefault(language: String = ""): String = getLocalizedResource(R.string.role_default, language)
    @Composable
    fun userRoleLabel(language: String = ""): String = getLocalizedResource(R.string.user_role_label, language)
    @Composable
    fun noRoleFlowsFound(language: String = "", role: String = ""): String = getLocalizedResource(R.string.no_role_flows_found, language, role)
    @Composable
    fun noRoleFlowsSubtitle(language: String = "", role: String = ""): String = getLocalizedResource(R.string.no_role_flows_subtitle, language, role)

    @Composable
    fun flowLibraryTitle(language: String = ""): String = getLocalizedResource(R.string.flow_library_title, language)
    @Composable
    fun search(language: String = ""): String = getLocalizedResource(R.string.search_placeholder, language)
    @Composable
    fun filterAll(language: String = ""): String = getLocalizedResource(R.string.filter_all, language)
    @Composable
    fun noFlowsFound(language: String = ""): String = getLocalizedResource(R.string.no_flows_found, language)
    @Composable
    fun noFlowsSubtitle(language: String = ""): String = getLocalizedResource(R.string.no_flows_subtitle, language)

    @Composable
    fun flowTemplatesTitle(language: String = ""): String = getLocalizedResource(R.string.flow_templates_title, language)
    @Composable
    fun preconfiguredWorkflows(language: String = ""): String = getLocalizedResource(R.string.preconfigured_workflows, language)
    @Composable
    fun countryRegion(language: String = ""): String = getLocalizedResource(R.string.country_region, language)
    @Composable
    fun preview(language: String = ""): String = getLocalizedResource(R.string.preview, language)
    @Composable
    fun useTemplate(language: String = ""): String = getLocalizedResource(R.string.use_template, language)
    @Composable
    fun searchTemplate(language: String = ""): String = getLocalizedResource(R.string.search_template, language)
    @Composable
    fun close(language: String = ""): String = getLocalizedResource(R.string.close, language)

    @Composable
    fun executeFlow(language: String = ""): String = getLocalizedResource(R.string.execute_flow, language)
    @Composable
    fun editBuilder(language: String = ""): String = getLocalizedResource(R.string.edit_builder, language)
    @Composable
    fun duplicate(language: String = ""): String = getLocalizedResource(R.string.duplicate, language)
    @Composable
    fun deleteFlow(language: String = ""): String = getLocalizedResource(R.string.delete_flow, language)
    @Composable
    fun runsCount(language: String = "", count: Int = 0): String = getLocalizedResource(R.string.runs_count, language, count)

    @Composable
    fun settingsTitle(language: String = ""): String = getLocalizedResource(R.string.settings_title, language)
    @Composable
    fun languageLabel(language: String = ""): String = getLocalizedResource(R.string.language_label, language)
    @Composable
    fun languageSubtitle(language: String = ""): String = getLocalizedResource(R.string.language_subtitle, language)
    @Composable
    fun securityTitle(language: String = ""): String = getLocalizedResource(R.string.security_title, language)
    @Composable
    fun autoVaultLabel(language: String = ""): String = getLocalizedResource(R.string.auto_vault_label, language)
    @Composable
    fun appearanceTitle(language: String = ""): String = getLocalizedResource(R.string.appearance_title, language)
    @Composable
    fun darkModeLabel(language: String = ""): String = getLocalizedResource(R.string.dark_mode_label, language)
}

