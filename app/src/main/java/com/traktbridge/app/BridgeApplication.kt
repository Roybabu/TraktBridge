package com.traktbridge.app

import android.app.Application
import android.content.ComponentCallbacks2

class BridgeApplication:Application() {
    override fun onCreate() { super.onCreate(); PlaybackTrackingMigration.apply(this); CrashRecovery.install(this) }
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        if(level>=ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Artwork.trimMemory()
    }
    override fun onLowMemory() { super.onLowMemory(); Artwork.trimMemory() }
}
