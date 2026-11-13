package com.rackz.stackmyrackz.data.remote

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

const val SHEETS_OAUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/spreadsheets"

/** Result of resolving a Google Sheets OAuth 2.0 Bearer access token. */
sealed interface TokenResult {
    data class Success(val accessToken: String, val isManualToken: Boolean = false) : TokenResult
    data class ConsentRequired(val intent: Intent) : TokenResult
    data class NotConfigured(val reason: String) : TokenResult
    data class Error(val message: String, val retryable: Boolean = true) : TokenResult
}

/** Contract for acquiring and invalidating OAuth access tokens for Google Sheets API requests. */
interface AuthTokenProvider {
    suspend fun getAccessToken(): TokenResult

    suspend fun invalidateToken(accessToken: String)
}

/** Resolves OAuth access tokens via manual token override or Android [GoogleAuthUtil]. */
@Singleton
class GoogleAuthTokenProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferencesRepository
) : AuthTokenProvider {

    override suspend fun getAccessToken(): TokenResult {
        val snapshot = syncPreferences.getSnapshot()
        val manualToken = snapshot.manualAccessToken?.trim()?.takeIf { it.isNotEmpty() }
        if (manualToken != null) {
            return TokenResult.Success(accessToken = manualToken, isManualToken = true)
        }

        val email = snapshot.accountEmail?.trim()?.takeIf { it.isNotEmpty() }
            ?: return TokenResult.NotConfigured("Connect a Google account")

        return withContext(Dispatchers.IO) {
            try {
                val account = Account(email, GOOGLE_ACCOUNT_TYPE)
                val token = GoogleAuthUtil.getToken(context, account, SHEETS_OAUTH_SCOPE)
                TokenResult.Success(accessToken = token, isManualToken = false)
            } catch (recoverable: UserRecoverableAuthException) {
                val consentIntent = recoverable.intent
                if (consentIntent != null) {
                    TokenResult.ConsentRequired(consentIntent)
                } else {
                    TokenResult.Error("Google authorization required", retryable = false)
                }
            } catch (authError: GoogleAuthException) {
                TokenResult.Error(
                    authError.message ?: "Google authentication failed",
                    retryable = false
                )
            } catch (ioError: IOException) {
                TokenResult.Error(
                    ioError.message ?: "Network error during authentication",
                    retryable = true
                )
            }
        }
    }

    override suspend fun invalidateToken(accessToken: String) {
        val snapshot = syncPreferences.getSnapshot()
        if (!snapshot.manualAccessToken.isNullOrBlank()) {
            return
        }
        withContext(Dispatchers.IO) {
            try {
                GoogleAuthUtil.clearToken(context, accessToken)
            } catch (_: Throwable) {
                // Best-effort token cache clear before retry.
            }
        }
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
