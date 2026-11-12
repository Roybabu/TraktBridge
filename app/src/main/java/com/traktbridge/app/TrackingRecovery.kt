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
    fun clearObservation(context:Context) {
        context.getSharedPreferences("tracker",Context.MODE_PRIVATE).edit().putString("sessions","[]").putInt("session_count",0).putInt("tracked_session_count",0).remove("last_sample").apply()
    }
}
