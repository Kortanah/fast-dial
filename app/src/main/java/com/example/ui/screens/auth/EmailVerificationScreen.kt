package com.example.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MainViewModel

@Composable
fun EmailVerificationScreen(
    viewModel: MainViewModel,
    email: String,
    pass: String = "",
    onNavigateToDashboard: () -> Unit = {},
    onNavigateToLogin: () -> Unit
) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val isFrench = userSettings.language.equals("French", ignoreCase = true)

    var showResendDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var isCheckingVerification by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableStateOf(60) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(false) }

    // Countdown timer for resend button
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            kotlinx.coroutines.delay(1000L)
            countdownSeconds -= 1
        }
    }

    if (showResendDialog) {
        AlertDialog(
            onDismissRequest = {
                showResendDialog = false
                passwordInput = ""
            },
            title = {
                Text(
                    text = if (isFrench) "Renvoyer l'e-mail de vérification" else "Resend Verification Email",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isFrench) {
                            "Veuillez entrer votre mot de passe pour renvoyer le lien de vérification à $email :"
                        } else {
                            "Please enter your password to resend the verification link to $email:"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(if (isFrench) "Mot de passe" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResendDialog = false
                        isResending = true
                        statusMessage = null
                        viewModel.resendVerificationEmail(email, passwordInput) { success, msg ->
                            isResending = false
                            passwordInput = ""
                            isSuccessStatus = success
                            if (success) {
                                countdownSeconds = 60
                                statusMessage = if (isFrench) "E-mail de vérification renvoyé avec succès !"
                                else "Verification email resent successfully!"
                            } else {
                                statusMessage = msg
                            }
                        }
                    },
                    enabled = passwordInput.isNotBlank() && !isResending
                ) {
                    Text(text = if (isFrench) "Renvoyer" else "Resend")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showResendDialog = false
                    passwordInput = ""
                }) {
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
            .testTag("email_verification_screen")
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
            // Header Icon Container
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MarkEmailUnread,
                    contentDescription = "Email Verification",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = if (isFrench) "Vérifiez Votre E-mail" else "Verify Your Email",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Email Chip / Badge
            if (email.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Description Body
            Text(
                text = if (isFrench) {
                    "Un e-mail de vérification a été envoyé à votre adresse. Veuillez consulter votre boîte de réception ainsi que votre dossier Spams / Courriers indésirables, puis cliquez sur le bouton ci-dessous pour continuer."
                } else {
                    "A verification email has been sent to your address. Please check your inbox as well as your Spam or Junk folder, then tap the button below after verifying."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status message (Feedback)
            statusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSuccessStatus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (isSuccessStatus) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Primary Check Verification Button (Logs in user if verified)
            Button(
                onClick = {
                    val passwordToUse = if (pass.isNotBlank()) pass else passwordInput
                    if (passwordToUse.isNotBlank()) {
                        isCheckingVerification = true
                        statusMessage = null
                        viewModel.login(email, passwordToUse) { success, error ->
                            isCheckingVerification = false
                            if (success) {
                                onNavigateToDashboard()
                            } else if (error?.startsWith("EMAIL_NOT_VERIFIED") == true) {
                                isSuccessStatus = false
                                statusMessage = if (isFrench) {
                                    "Votre e-mail n'est pas encore vérifié. Veuillez cliquer sur le lien dans votre boîte de réception."
                                } else {
                                    "Your email is not verified yet. Please check your inbox and click the verification link."
                                }
                            } else {
                                isSuccessStatus = false
                                statusMessage = error ?: "Verification check failed. Please try again."
                            }
                        }
                    } else {
                        onNavigateToLogin()
                    }
                },
                enabled = !isCheckingVerification && !isResending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("check_verification_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isCheckingVerification) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isFrench) "J'ai Vérifié Mon E-mail" else "I Have Verified My Email",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Resend Verification Email Button with Countdown Timer
            OutlinedButton(
                onClick = {
                    if (pass.isNotBlank()) {
                        isResending = true
                        statusMessage = null
                        viewModel.resendVerificationEmail(email, pass) { success, msg ->
                            isResending = false
                            isSuccessStatus = success
                            if (success) {
                                countdownSeconds = 60
                                statusMessage = if (isFrench) "E-mail de vérification renvoyé avec succès !"
                                else "Verification email resent successfully!"
                            } else {
                                statusMessage = msg
                            }
                        }
                    } else {
                        showResendDialog = true
                    }
                },
                enabled = countdownSeconds == 0 && !isResending && !isCheckingVerification,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("resend_verification_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isResending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (countdownSeconds > 0) {
                            if (isFrench) "Renvoyer dans ${countdownSeconds}s" else "Resend in ${countdownSeconds}s"
                        } else {
                            if (isFrench) "Renvoyer l'E-mail" else "Resend Verification Email"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Link Under the Button to Navigate to Login
            TextButton(
                onClick = onNavigateToLogin,
                modifier = Modifier
                    .testTag("back_to_login_text_link")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (isFrench) "Retour à la page de connexion" else "Back to Sign In",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
