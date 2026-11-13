package com.traktbridge.app

import android.content.Context
import android.content.ComponentName
import android.app.NotificationManager
import android.provider.Settings

object TrackingRecovery {
    fun listenerGranted(context:Context):Boolean {
        val component=ComponentName(context,PlaybackListener::class.java)
        return if(android.os.Build.VERSION.SDK_INT>=27) context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
        else Settings.Secure.getString(context.contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { ComponentName.unflattenFromString(it)==component }
    }
    fun shouldStart(enabled:Boolean,unlocked:Boolean,foreground:Boolean,access:Boolean)=enabled && unlocked && !foreground && access
    private var lastRequest=-60000L
    @Synchronized fun request(context:Context,reason:String):Boolean {
        if(!listenerGranted(context)) return false
        val now=android.os.SystemClock.elapsedRealtime()
        if(now-lastRequest<15000) return false
        lastRequest=now
        return runCatching {
            android.service.notification.NotificationListenerService.requestRebind(ComponentName(context,PlaybackListener::class.java))
            DebugTrace.event("recovery","Rebind requested: "+reason)
            true
        }.getOrElse { DebugTrace.failure("listener-rebind",it); false }
    }
    fun status(context:Context):String {
        val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        if(!p.getBoolean("enabled",false)) return "Tracking paused"
        if(!listenerGranted(context)) return "Notification access required"
        if(!PlaybackListener.liveConnected()) return if(p.getInt("recovery_attempts",0)>=3) "Tracking disconnected — repair required" else "Reconnecting tracking"
        val age=System.currentTimeMillis()-p.getLong("last_sample",0)
        return if(age in 0..45000 && p.getString("sample_error",null)==null) "Tracking active" else "Waiting for fresh playback sample"
    }
    fun clearObservation(context:Context) {
        context.getSharedPreferences("tracker",Context.MODE_PRIVATE).edit().putString("sessions","[]").putInt("session_count",0).putInt("tracked_session_count",0).remove("last_sample").apply()
    }
}
