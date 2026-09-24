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
import com.onelineaday.dailydiary.PremiumManager
import com.onelineaday.dailydiary.BuildConfig

object RewardedAdManager {

    private const val TAG = "RewardedAdManager"
    private val AD_UNIT_ID = if (BuildConfig.DEBUG)
        "ca-app-pub-3940256099942544/5224354917"  // Google test rewarded
    else
        "ca-app-pub-8204679574020840/8271044708"  // Production

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    fun loadAd(context: Context) {
        if (
            PremiumManager.isPremium.value ||
            !AdsConsentManager.canRequestAds.value ||
            rewardedAd != null ||
            loading
        ) {
            return
        }

        loading = true

        RewardedAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, error.message)
                    rewardedAd = null
                    loading = false
                }
            }
        )
    }

    fun isAdReady(): Boolean =
        !PremiumManager.isPremium.value &&
            AdsConsentManager.canRequestAds.value &&
            rewardedAd != null

    fun showAd(
        activity: Activity,
        onRewarded: () -> Unit,
        onAdDismissed: () -> Unit,
        onAdNotAvailable: () -> Unit
    ) {
        if (PremiumManager.isPremium.value) {
            onAdNotAvailable()
            return
        }

        if (!AdsConsentManager.canRequestAds.value) {
            /*
             * Export must not be blocked because consent prevents ads.
             * Never punish the user for declining advertising consent.
             */
            onAdNotAvailable()
            return
        }

        val ad = rewardedAd
        if (ad == null) {
            onAdNotAvailable()
            loadAd(activity.applicationContext)
            return
        }

        rewardedAd = null
        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                if (rewardEarned) {
                    onRewarded()
                }
                onAdDismissed()
                loadAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, adError.message)
                onAdNotAvailable()
                onAdDismissed()
                loadAd(activity.applicationContext)
            }
        }

        ad.show(activity) {
            rewardEarned = true
        }
    }
}
