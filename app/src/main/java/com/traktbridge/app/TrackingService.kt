package com.traktbridge.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class TrackingService: Service() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    private var deviceSync: LocalPlaybackSync?=null
    private var relaySync: RelayPlaybackSync?=null
    private fun updateDeviceSync() {
        val config=if(PlaybackSharing.enabled(this)) RelaySettings.load(this) else null
        if(relaySync?.config!=config) {
            relaySync?.close(); relaySync=null
            if(config!=null) runCatching { RelayPlaybackSync(this,config).also { it.start(); relaySync=it } }.onFailure { DebugTrace.failure("relay-sync",it) }
        }
        val allowed=PlaybackSharing.enabled(this) && PlaybackSharing.localWifi(this)
        if(!allowed) { deviceSync?.close(); deviceSync=null; return }
        if(deviceSync==null) runCatching {
            val sync=LocalPlaybackSync(this)
            try { sync.start(); deviceSync=sync } catch(e:Exception) { sync.close(); throw e }
        }.onFailure { DebugTrace.failure("device-sync",it) }
    }
    private var lastRebind= -60000L
    private val heartbeat=object: Runnable {
        override fun run() {
            if(!prefs.getBoolean("enabled",false)) { stopSelf(); return }
            updateDeviceSync()
            val now=System.currentTimeMillis()
            val last=prefs.getLong("last_sample",0)
            val tick=SystemClock.elapsedRealtime()
            if(TrackingHealthPolicy.shouldRebind(now,last,tick,lastRebind)) {
                lastRebind=tick
                DebugTrace.event("foreground","listener stale; requesting rebind")
                runCatching { android.service.notification.NotificationListenerService.requestRebind(ComponentName(this@TrackingService,PlaybackListener::class.java)) }
            }
            handler.postDelayed(this,15000)
        }
    }
    override fun onBind(intent: Intent?) : IBinder?=null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        DebugTrace.init(this)
        if(intent?.action=="stop" || !prefs.getBoolean("enabled",false)) {
            prefs.edit().putBoolean("enabled",false).apply()
            stopSelf(); return START_NOT_STICKY
        }
        val nm=getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("tracking","Active playback tracking",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,TrackingService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val n=Notification.Builder(this,"tracking").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("TraktBridge tracking active").setContentText("Selected video apps • tap to open").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Stop tracking",stop).build()).build()
        try { if(Build.VERSION.SDK_INT>=34) startForeground(721,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(721,n) } catch(e:Exception) {
            DebugTrace.failure("foreground-start",e); prefs.edit().putBoolean("foreground_running",false).putString("sample_error","Foreground start failed").apply(); handler.removeCallbacks(heartbeat); stopSelf(); return START_NOT_STICKY
        }
        prefs.edit().putBoolean("foreground_running",true).apply()
        DebugTrace.event("foreground","started")
        handler.removeCallbacks(heartbeat); handler.post(heartbeat)
        return START_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        deviceSync?.close(); deviceSync=null
        relaySync?.close(); relaySync=null; RemotePlayback.clear()
        prefs.edit().putBoolean("foreground_running",false).apply()
        DebugTrace.event("foreground","stopped")
        super.onDestroy()
    }
}
