package com.onelineaday.dailydiary.security

/**
 * Abstraction over Android Window flag mutations to enable 100% deterministic JVM unit testing.
 */
interface WindowFlagMutator {
    fun setFlags(flags: Int, mask: Int)
    fun clearFlags(flags: Int)
}

/**
 * Production controller managing dynamic FLAG_SECURE window masking based on app lock and lifecycle.
 */
class WindowPrivacyController(
    private val windowMutator: WindowFlagMutator,
    private val onRecentsScreenshotChanged: ((Boolean) -> Unit)? = null
) {

    var isFlagSecureApplied: Boolean = false
        private set

    /**
     * Updates window privacy flags dynamically based on lock state, app lock setting, and foreground status.
     */
    fun updateWindowPrivacy(
        isLocked: Boolean,
        isAppLockEnabled: Boolean,
        isForeground: Boolean = true
    ) {
        val shouldMask = WindowPrivacyEngine.shouldMaskWindow(
            isAppLockEnabled = isAppLockEnabled,
            isSessionUnlocked = !isLocked,
            isForeground = isForeground
        )
        applyMask(shouldMask)
    }

    /**
     * Synchronously invoked in onPause() to mask the Recent Apps / Task Switcher snapshot immediately.
     */
    fun onPause(isAppLockEnabled: Boolean) {
        if (isAppLockEnabled) {
            applyMask(true)
        }
    }

    /**
     * Invoked in onResume() to evaluate whether session remains unlocked within the grace window.
     */
    fun onResume(isAppLockEnabled: Boolean, isSessionUnlocked: Boolean) {
        updateWindowPrivacy(
            isLocked = !isSessionUnlocked,
            isAppLockEnabled = isAppLockEnabled,
            isForeground = true
        )
    }

    private fun applyMask(mask: Boolean) {
        if (mask) {
            windowMutator.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
            isFlagSecureApplied = true
        } else {
            windowMutator.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            isFlagSecureApplied = false
        }
        onRecentsScreenshotChanged?.invoke(!mask)
    }
}
