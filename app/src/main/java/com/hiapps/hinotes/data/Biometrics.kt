package com.hiapps.hinotes.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock.
 *
 * One switch is offered - face unlock - and it asks for
 * [BiometricManager.Authenticators.BIOMETRIC_WEAK], the class face implementations normally hold.
 * Android gives an app no way to name a modality outright: the authenticator class is the only
 * lever, and it is a lower bound rather than an exact match, so a device with a fingerprint (class
 * 3) enrolled satisfies a weak request with the fingerprint instead. What the class does buy is
 * honesty about availability: [isAvailable] is false where no such authenticator exists, so the
 * switch can be disabled rather than opening a prompt that cannot succeed.
 */
object Biometrics {

    /** The authenticator class the face-unlock switch asks for. */
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** True when the device can perform a biometric authentication. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Shows the system biometric prompt.
     *
     * @param onSuccess invoked on the main thread once the user authenticates.
     * @param onFailure invoked with a user-presentable reason when authentication cannot run
     *   or is rejected; errors that merely mean "cancelled" are reported as null.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeLabel: String,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFailure(if (cancelled) null else errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // A single non-matching attempt; the prompt stays open for a retry.
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeLabel)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
