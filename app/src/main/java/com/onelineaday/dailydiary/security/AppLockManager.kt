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
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_PIN_FAILED_ATTEMPTS = "pin_failed_attempts"
    const val KEY_PIN_LOCKOUT_UNTIL = "pin_lockout_until"
    const val MAX_FAILED_ATTEMPTS = 5

    sealed class PinVerificationResult {
        data object Success : PinVerificationResult()
        data class Failed(val remainingAttempts: Int) : PinVerificationResult()
        data class LockedOut(val secondsRemaining: Long) : PinVerificationResult()
    }

    fun setPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hashedPin = hashPinSalted(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hashedPin)
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
            .apply()
        com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(context)
    }

    fun getRemainingLockoutSeconds(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockoutUntil = prefs.getLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (now < lockoutUntil) {
            (lockoutUntil - now + 999) / 1000
        } else {
            0L
        }
    }

    fun verifyPinWithThrottling(context: Context, pin: String): PinVerificationResult {
        val remainingLockout = getRemainingLockoutSeconds(context)
        if (remainingLockout > 0) {
            return PinVerificationResult.LockedOut(remainingLockout)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return PinVerificationResult.Failed(0)

        val isMatch = if (stored.startsWith("s1:")) {
            val parts = stored.split(":")
            if (parts.size == 3) {
                val salt = parts[1]
                val expectedHash = parts[2]
                val computedHash = computeHash(salt, pin)
                java.security.MessageDigest.isEqual(
                    computedHash.toByteArray(Charsets.UTF_8),
                    expectedHash.toByteArray(Charsets.UTF_8)
                )
            } else {
                false
            }
        } else {
            // Legacy unsalted hash
            val legacyHash = hashLegacyPin(pin)
            val match = stored == legacyHash
            if (match) {
                // Auto-upgrade to salted hash
                prefs.edit().putString(KEY_PIN_HASH, hashPinSalted(pin)).apply()
            }
            match
        }

        return if (isMatch) {
            prefs.edit()
                .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
                .putLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
                .apply()
            PinVerificationResult.Success
        } else {
            val failedAttempts = prefs.getInt(KEY_PIN_FAILED_ATTEMPTS, 0) + 1
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                val lockoutDuration = 30_000L * (1 shl (failedAttempts - MAX_FAILED_ATTEMPTS).coerceAtMost(3))
                val lockoutUntil = System.currentTimeMillis() + lockoutDuration
                prefs.edit()
                    .putInt(KEY_PIN_FAILED_ATTEMPTS, failedAttempts)
                    .putLong(KEY_PIN_LOCKOUT_UNTIL, lockoutUntil)
                    .apply()
                PinVerificationResult.LockedOut((lockoutDuration + 999) / 1000)
            } else {
                prefs.edit().putInt(KEY_PIN_FAILED_ATTEMPTS, failedAttempts).apply()
                PinVerificationResult.Failed(MAX_FAILED_ATTEMPTS - failedAttempts)
            }
        }
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        return verifyPinWithThrottling(context, pin) is PinVerificationResult.Success
    }

    fun hasPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun removePin(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putInt(KEY_PIN_FAILED_ATTEMPTS, 0)
            .putLong(KEY_PIN_LOCKOUT_UNTIL, 0L)
            .apply()
        com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(context)
    }

    private fun hashPinSalted(pin: String): String {
        val random = java.security.SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hash = computeHash(saltHex, pin)
        return "s1:$saltHex:$hash"
    }

    private fun computeHash(saltHex: String, pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        return digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hashLegacyPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    enum class LockType {
        NONE, BIOMETRIC, PIN, BOTH
    }

    fun getLockType(context: Context): LockType {
        val hasBiometric = isEnabled(context)
        val hasPin = hasPin(context)
        return when {
            hasBiometric && hasPin -> LockType.BOTH
            hasBiometric -> LockType.BIOMETRIC
            hasPin -> LockType.PIN
            else -> LockType.NONE
        }
    }

    /**
     * Class 2+ biometric OR device screen-lock credential.
     *
     * BIOMETRIC_WEAK includes BIOMETRIC_STRONG devices as well.
     */
    const val ALLOWED_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    /**
     * Whether the user enabled biometric diary locking.
     */
    fun isEnabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_LOCK, false)
    }

    /**
     * Enable/disable biometric diary locking.
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
        com.onelineaday.dailydiary.widget.JournalWidget.updateAllWidgets(context)
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
