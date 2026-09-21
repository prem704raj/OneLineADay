package com.onelineaday.dailydiary.ads

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.onelineaday.dailydiary.R

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-8204679574020840/7695158775"
) {
    val isPremium by com.onelineaday.dailydiary.PremiumManager.isPremium.collectAsState()
    if (isPremium) return
    
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(adUnitId) {
        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad: NativeAd ->
                nativeAd = ad
                isLoading = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e("NativeAdCard", "Failed to load native ad: ${adError.message}")
                    isLoading = false
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(nativeAd) {
        onDispose {
            nativeAd?.destroy()
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (nativeAd != null) {
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                val adView = LayoutInflater.from(ctx)
                    .inflate(R.layout.ad_native_timeline, null) as NativeAdView
                populateNativeAdView(nativeAd!!, adView)
                adView
            },
            update = { adView ->
                nativeAd?.let { populateNativeAdView(it, adView) }
            }
        )
    }
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    // Set the media view.
    adView.mediaView = adView.findViewById<MediaView>(R.id.ad_media)

    // Set other ad assets.
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline)
    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body)
    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action)
    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
    adView.starRatingView = adView.findViewById<RatingBar>(R.id.ad_stars)

    // The headline and mediaContent are guaranteed to be in every NativeAd.
    (adView.headlineView as TextView).text = nativeAd.headline
    adView.mediaView?.mediaContent = nativeAd.mediaContent

    // These assets aren't guaranteed to be in every NativeAd, so we must check them.
    if (nativeAd.body == null) {
        adView.bodyView?.visibility = android.view.View.INVISIBLE
    } else {
        adView.bodyView?.visibility = android.view.View.VISIBLE
        (adView.bodyView as TextView).text = nativeAd.body
    }

    if (nativeAd.callToAction == null) {
        adView.callToActionView?.visibility = android.view.View.INVISIBLE
    } else {
        adView.callToActionView?.visibility = android.view.View.VISIBLE
        (adView.callToActionView as Button).text = nativeAd.callToAction
    }

    if (nativeAd.icon == null) {
        adView.iconView?.visibility = android.view.View.GONE
    } else {
        (adView.iconView as ImageView).setImageDrawable(nativeAd.icon?.drawable)
        adView.iconView?.visibility = android.view.View.VISIBLE
    }

    if (nativeAd.starRating == null) {
        adView.starRatingView?.visibility = android.view.View.INVISIBLE
    } else {
        (adView.starRatingView as RatingBar).rating = nativeAd.starRating!!.toFloat()
        adView.starRatingView?.visibility = android.view.View.VISIBLE
    }

    // Call this method to tell the NativeAdView that you have populated it
    adView.setNativeAd(nativeAd)
}
