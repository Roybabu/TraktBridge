package com.traktbridge.app

import android.content.Context
import android.os.Build

object PermissionDiagnostics {
    data class Check(val name:String,val ready:Boolean,val required:Boolean,val detail:String)
    fun checks(access:Boolean,selected:Int,enabled:Boolean,notifications:Boolean,restricted:Boolean,foreground:Boolean):List<Check> = listOf(
        Check("Media notification access",access,true,"Required for Android media-session observations."),
        Check("Selected playback apps",selected>0,true,"Choose installed apps explicitly."),
        Check("Tracking enabled",enabled,true,"Start tracking only when you want it."),
        Check("Foreground tracking service",foreground,false,"Keep the user-started tracking service active."),
        Check("Review notifications",notifications,false,"Optional; local history works without review alerts."),
        Check("Background activity",!restricted,false,"Review battery restrictions if tracking stops.")
    )
    fun read(context:Context):List<Check> {
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val battery=BatteryCompatibility.status(context)
        return checks(TrackingRecovery.listenerGranted(context),prefs.getStringSet("allowed",emptySet())?.size ?: 0,prefs.getBoolean("enabled",false),ViewingNotifications.enabled(context),battery.restricted,TrackingService.running)
    }
}
