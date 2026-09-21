package com.onelineaday.dailydiary

import android.app.Application
import com.google.android.gms.ads.MobileAds

import com.onelineaday.dailydiary.ads.InterstitialAdManager
import com.onelineaday.dailydiary.ads.RewardedAdManager

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class OneLineApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
    }
    override fun onCreate() {
        super.onCreate()
        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}
        
        // Pre-load ads
        InterstitialAdManager.loadAd(this)
        RewardedAdManager.loadAd(this)
    }
}
