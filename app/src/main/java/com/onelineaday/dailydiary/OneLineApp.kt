package com.onelineaday.dailydiary

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class OneLineApp : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()

    override fun onCreate() {
        super.onCreate()

        /*
         * Do NOT initialize or preload ads here.
         *
         * UMP needs an Activity to show required forms.
         * MainActivity triggers AdsConsentManager instead.
         */
    }
}
