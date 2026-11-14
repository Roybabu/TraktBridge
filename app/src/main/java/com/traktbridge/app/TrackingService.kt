package com.traktbridge.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class TrackingService: Service() {
    companion object { @Volatile var running=false; private set }
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    private var deviceSync: LocalPlaybackSync?=null
    private var relaySync: RelayPlaybackSync?=null
    private var lastPeers=emptySet<String>()
    private var localNetwork:android.net.Network?=null
    private var networkRegistered=false
    private val connectivity by lazy { getSystemService(android.net.ConnectivityManager::class.java) }
    private val networkCallback=object:android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network:android.net.Network) { handler.post { if(running) updateDeviceSync() } }
        override fun onLost(network:android.net.Network) { handler.post { if(running) updateDeviceSync() } }
        override fun onCapabilitiesChanged(network:android.net.Network,caps:android.net.NetworkCapabilities) { handler.post { if(running) updateDeviceSync() } }
    }
    private fun updateDeviceSync() {
        val peers=DevicePairing.peers(this).map { it.id }.toSet()
        if(peers!=lastPeers) {
            deviceSync?.close(); deviceSync=null
            relaySync?.close(); relaySync=null
            RemotePlayback.clear(); lastPeers=peers
        }
        if(deviceSync?.isClosed()==true) deviceSync=null
        val config=if(PlaybackSharing.enabled(this) && peers.isNotEmpty()) RelaySettings.load(this) else null
        if(relaySync?.config!=config) {
            relaySync?.close(); relaySync=null
            if(config!=null) runCatching { RelayPlaybackSync(this,config).also { it.start(); relaySync=it } }.onFailure { DebugTrace.failure("relay-sync",it) }
        }
        val network=if(PlaybackSharing.enabled(this) && peers.isNotEmpty()) DeviceNetwork.local(this) else null
        if(network!=localNetwork) { deviceSync?.close(); deviceSync=null; RemotePlayback.clearLocal(); localNetwork=network }
        val allowed=network!=null
        if(!allowed) { deviceSync?.close(); deviceSync=null; return }
        if(deviceSync==null) runCatching {
            val sync=LocalPlaybackSync(this)
            try { sync.start(); deviceSync=sync } catch(e:Exception) { sync.close(); throw e }
        }.onFailure { DebugTrace.failure("device-sync",it) }
    }
    private var lastRebind= -60000L
    private var attempts=0
    private var lastStatus=""
    private fun notification():Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val repair=PendingIntent.getActivity(this,2,Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,TrackingService::class.java).setAction("stop"),PendingIntent.FLAG_IMMUTABLE)
        val status=TrackingRecovery.status(this)
        return Notification.Builder(this,"tracking").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("TraktBridge • "+status).setContentText(if(status=="Tracking active") "YouTube playback monitoring" else "Check notification access if recovery fails").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Repair tracking",repair).build()).addAction(Notification.Action.Builder(null,"Stop tracking",stop).build()).build()
    }
    private val heartbeat=object: Runnable {
        override fun run() {
            if(!prefs.getBoolean("enabled",false)) { stopSelf(); return }
            try {
                val now=System.currentTimeMillis()
                val age=now-prefs.getLong("last_sample",0)
                val tick=SystemClock.elapsedRealtime()
                val connected=PlaybackListener.liveConnected()
                val granted=TrackingRecovery.listenerGranted(this@TrackingService)
                val healthy=connected && age in 0..45000 && prefs.getString("sample_error",null)==null
                if(healthy) {
                    if(attempts>0) DebugTrace.event("recovery","Verified fresh playback sampling after "+attempts+" attempts")
                    attempts=0
                } else if(granted && tick-lastRebind>=RecoveryRetryPolicy.delay(attempts)) {
                    lastRebind=tick
                    attempts=(attempts+1).coerceAtMost(20)
                    if(!connected) {
                        prefs.edit().putBoolean("listener_connected",false).apply()
                        TrackingRecovery.clearObservation(this@TrackingService)
                        TrackingRecovery.request(this@TrackingService,"watchdog attempt "+attempts)
                    } else if(attempts<=1) PlaybackListener.wake()
                    else PlaybackListener.reconnect()
                    DebugTrace.event("recovery","attempt="+attempts+" connected="+connected+" ageMs="+age)
                }
                prefs.edit().putInt("recovery_attempts",attempts).apply()
                val status=TrackingRecovery.status(this@TrackingService)
                if(status!=lastStatus) {
                    lastStatus=status
                    getSystemService(NotificationManager::class.java).notify(721,notification())
                }
                runCatching { updateDeviceSync() }.onFailure { DebugTrace.failure("device-sync-heartbeat",it) }
            } catch(e:Exception) { DebugTrace.failure("tracking-watchdog",e) }
            finally { handler.postDelayed(this,15000) }
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
        val n=notification()
        try { if(Build.VERSION.SDK_INT>=34) startForeground(721,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(721,n) } catch(e:Exception) {
            DebugTrace.failure("foreground-start",e); prefs.edit().putBoolean("foreground_running",false).putString("sample_error","Foreground start failed").apply(); handler.removeCallbacks(heartbeat); stopSelf(); return START_NOT_STICKY
        }
        running=true
        if(!networkRegistered) runCatching { connectivity.registerDefaultNetworkCallback(networkCallback); networkRegistered=true }
        prefs.edit().putBoolean("foreground_running",true).putLong("foreground_started_elapsed",SystemClock.elapsedRealtime()).apply()
        DebugTrace.event("foreground","started")
        if(!PlaybackListener.wake()) TrackingRecovery.request(this,"foreground started")
        handler.removeCallbacks(heartbeat); handler.post(heartbeat)
        return START_STICKY
    }
    override fun onDestroy() {
        running=false
        if(networkRegistered) { runCatching { connectivity.unregisterNetworkCallback(networkCallback) }; networkRegistered=false }
        handler.removeCallbacks(heartbeat)
        deviceSync?.close(); deviceSync=null
        relaySync?.close(); relaySync=null; RemotePlayback.clear()
        prefs.edit().putBoolean("foreground_running",false).remove("foreground_started_elapsed").apply()
        if(!prefs.getBoolean("enabled",false)) TrackingRecovery.clearObservation(this)
        DebugTrace.event("foreground","stopped")
        super.onDestroy()
    }
}
