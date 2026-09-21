package com.onelineaday.dailydiary.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages loading and showing interstitial ads on a time interval.
 * 
 * Uses Google's test ad unit ID by default.
 * Replace TEST_INTERSTITIAL_AD_UNIT_ID with your real ad unit ID before publishing.
 */
object InterstitialAdManager {

    private const val TAG = "InterstitialAdManager"

    // Real AdMob ad unit ID
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8204679574020840/2394730635"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var isShowing = false
    
    // Show interstitial every N saves/edits
    private const val SAVE_INTERVAL = 3
    private var saveCount = 0

    /**
     * Pre-loads an interstitial ad so it's ready when needed.
     */
    fun loadAd(context: Context) {
        if (com.onelineaday.dailydiary.PremiumManager.isPremium.value) return
        if (interstitialAd != null || isLoading) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded successfully")
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial ad failed to load: ${error.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Tracks a save/edit event and shows an interstitial every N times.
     * [onAdDismissed] is called when the ad is closed (or if it wasn't shown).
     */
    fun onEntrySaved(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (com.onelineaday.dailydiary.PremiumManager.isPremium.value) {
            onAdDismissed()
            return
        }

        saveCount++
        if (saveCount % SAVE_INTERVAL != 0) {
            // Not time to show yet
            onAdDismissed()
            return
        }

        showAd(activity, onAdDismissed)
    }

    /**
     * Shows the interstitial ad unconditionally.
     * 
     * [onAdDismissed] is called when the ad is closed (or if it wasn't shown).
     */
    private fun showAd(activity: Activity, onAdDismissed: () -> Unit = {}) {
        if (isShowing) {
            onAdDismissed()
            return
        }

        val ad = interstitialAd
        
        if (ad == null) {
            // Ad isn't loaded yet
            onAdDismissed()
            return
        }

        isShowing = true

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                isShowing = false
                onAdDismissed()
                // Pre-load the next ad
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Interstitial ad failed to show: ${error.message}")
                interstitialAd = null
                isShowing = false
                onAdDismissed()
                // Try loading again
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad shown")
            }
        }

        ad.show(activity)
    }
}
