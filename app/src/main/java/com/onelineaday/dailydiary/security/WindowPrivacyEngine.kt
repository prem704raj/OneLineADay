package com.onelineaday.dailydiary.security

/**
 * Pure Kotlin policy engine for Window Privacy (FLAG_SECURE and recents screenshot masking).
 * Decoupled from Android framework classes for 100% JVM unit testability.
 */
object WindowPrivacyEngine {

    /**
     * Determines whether WindowManager.LayoutParams.FLAG_SECURE must be applied.
     *
     * Invariants:
     * - If app lock is disabled: never mask.
     * - If app lock is enabled: mask if backgrounded (!isForeground) or if session is locked (!isSessionUnlocked).
     */
    fun shouldMaskWindow(
        isAppLockEnabled: Boolean,
        isSessionUnlocked: Boolean,
        isForeground: Boolean
    ): Boolean {
        if (!isAppLockEnabled) return false
        return !isForeground || !isSessionUnlocked
    }

    /**
     * Determines whether recents screenshot is permitted on API 33+ (TIRAMISU).
     */
    fun isRecentsScreenshotEnabled(
        isAppLockEnabled: Boolean,
        isSessionUnlocked: Boolean,
        isForeground: Boolean
    ): Boolean {
        return !shouldMaskWindow(isAppLockEnabled, isSessionUnlocked, isForeground)
    }
}
