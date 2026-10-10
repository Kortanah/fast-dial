package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object GoogleAuthHelper {
    private const val TAG = "GoogleAuthHelper"

    fun getWebClientId(context: Context): String {
        return try {
            val configClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            if (!configClientId.isNullOrBlank()) {
                configClientId
            } else {
                context.getString(R.string.default_web_client_id)
            }
        } catch (_: Throwable) {
            try {
                context.getString(R.string.default_web_client_id)
            } catch (_: Throwable) {
                ""
            }
        }
    }

    fun launchGoogleSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (idToken: String, displayName: String?, email: String?) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val activity = context.findActivity() ?: run {
            onError("Activity context required for Google Sign-In")
            return
        }

        val clientId = getWebClientId(context)
        if (clientId.isBlank() || clientId.contains("YOUR_WEB_CLIENT_ID")) {
            onError("Invalid Google Web Client ID configured. Please check your settings.")
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                    onSuccess(googleIdToken.idToken, googleIdToken.displayName, googleIdToken.id)
                } else {
                    onError("Unexpected credential format received from Google Sign-In.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In dismissed by user: ${e.message}")
                onCancelled()
            } catch (e: NoCredentialException) {
                Log.e(TAG, "No Google accounts found on device: ${e.message}", e)
                onError("No Google accounts found on this device. Please add a Google account in Android settings.")
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e(TAG, "Credential provider configuration error: ${e.message}", e)
                onError("Credential provider configuration error. Google Play Services may need an update.")
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In error: ${e.message}", e)
                onError("Google Sign-In failed: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }
}
