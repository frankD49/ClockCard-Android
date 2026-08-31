package com.kosd.log_inattendancesafeguard.services

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Client-side biometric authentication service.
 *
 * Uses BiometricPrompt (fingerprint, face, iris, or device credential) as a
 * second factor after successful email+password login. No biometric data is
 * collected or sent to the server — this is purely a local device gate.
 *
 * BiometricPrompt requires a FragmentActivity (not a plain Activity) and must
 * be called on the main thread.
 */
class BiometricService(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    /**
     * Whether the device can authenticate with biometrics OR device credential
     * (PIN/pattern/password).
     */
    val isAuthenticationAvailable: Boolean
        get() {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                 BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return biometricManager.canAuthenticate(authenticators) ==
                   BiometricManager.BIOMETRIC_SUCCESS
        }

    /**
     * Whether the device can authenticate with biometrics specifically
     * (fingerprint/face/iris).
     */
    val isBiometricAvailable: Boolean
        get() {
            return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
        }

    /**
     * A human-readable description of what authentication methods are available.
     */
    val authTypeDescription: String
        get() = if (isBiometricAvailable) "Biometric" else "Device Credential"

    /**
     * Prompts the user for biometric/device-credential authentication.
     *
     * Must be called from a FragmentActivity (BiometricPrompt requirement).
     * Returns true on success, false on user cancel.
     *
     * @param activity The FragmentActivity hosting the prompt.
     * @param title The title shown in the biometric dialog.
     * @param subtitle The subtitle shown in the biometric dialog.
     * @param onSuccess Called when authentication succeeds.
     * @param onError Called when authentication fails with an error message.
     * @param onCancel Called when the user cancels authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentication Required",
        subtitle: String = "Authenticate to access ClockCard",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (!isAuthenticationAvailable) {
            // If neither biometrics nor device credential is available, skip the gate
            // (the user has already authenticated with email+password)
            onSuccess()
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> onCancel()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                // Called on each failed attempt (e.g. wrong finger)
                // Don't dismiss the prompt — let the user retry
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setConfirmationRequired(false)
            .build()

        BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
    }
}
