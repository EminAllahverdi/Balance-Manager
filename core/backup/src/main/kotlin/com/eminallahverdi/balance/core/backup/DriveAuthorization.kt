package com.eminallahverdi.balance.core.backup

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Only the app's own hidden folder in Drive. It can't see anything else the user keeps there. */
private const val APP_FOLDER_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

/** Only files this app made itself, for the accountant's copy. Like the hidden folder, it can't see anything else. */
private const val OWN_FILES_SCOPE = "https://www.googleapis.com/auth/drive.file"

sealed interface DriveAccess {
    data class Granted(val token: String) : DriveAccess

    /** The user has to choose an account or agree, on a screen Google shows. */
    data class NeedsConsent(val screen: IntentSender) : DriveAccess

    /** This build isn't registered with Google, so no account can work until it is. */
    data object NotSetUp : DriveAccess

    data object Unavailable : DriveAccess
}

@Singleton
class DriveAuthorization @Inject constructor(@ApplicationContext private val context: Context) {
    private val client get() = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(APP_FOLDER_SCOPE))).build()
    private val accountantRequest = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(APP_FOLDER_SCOPE), Scope(OWN_FILES_SCOPE))).build()

    suspend fun access(): DriveAccess = authorize(request)

    /** The backup's folder and the files the app makes for the accountant. Google asks once, when the copy is turned on. */
    suspend fun accountantAccess(): DriveAccess = authorize(accountantRequest)

    private suspend fun authorize(request: AuthorizationRequest): DriveAccess = try {
        client.authorize(request).await().toAccess()
    } catch (failure: ApiException) {
        if (failure.statusCode == CommonStatusCodes.DEVELOPER_ERROR) DriveAccess.NotSetUp else DriveAccess.Unavailable
    }

    /** Reads what Google's consent screen sent back. */
    fun accessFrom(result: Intent?): DriveAccess = try {
        client.getAuthorizationResultFromIntent(result).toAccess()
    } catch (failure: ApiException) {
        DriveAccess.Unavailable
    }

    private fun AuthorizationResult.toAccess(): DriveAccess {
        val screen = pendingIntent
        val token = accessToken
        return when {
            hasResolution() && screen != null -> DriveAccess.NeedsConsent(screen.intentSender)
            token != null -> DriveAccess.Granted(token)
            else -> DriveAccess.Unavailable
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
