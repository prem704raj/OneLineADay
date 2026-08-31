package com.onelineaday.dailydiary.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Central authentication layer for One Line A Day.
 *
 * App Lock is intentionally based on Android's trusted authentication:
 *
 * - Fingerprint
 * - Face authentication
 * - Device PIN
 * - Device pattern
 * - Device password
 *
 * We do not store our own password/PIN.
 */
object AppLockManager {

    const val PREFS_NAME = "settings"
    const val KEY_APP_LOCK = "app_lock"

    /**
     * Class 2+ biometric OR device screen-lock credential.
     *
     * BIOMETRIC_WEAK includes BIOMETRIC_STRONG devices as well.
     */
    const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether the user enabled diary locking.
     */
    fun isEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK, false)
    }

    /**
     * Enable/disable diary locking.
     *
     * This should only be called after successful authentication.
     */
    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_LOCK, enabled)
            .apply()
    }

    /**
     * Returns true when Android has at least one usable authentication method.
     *
     * This can be:
     * - biometric
     * - PIN
     * - pattern
     * - password
     */
    fun canAuthenticate(context: Context): Boolean {
        val result = BiometricManager
            .from(context)
            .canAuthenticate(ALLOWED_AUTHENTICATORS)

        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * More descriptive status for UI/error handling.
     */
    fun getAuthenticationAvailability(
        context: Context
    ): AuthenticationAvailability {

        return when (
            BiometricManager
                .from(context)
                .canAuthenticate(ALLOWED_AUTHENTICATORS)
        ) {

            BiometricManager.BIOMETRIC_SUCCESS ->
                AuthenticationAvailability.AVAILABLE

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                AuthenticationAvailability.NOT_ENROLLED

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                AuthenticationAvailability.NO_HARDWARE

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                AuthenticationAvailability.TEMPORARILY_UNAVAILABLE

            else ->
                AuthenticationAvailability.UNAVAILABLE
        }
    }

    /**
     * Displays Android's secure authentication prompt.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {

        if (!canAuthenticate(activity)) {
            onError(
                "Set up a fingerprint, face unlock, PIN, pattern, or password in Android settings."
            )
            return
        }

        val executor =
            ContextCompat.getMainExecutor(activity)

        val biometricPrompt =
            BiometricPrompt(
                activity,
                executor,
                object :
                    BiometricPrompt.AuthenticationCallback() {

                    override fun onAuthenticationSucceeded(
                        result:
                            BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)

                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()

                        /*
                         * Do NOT unlock here.
                         *
                         * Also don't show a Toast every time a fingerprint
                         * scan fails. Android's authentication dialog already
                         * gives the user feedback and lets them retry.
                         */
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        super.onAuthenticationError(
                            errorCode,
                            errString
                        )

                        when (errorCode) {

                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                                onCancelled()
                            }

                            else -> {
                                onError(errString.toString())
                            }
                        }
                    }
                }
            )

        try {

            val promptInfo =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)

                    /*
                     * VERY IMPORTANT:
                     *
                     * This lets the user authenticate with:
                     *
                     * - fingerprint
                     * - compatible face unlock
                     * - PIN
                     * - pattern
                     * - password
                     */
                    .setAllowedAuthenticators(
                        ALLOWED_AUTHENTICATORS
                    )

                    /*
                     * DO NOT call:
                     *
                     * setNegativeButtonText(...)
                     *
                     * because DEVICE_CREDENTIAL is enabled.
                     */
                    .build()

            biometricPrompt.authenticate(promptInfo)

        } catch (exception: IllegalArgumentException) {

            onError(
                exception.message
                    ?: "Authentication is unavailable."
            )
        }
    }
}

enum class AuthenticationAvailability {

    AVAILABLE,

    NOT_ENROLLED,

    NO_HARDWARE,

    TEMPORARILY_UNAVAILABLE,

    UNAVAILABLE
}
