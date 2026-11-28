package com.example.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class GoogleSignInOutcome(
    val user: FirebaseUser?,
    val cancelled: Boolean = false,
    val errorMessage: String? = null
)

class GoogleAuthHelper(private val context: Context) {

    private val credentialManager by lazy { CredentialManager.create(context) }

    private fun resolveWebClientId(): String {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else ""
    }

    /**
     * Interactive Google Sign-In initiated by a user button tap.
     * Always uses GetSignInWithGoogleOption in a dedicated request.
     */
    suspend fun signInInteractive(activityContext: Context): GoogleSignInOutcome {
        val webClientId = resolveWebClientId()
        if (webClientId.isBlank()) {
            return GoogleSignInOutcome(
                user = null,
                errorMessage = "Konfigurasi Google Sign-In belum tersedia di proyek."
            )
        }
        return try {
            val googleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            handleCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleAuth", "User cancelled Google Sign-In", e)
            GoogleSignInOutcome(user = null, cancelled = true, errorMessage = "Masuk dengan Google dibatalkan.")
        } catch (e: NoCredentialException) {
            Log.w("GoogleAuth", "No Google credentials found on device", e)
            GoogleSignInOutcome(
                user = null,
                errorMessage = "Belum ada akun Google yang login di perangkat ini."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Google Sign-In failed", e)
            GoogleSignInOutcome(
                user = null,
                errorMessage = e.localizedMessage ?: "Gagal terhubung ke Google Sign-In."
            )
        }
    }

    /**
     * Optional silent / authorized-account sign-in attempt using GetGoogleIdOption.
     */
    suspend fun trySilentSignIn(activityContext: Context): GoogleSignInOutcome {
        val existing = Firebase.auth.currentUser
        if (existing != null) return GoogleSignInOutcome(user = existing)

        val webClientId = resolveWebClientId()
        if (webClientId.isBlank()) return GoogleSignInOutcome(user = null)

        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            handleCredential(result.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w("GoogleAuth", "Silent Google Sign-In cancelled", e)
            GoogleSignInOutcome(user = null, cancelled = true)
        } catch (e: NoCredentialException) {
            GoogleSignInOutcome(user = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("GoogleAuth", "Silent Google Sign-In skipped", e)
            GoogleSignInOutcome(user = null)
        }
    }

    private suspend fun handleCredential(credential: androidx.credentials.Credential): GoogleSignInOutcome {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
            return GoogleSignInOutcome(user = authResult.user)
        }
        return GoogleSignInOutcome(user = null, errorMessage = "Tipe kredensial Google tidak valid.")
    }

    suspend fun signOut() = withContext(NonCancellable) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w("GoogleAuth", "clearCredentialState warning", e)
        } finally {
            Firebase.auth.signOut()
        }
    }
}
