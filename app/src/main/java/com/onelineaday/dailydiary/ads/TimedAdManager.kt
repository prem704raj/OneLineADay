package com.onelineaday.dailydiary.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.onelineaday.dailydiary.PremiumManager

object TimedAdManager : DefaultLifecycleObserver {

    private const val TAG = "TimedAdManager"

    // 3 minutes = 180 seconds for interstitial ad
    private const val INTERSTITIAL_INTERVAL_MS = 3 * 60 * 1000L

    // 5 minutes = 300 seconds for video ad
    private const val VIDEO_INTERVAL_MS = 5 * 60 * 1000L

    // Minimum gap between any two full-screen ads (45 seconds)
    private const val MIN_AD_GAP_MS = 45 * 1000L

    private var currentActivity: Activity? = null
    private var isForeground = false

    private var lastInterstitialShownTime = 0L
    private var lastVideoShownTime = 0L
    private var lastAnyAdShownTime = 0L

    fun init(activity: Activity, lifecycleOwner: LifecycleOwner) {
        currentActivity = activity
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isForeground = true
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isForeground = false
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        currentActivity = null
    }

    fun notifyAdShown() {
        lastAnyAdShownTime = System.currentTimeMillis()
    }

    private fun checkAndShowTimedAds() {
        // Disabled per user experience & store policy guidelines (no unsolicited full-screen interruptions)
    }
}
