package com.rombamukiyam.vault.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.rombamukiyam.vault.core.common.BiometricUnavailableReason
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed class BiometricOutcome {
    /** The cipher has been authenticated and may now perform exactly one doFinal. */
    data class Success(val cipher: Cipher) : BiometricOutcome()
    data object Cancelled : BiometricOutcome()
    /** User tapped the negative button ("Use Master Passphrase"). */
    data object UseFallback : BiometricOutcome()
    data object LockedOut : BiometricOutcome()
    data class Error(val code: Int) : BiometricOutcome()
}

/** Thin, testable wrapper over AndroidX BiometricPrompt using STRONG biometrics + CryptoObject only. */
class BiometricAuthenticator(private val context: Context) {

    fun availability(): BiometricUnavailableReason? =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricUnavailableReason.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricUnavailableReason.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricUnavailableReason.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricUnavailableReason.SECURITY_UPDATE_REQUIRED
            else -> BiometricUnavailableReason.UNSUPPORTED
        }

    val isAvailable: Boolean get() = availability() == null

    suspend fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String?,
        negativeButtonText: String,
    ): BiometricOutcome = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (cont.isActive) cont.resume(if (c != null) BiometricOutcome.Success(c) else BiometricOutcome.Error(-1))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!cont.isActive) return
                val outcome = when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricOutcome.UseFallback
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_CANCELED -> BiometricOutcome.Cancelled
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricOutcome.LockedOut
                    else -> BiometricOutcome.Error(errorCode)
                }
                cont.resume(outcome)
            }

            override fun onAuthenticationFailed() {
                // A single non-matching finger: the prompt stays open; nothing to do.
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { subtitle?.let { setSubtitle(it) } }
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        cont.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
