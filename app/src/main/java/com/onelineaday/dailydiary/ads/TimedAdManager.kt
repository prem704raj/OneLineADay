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

    private val handler = Handler(Looper.getMainLooper())

    private val checkAdsRunnable = object : Runnable {
        override fun run() {
            if (isForeground) {
                checkAndShowTimedAds()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    fun init(activity: Activity, lifecycleOwner: LifecycleOwner) {
        currentActivity = activity
        lifecycleOwner.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isForeground = true
        val now = System.currentTimeMillis()
        if (lastInterstitialShownTime == 0L) lastInterstitialShownTime = now
        if (lastVideoShownTime == 0L) lastVideoShownTime = now

        handler.removeCallbacks(checkAdsRunnable)
        handler.postDelayed(checkAdsRunnable, 1000L)
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isForeground = false
        handler.removeCallbacks(checkAdsRunnable)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        currentActivity = null
        handler.removeCallbacks(checkAdsRunnable)
    }

    fun notifyAdShown() {
        lastAnyAdShownTime = System.currentTimeMillis()
    }

    private fun checkAndShowTimedAds() {
        val activity = currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (PremiumManager.isPremium.value || !AdsConsentManager.canRequestAds.value) return

        val now = System.currentTimeMillis()

        // Ensure minimum gap between full-screen ads
        if (now - lastAnyAdShownTime < MIN_AD_GAP_MS) {
            return
        }

        // Check 5-minute video ad
        if (now - lastVideoShownTime >= VIDEO_INTERVAL_MS) {
            Log.d(TAG, "5 minutes in-app reached, requesting video ad")
            if (RewardedAdManager.isAdReady()) {
                lastVideoShownTime = now
                lastAnyAdShownTime = now
                RewardedAdManager.showAd(
                    activity = activity,
                    onRewarded = {},
                    onAdDismissed = {
                        notifyAdShown()
                    },
                    onAdNotAvailable = {
                        // Fallback to interstitial if video fails
                        if (InterstitialAdManager.isAdReady()) {
                            InterstitialAdManager.showAd(activity) {
                                notifyAdShown()
                            }
                        }
                    }
                )
                return
            } else {
                RewardedAdManager.loadAd(activity)
            }
        }

        // Check 3-minute interstitial ad
        if (now - lastInterstitialShownTime >= INTERSTITIAL_INTERVAL_MS) {
            Log.d(TAG, "3 minutes in-app reached, requesting interstitial ad")
            if (InterstitialAdManager.isAdReady()) {
                lastInterstitialShownTime = now
                lastAnyAdShownTime = now
                InterstitialAdManager.showAd(activity) {
                    notifyAdShown()
                }
            } else {
                InterstitialAdManager.loadAd(activity)
            }
        }
    }
}
