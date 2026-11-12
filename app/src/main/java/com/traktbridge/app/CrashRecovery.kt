package com.traktbridge.app

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in local crash marker; no exception text, credentials, titles or automatic reporting. */
object CrashRecovery {
    private val installed=AtomicBoolean(false)
    fun safeClass(name:String)=name.takeIf { it.length<=160 && it.matches(Regex("[A-Za-z0-9_.$]+")) } ?: "Unknown"
    fun install(context:Context) {
        if(!installed.compareAndSet(false,true)) return
        val app=context.applicationContext
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            try {
                val prefs=app.getSharedPreferences("tracker",Context.MODE_PRIVATE)
                if(prefs.getBoolean("debug_capture",false)) prefs.edit().putLong("last_crash_at",System.currentTimeMillis()).putString("last_crash_class",safeClass(error.javaClass.name)).commit()
            } catch(_:Throwable) {} finally {
                if(previous!=null) previous.uncaughtException(thread,error) else android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
    fun consume(context:Context):String? {
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val at=prefs.getLong("last_crash_at",0)
        if(at<=0) return null
        val label=safeClass(prefs.getString("last_crash_class","Unknown").orEmpty())
        prefs.edit().remove("last_crash_at").remove("last_crash_class").apply()
        return if(prefs.getBoolean("debug_capture",false)) label else null
    }
}
