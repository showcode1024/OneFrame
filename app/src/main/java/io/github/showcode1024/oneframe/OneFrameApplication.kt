package io.github.showcode1024.oneframe

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

class OneFrameApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(OkHttpClient.Builder().followRedirects(false).build())
        .build()
}
