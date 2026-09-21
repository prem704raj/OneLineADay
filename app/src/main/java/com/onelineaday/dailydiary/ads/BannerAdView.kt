package com.onelineaday.dailydiary.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = "ca-app-pub-8204679574020840/5829744461",
) {
    val isPremium by com.onelineaday.dailydiary.PremiumManager.isPremium.collectAsState()
    if (isPremium) return
    
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
