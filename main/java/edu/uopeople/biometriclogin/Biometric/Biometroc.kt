package edu.uopeople.biometriclogin.Biometric

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest


fun generateToken(biometricData: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(biometricData)
    return hash.joinToString("") { "%02x".format(it) }
}

fun captureBiometric(
    context: FragmentActivity,
    onSuccess: (ByteArray) -> Unit,
    onFailure: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)

    val biometricPrompt = BiometricPrompt(
        context,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val biometricData = result.cryptoObject?.cipher?.doFinal() ?: return
                onSuccess(biometricData)
            }

            override fun onAuthenticationFailed() {
                onFailure("Authentication failed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Authentication")
        .setSubtitle("Authenticate using your biometric credential")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}
