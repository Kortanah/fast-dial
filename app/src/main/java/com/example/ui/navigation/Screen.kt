package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object ResetPassword : Screen("reset_password")
    object EmailVerification : Screen("email_verification?email={email}&pass={pass}") {
        fun createRoute(email: String = "", pass: String = ""): String {
            val encodedEmail = try { java.net.URLEncoder.encode(email, "UTF-8") } catch (e: Exception) { email }
            val encodedPass = try { java.net.URLEncoder.encode(pass, "UTF-8") } catch (e: Exception) { pass }
            return "email_verification?email=$encodedEmail&pass=$encodedPass"
        }
    }
    object Dashboard : Screen("dashboard")
    object CreateFlow : Screen("create_flow")
    object FlowBuilder : Screen("flow_builder/{flowId}") {
        fun createRoute(flowId: String) = "flow_builder/$flowId"
    }
    object ExecuteFlow : Screen("execute_flow?flowId={flowId}") {
        fun createRoute(flowId: String = "") = if (flowId.isEmpty()) "execute_flow" else "execute_flow?flowId=$flowId"
    }
    object FlowLibrary : Screen("flow_library")
    object Templates : Screen("templates")
    object Settings : Screen("settings")
    object Lock : Screen("lock")
}
