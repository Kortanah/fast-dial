package com.example.util

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
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
                    onError("Unexpected credential format received")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In dismissed by user: ${e.message}")
                onCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In error: ${e.message}", e)
                onError(e.localizedMessage ?: "Google Sign-In failed")
            }
        }
    }
}
