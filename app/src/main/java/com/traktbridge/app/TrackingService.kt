package com.traktbridge.app

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*

class TrackingService: Service() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private val handler=Handler(Looper.getMainLooper())
    private var lastRebind= -60000L
    private val heartbeat=object: Runnable {
        override fun run() {
            if(!prefs.getBoolean("enabled",false)) { stopSelf(); return }
            val now=System.currentTimeMillis()
            val last=prefs.getLong("last_sample",0)
            val tick=SystemClock.elapsedRealtime()
            if((last==0L || now-last>20000 || now<last) && tick-lastRebind>=60000L) {
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
        if(Build.VERSION.SDK_INT>=34) startForeground(721,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(721,n)
        prefs.edit().putBoolean("foreground_running",true).apply()
        DebugTrace.event("foreground","started")
        handler.removeCallbacks(heartbeat); handler.post(heartbeat)
        return START_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(heartbeat)
        prefs.edit().putBoolean("foreground_running",false).apply()
        DebugTrace.event("foreground","stopped")
        super.onDestroy()
    }
}
