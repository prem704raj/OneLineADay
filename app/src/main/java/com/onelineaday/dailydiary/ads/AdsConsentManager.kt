package com.onelineaday.dailydiary.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object AdsConsentManager {

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val mobileAdsInitialized = AtomicBoolean(false)

    fun gatherConsent(activity: Activity) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                updateState(consentInformation)

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    updateState(consentInformation)
                    initializeAdsIfAllowed(activity.applicationContext)
                }
            },
            { _ ->
                // Do not guess consent. Use the current UMP state.
                updateState(consentInformation)
                initializeAdsIfAllowed(activity.applicationContext)
            }
        )
    }

    private fun updateState(info: ConsentInformation) {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initializeAdsIfAllowed(context: Context) {
        if (!_canRequestAds.value) {
            return
        }

        if (mobileAdsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(context) {
                InterstitialAdManager.loadAd(context)
                RewardedAdManager.loadAd(context)
            }
        }
    }

    fun showPrivacyOptions(
        activity: Activity,
        onComplete: (String?) -> Unit = {}
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            val info = UserMessagingPlatform.getConsentInformation(activity)
            updateState(info)
            initializeAdsIfAllowed(activity.applicationContext)
            onComplete(formError?.message)
        }
    }

    fun resetForDebugOnly(context: Context) {
        if (com.onelineaday.dailydiary.BuildConfig.DEBUG) {
            UserMessagingPlatform.getConsentInformation(context).reset()
            _canRequestAds.value = false
            _privacyOptionsRequired.value = false
        }
    }
}

