package com.onelineaday.dailydiary

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PremiumManager {
    private const val PREFS_NAME = "premium_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_TEST_MODE = "is_test_mode"

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private var isTestMode = false

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isTestMode = prefs.getBoolean(KEY_TEST_MODE, false)
        _isPremium.value = prefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun setPremium(context: Context, premium: Boolean, testMode: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isTestMode = if (premium) testMode else false
        prefs.edit()
            .putBoolean(KEY_IS_PREMIUM, premium)
            .putBoolean(KEY_TEST_MODE, isTestMode)
            .apply()
        _isPremium.value = premium
    }

    fun isDebugOrTestMode(): Boolean = isTestMode
}

