package com.onelineaday.dailydiary.e2e.engine

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * App Lock & PIN Security Engine conforming to PROJECT.md §F18 and AppLockManager architecture.
 * Evaluates salted hashing, brute-force throttling, and UI/widget masking state.
 */
object AppLockSecurityEngine {

    const val MAX_FAILED_ATTEMPTS = 5
    private val secureRandom = SecureRandom()

    sealed class PinResult {
        object Success : PinResult()
        data class Failed(val remainingAttempts: Int) : PinResult()
        data class LockedOut(val lockoutSeconds: Long) : PinResult()
    }

    fun hashPinSalted(pin: String): String {
        val saltBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        val saltHex = saltBytes.joinToString("") { "%02x".format(it) }
        val hash = computeSha256(saltHex, pin)
        return "s1:$saltHex:$hash"
    }

    fun verifyPinConstantTime(storedHash: String, inputPin: String): Boolean {
        if (!storedHash.startsWith("s1:")) return false
        val parts = storedHash.split(":")
        if (parts.size != 3) return false
        val salt = parts[1]
        val expectedHash = parts[2]
        val computedHash = computeSha256(salt, inputPin)
        return MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun calculateThrottling(failedAttempts: Int): PinResult {
        return if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            PinResult.Failed(remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts)
        } else {
            val exponent = (failedAttempts - MAX_FAILED_ATTEMPTS).coerceAtMost(3)
            val durationSeconds = 30L * (1 shl exponent) // 30s, 60s, 120s, 240s
            PinResult.LockedOut(durationSeconds)
        }
    }

    fun shouldMaskContent(isAppLockEnabled: Boolean, isSessionAuthenticated: Boolean): Boolean {
        return isAppLockEnabled && !isSessionAuthenticated
    }

    private fun computeSha256(saltHex: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(saltHex.toByteArray(Charsets.UTF_8))
        return digest.digest(pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
