package com.traktbridge.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Intent
import android.app.PendingIntent
import android.os.Build

class TrackingTile:TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val prefs=getSharedPreferences("tracker",MODE_PRIVATE)
        qsTile?.apply { state=if(prefs.getBoolean("enabled",false)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label="TraktBridge"; updateTile() }
    }
    override fun onClick() {
        super.onClick()
        val prefs=getSharedPreferences("tracker",MODE_PRIVATE)
        if(prefs.getBoolean("enabled",false)) {
            prefs.edit().putBoolean("enabled",false).apply()
            stopService(Intent(this,TrackingService::class.java)); onStartListening()
        } else {
            // Start from a visible activity, not a restricted background foreground-service launch.
            val intent=Intent(this,MainActivity::class.java).setAction("com.traktbridge.app.OPEN_TRACKING").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,91,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        }
    }
}
