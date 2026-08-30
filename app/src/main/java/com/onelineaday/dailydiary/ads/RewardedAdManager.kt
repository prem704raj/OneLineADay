package com.onelineaday.dailydiary.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Manages loading and showing rewarded ads for the PDF export feature.
 * 
 * Uses Google's test ad unit ID by default.
 * Replace TEST_REWARDED_AD_UNIT_ID with your real ad unit ID before publishing.
 */
object RewardedAdManager {

    private const val TAG = "RewardedAdManager"

    // Real AdMob ad unit ID
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-8204679574020840/8271044708"

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    /**
     * Pre-loads a rewarded ad so it's ready when the user taps Export.
     * Call this early (e.g. when SettingsScreen opens).
     */
    fun loadAd(context: Context) {
        if (rewardedAd != null || isLoading) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    rewardedAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load: ${error.message}")
                    rewardedAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Returns true if a rewarded ad is ready to show.
     */
    fun isAdReady(): Boolean = rewardedAd != null

    /**
     * Shows the rewarded ad. 
     * [onRewarded] is called when the user earns the reward (watched the ad).
     * [onAdDismissed] is called when the ad is closed (whether rewarded or not).
     * [onAdNotAvailable] is called if no ad is loaded — the caller should 
     * grant the export anyway as a fallback.
     */
    fun showAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onAdDismissed: () -> Unit,
        onAdNotAvailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "Rewarded ad not available, granting access anyway")
            onAdNotAvailable()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed")
                rewardedAd = null
                onAdDismissed()
                // Pre-load the next ad for future use
                loadAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Ad failed to show: ${error.message}")
                rewardedAd = null
                onAdNotAvailable()
                loadAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad shown")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewarded()
        }
    }
}
