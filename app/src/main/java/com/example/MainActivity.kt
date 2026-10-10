package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.RegisterScreen
import com.example.ui.screens.builder.FlowBuilderScreen
import com.example.ui.screens.create.CreateFlowScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.execute.ExecuteFlowScreen
import com.example.ui.screens.library.FlowLibraryScreen
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.security.LockScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.FastDailTheme
import com.example.ui.viewmodel.MainViewModel
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import java.util.Locale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.fragment.app.FragmentActivity
import com.example.gate.GateManager

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (e: Exception) {
            // Ignore if already initialized
        }
        com.example.service.UssdAccessibilityService.appContextRef = applicationContext
        com.example.util.AdsManager.initialize(applicationContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val userSettings by mainViewModel.userSettings.collectAsStateWithLifecycle()
            val isGateActive by GateManager.isGateActive.collectAsStateWithLifecycle()

            val isDarkTheme = if (userSettings.useSystemTheme) isSystemInDarkTheme() else userSettings.isDarkModeEnabled

            val activityInstance = this
            val activeLocale = remember(userSettings.language) {
                if (userSettings.language.equals("French", ignoreCase = true) || userSettings.language.equals("fr", ignoreCase = true)) {
                    Locale.FRENCH
                } else if (userSettings.language.equals("English", ignoreCase = true) || userSettings.language.equals("en", ignoreCase = true)) {
                    Locale.ENGLISH
                } else {
                    Locale.getDefault()
                }
            }
            val localizedConfig = remember(activeLocale) {
                val config = Configuration(resources.configuration)
                config.setLocale(activeLocale)
                config
            }
            val localizedContext = remember(activityInstance, activeLocale) {
                object : ContextWrapper(activityInstance) {
                    private val res = activityInstance.createConfigurationContext(localizedConfig).resources
                    override fun getResources(): android.content.res.Resources = res
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalActivityResultRegistryOwner provides activityInstance
            ) {
                FastDailTheme(darkTheme = isDarkTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isGateActive) {
                                            Modifier.blur(16.dp)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                FastDailApp(mainViewModel = mainViewModel)
                            }

                            if (isGateActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.service.UssdAccessibilityService.refreshServiceState(applicationContext)
        GateManager.checkAndVerifyPendingAction(this)
        GateManager.reEvaluateGate(this)
    }
}

private fun Context.findActivity(): FragmentActivity? {
    var cur: Context? = this
    while (cur != null) {
        if (cur is FragmentActivity) return cur
        cur = (cur as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
fun FastDailApp(mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fragmentActivity = context.findActivity()

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null && fragmentActivity != null) {
                GateManager.attachListener(fragmentActivity, uid)
            }
        } else {
            fragmentActivity?.let { GateManager.detachListener(it) }
        }
    }

    LaunchedEffect(authState.isMultipleDeviceLoginDetected, authState.isAccountBlocked, authState.isAuthenticated) {
        val currentRoute = navController.currentDestination?.route
        if (authState.isMultipleDeviceLoginDetected || authState.isAccountBlocked || (!authState.isAuthenticated && currentRoute != null && currentRoute != Screen.Splash.route && currentRoute != Screen.Onboarding.route)) {
            if (currentRoute != Screen.Login.route && currentRoute != Screen.Register.route && currentRoute != Screen.ResetPassword.route && currentRoute != Screen.EmailVerification.route && currentRoute?.startsWith("email_verification") != true) {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    val onNavigateWithAds: (String) -> Unit = { route ->
        if (route == Screen.Settings.route) {
            com.example.util.AdsManager.showInterstitialAd(context) {
                navController.navigate(route)
            }
        } else {
            navController.navigate(route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250)) { it / 6 } },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
        popEnterTransition = { fadeIn(animationSpec = tween(250)) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { it / 6 } }
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                viewModel = mainViewModel,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = mainViewModel,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = mainViewModel,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToResetPassword = {
                    navController.navigate(Screen.ResetPassword.route)
                },
                onNavigateToEmailVerification = { email, pass ->
                    navController.navigate(Screen.EmailVerification.createRoute(email, pass))
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = mainViewModel,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onNavigateToEmailVerification = { email, pass ->
                    navController.navigate(Screen.EmailVerification.createRoute(email, pass))
                }
            )
        }

        composable(Screen.ResetPassword.route) {
            com.example.ui.screens.auth.ResetPasswordScreen(
                viewModel = mainViewModel,
                onNavigateToLogin = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.EmailVerification.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType; defaultValue = "" },
                navArgument("pass") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val rawEmail = backStackEntry.arguments?.getString("email") ?: ""
            val rawPass = backStackEntry.arguments?.getString("pass") ?: ""
            val emailArg = try { java.net.URLDecoder.decode(rawEmail, "UTF-8") } catch (e: Exception) { rawEmail }
            val passArg = try { java.net.URLDecoder.decode(rawPass, "UTF-8") } catch (e: Exception) { rawPass }
            com.example.ui.screens.auth.EmailVerificationScreen(
                viewModel = mainViewModel,
                email = emailArg,
                pass = passArg,
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                viewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(Screen.CreateFlow.route) {
            CreateFlowScreen(
                viewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(
            route = Screen.FlowBuilder.route,
            arguments = listOf(navArgument("flowId") { type = NavType.StringType })
        ) { backStackEntry ->
            val flowId = backStackEntry.arguments?.getString("flowId")
            FlowBuilderScreen(
                flowId = flowId,
                mainViewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(
            route = Screen.ExecuteFlow.route,
            arguments = listOf(navArgument("flowId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val flowId = backStackEntry.arguments?.getString("flowId")
            ExecuteFlowScreen(
                initialFlowId = flowId,
                mainViewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(Screen.FlowLibrary.route) {
            FlowLibraryScreen(
                viewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(Screen.Templates.route) {
            com.example.ui.screens.templates.TemplatesScreen(
                viewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = mainViewModel,
                onNavigate = onNavigateWithAds
            )
        }

        composable(Screen.Lock.route) {
            LockScreen(
                viewModel = mainViewModel,
                onUnlockSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Lock.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
