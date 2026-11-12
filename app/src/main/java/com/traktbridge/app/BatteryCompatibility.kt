package com.traktbridge.app

import android.content.Context
import android.app.ActivityManager
import android.os.PowerManager
import android.os.Build
import java.util.Locale

object BatteryCompatibility {
    data class Status(val manufacturer:String,val restricted:Boolean,val exempt:Boolean,val saver:Boolean)
    fun status(context:Context):Status {
        val power=context.getSystemService(PowerManager::class.java)
        val activity=context.getSystemService(ActivityManager::class.java)
        return Status(Build.MANUFACTURER.orEmpty(),Build.VERSION.SDK_INT>=28 && activity.isBackgroundRestricted,power.isIgnoringBatteryOptimizations(context.packageName),power.isPowerSaveMode)
    }
    fun guidance(manufacturer:String):String=when(manufacturer.lowercase(Locale.ROOT)) {
        "xiaomi","redmi","poco"->"If tracking stops, review the app battery mode and available autostart options in your device settings."
        "samsung"->"If tracking stops, check Sleeping apps and the app battery settings."
        "oppo","realme","oneplus","vivo","iqoo"->"If tracking stops, check background activity and app battery management in device settings."
        else->"If tracking stops, review the app battery settings and manufacturer background restrictions."
    }
    fun idleDelay(playing:Boolean,powerSave:Boolean,restricted:Boolean):Long=if(playing) 5000L else if(powerSave || restricted) 15000L else 10000L
}
