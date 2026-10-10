package com.example.ui.screens.auth

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.viewmodel.MainViewModel
import com.example.util.GoogleAuthHelper

@Composable
fun LoginScreen(
    viewModel: MainViewModel,
    onNavigateToDashboard: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    onNavigateToResetPassword: () -> Unit = {},
    onNavigateToEmailVerification: (String, String) -> Unit = { _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isFrench = userSettings.language.equals("French", ignoreCase = true)
    val errAuthFailed = stringResource(R.string.error_auth_failed)

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var googleErrorDialogMessage by remember { mutableStateOf<String?>(null) }
    var showDeviceOverrideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
        rememberMe = prefs.getBoolean("rememberMe", false)
        if (rememberMe) {
            email = prefs.getString("email", "") ?: ""
        }
    }

    if (googleErrorDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { googleErrorDialogMessage = null },
            title = {
                Text(
                    text = if (isFrench) "Erreur de Connexion Google" else "Google Sign-In Error",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = googleErrorDialogMessage!!)
            },
            confirmButton = {
                Button(onClick = { googleErrorDialogMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (authState.isMultipleDeviceLoginDetected) {
        AlertDialog(
            onDismissRequest = { viewModel.clearMultipleDeviceLoginFlag() },
            title = {
                Text(
                    text = if (isFrench) "Session Expirée" else "Session Expired",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isFrench) {
                        "Vous avez été déconnecté car votre compte est connecté sur un autre appareil."
                    } else {
                        "You have been logged out because your account is signed in on another device."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearMultipleDeviceLoginFlag() }
                ) {
                    Text(text = if (isFrench) "Compris" else "OK")
                }
            }
        )
    }

    if (authState.isAccountBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.clearAccountBlockedFlag() },
            title = {
                Text(
                    text = if (isFrench) "Compte Bloqué" else "Account Blocked",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Text(
                    text = authState.blockReason.ifEmpty {
                        if (isFrench) "Votre compte a été bloqué. Vous n'avez plus accès au service. Si vous pensez qu'il s'agit d'une erreur, veuillez contacter le support."
                        else "Your account has been blocked. You no longer have access to the service. If you believe this was done in error, please contact support."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearAccountBlockedFlag() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = if (isFrench) "Compris" else "OK")
                }
            }
        )
    }

    if (showDeviceOverrideDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceOverrideDialog = false },
            title = {
                Text(
                    text = if (isFrench) "Session Active Détectée" else "Active Session Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isFrench) {
                        "Ce compte est actuellement connecté sur un autre appareil. Voulez-vous vous connecter ici et déconnecter l'autre appareil ?"
                    } else {
                        "This account is currently active on another device. Signing in here will sign out your other device. Do you wish to proceed?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeviceOverrideDialog = false
                        isLoading = true
                        viewModel.login(email, password, forceOverride = true) { success, error ->
                            isLoading = false
                            if (success) {
                                val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                                prefs.edit().apply {
                                    putBoolean("rememberMe", rememberMe)
                                    if (rememberMe) {
                                        putString("email", email)
                                    } else {
                                        remove("email")
                                    }
                                    apply()
                                }
                                if (viewModel.authState.value.needsOnboarding) {
                                    onNavigateToOnboarding()
                                } else {
                                    onNavigateToDashboard()
                                }
                            } else if (error?.startsWith("ACCOUNT_BLOCKED:") == true) {
                                errorMessage = if (isFrench) "Votre compte a été bloqué" else "Your account has been blocked"
                            } else {
                                errorMessage = error
                            }
                        }
                    }
                ) {
                    Text(text = if (isFrench) "Se Connecter Ici" else "Sign In Here")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeviceOverrideDialog = false }) {
                    Text(text = if (isFrench) "Annuler" else "Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .testTag("login_screen")
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Logo
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFC107)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_custom),
                    contentDescription = "Fast Dial Logo",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.login_welcome_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.login_welcome_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Primary Google Action: Continue with Google
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !isGoogleLoading && !isLoading) {
                        isGoogleLoading = true
                        errorMessage = null
                        googleErrorDialogMessage = null
                        GoogleAuthHelper.launchGoogleSignIn(
                            context = context,
                            scope = coroutineScope,
                            onSuccess = { idToken, _, _ ->
                                viewModel.signInWithGoogle(idToken) { success, needsOnboarding, errorMsg ->
                                    isGoogleLoading = false
                                    if (success) {
                                        if (needsOnboarding) {
                                            onNavigateToOnboarding()
                                        } else {
                                            onNavigateToDashboard()
                                        }
                                    } else {
                                        googleErrorDialogMessage = errorMsg ?: errAuthFailed
                                    }
                                }
                            },
                            onError = { error ->
                                isGoogleLoading = false
                                googleErrorDialogMessage = error
                            },
                            onCancelled = {
                                isGoogleLoading = false
                            }
                        )
                    }
                    .testTag("continue_with_google_button"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isGoogleLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_compass),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.continue_with_google),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = stringResource(R.string.or_divider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))

            // Email Input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text(stringResource(R.string.email_address_label)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text(stringResource(R.string.password_label)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle Password"
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberMe = !rememberMe }
                ) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { checked ->
                            rememberMe = checked
                        },
                        modifier = Modifier.testTag("remember_me_checkbox")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.remember_me_label), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onNavigateToResetPassword, modifier = Modifier.testTag("forgot_password_button")) {
                    Text(text = stringResource(R.string.forgot_password_label), style = MaterialTheme.typography.bodySmall)
                }
            }

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val errInvalidCredentials = stringResource(R.string.error_invalid_credentials)
            val errInvalidEmail = stringResource(R.string.error_invalid_email)

            Button(
                onClick = {
                    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]+$".toRegex()
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = errInvalidCredentials
                        return@Button
                    }
                    if (!email.matches(emailRegex)) {
                        errorMessage = errInvalidEmail
                        return@Button
                    }
                    isLoading = true
                    viewModel.login(email, password) { success, error ->
                        isLoading = false
                        if (success) {
                            val prefs = context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                Boolean
                                putBoolean("rememberMe", rememberMe)
                                if (rememberMe) {
                                    putString("email", email)
                                } else {
                                    remove("email")
                                }
                                apply()
                            }
                            if (viewModel.authState.value.needsOnboarding) {
                                onNavigateToOnboarding()
                            } else {
                                onNavigateToDashboard()
                            }
                        } else if (error?.startsWith("EMAIL_NOT_VERIFIED") == true) {
                            val unverifiedEmail = error.substringAfter("EMAIL_NOT_VERIFIED:").ifEmpty { email }
                            onNavigateToEmailVerification(unverifiedEmail, password)
                        } else if (error == "CONFIRMATION_REQUIRED") {
                            showDeviceOverrideDialog = true
                        } else if (error?.startsWith("ACCOUNT_BLOCKED:") == true) {
                            errorMessage = if (isFrench) "Votre compte a été bloqué" else "Your account has been blocked"
                        } else {
                            errorMessage = error ?: errAuthFailed
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_in_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.sign_in_button),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToRegister,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("goto_register_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_new_account_button),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
