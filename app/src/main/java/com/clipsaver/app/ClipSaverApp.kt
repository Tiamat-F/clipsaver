package com.clipsaver.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipSaverApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // First launch unpacks the bundled Python/yt-dlp; start it early so the
        // first download doesn't wait on it.
        appScope.launch { runCatching { Downloader.init(this@ClipSaverApp) } }
    }
}
