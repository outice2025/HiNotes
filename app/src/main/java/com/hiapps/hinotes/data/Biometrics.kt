package com.hiapps.hinotes.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock.
 *
 * Both "Fingerprint" and "Face unlock" are Android biometric classes: the fingerprint row asks
 * for a strong biometric (class 3) falling back to weak, and the face row asks for a weak
 * biometric because most face implementations are class 2. Whether a class is actually present
 * is reported by [BiometricManager], so the settings switches can be disabled honestly instead
 * of pretending to work.
 */
object Biometrics {

    /** Which authenticator class a settings row is asking for. */
    enum class Kind(val authenticators: Int) {
        Fingerprint(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
        ),
        Face(BiometricManager.Authenticators.BIOMETRIC_WEAK),
    }

    /** True when the device can perform this kind of biometric authentication. */
    fun isAvailable(context: Context, kind: Kind): Boolean =
        BiometricManager.from(context).canAuthenticate(kind.authenticators) ==
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
        kind: Kind,
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
            .setAllowedAuthenticators(kind.authenticators)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
