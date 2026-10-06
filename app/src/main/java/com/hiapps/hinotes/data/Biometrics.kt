package com.hiapps.hinotes.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock.
 *
 * Both "Fingerprint" and "Face unlock" are Android biometric classes, and the classes are what
 * decide which sensor the system prompt uses: a fingerprint sensor is class 3 (strong) and most
 * face implementations are class 2 (weak). The fingerprint row therefore asks for
 * [BiometricManager.Authenticators.BIOMETRIC_STRONG] *only* - allowing weak as well, as it
 * previously did, let the system answer the prompt with face recognition on a device that has
 * both, so turning on "Fingerprint" produced a face prompt.
 *
 * Face keeps asking for weak, since that is the class face unlock normally holds. Android offers
 * no way to name the modality outright, so these two classes are the closest thing to it.
 *
 * Whether a class is actually present is reported by [BiometricManager], so the settings switches
 * can be disabled honestly instead of pretending to work.
 */
object Biometrics {

    /** Which authenticator class a settings row is asking for. */
    enum class Kind(val authenticators: Int) {
        Fingerprint(BiometricManager.Authenticators.BIOMETRIC_STRONG),
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
