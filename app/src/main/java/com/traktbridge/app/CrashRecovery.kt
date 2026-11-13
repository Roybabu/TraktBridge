package com.traktbridge.app

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.util.AtomicFile
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/** Local crash reports: structural stack frames only, no exception messages or personal data. */
object CrashRecovery {
    private val installed=AtomicBoolean(false)
    private val saving=AtomicBoolean(false)
    @Volatile private var screen="Startup"
    private fun prefs(c:Context)=c.getSharedPreferences("crash_report_state",Context.MODE_PRIVATE)
    private fun file(c:Context)=AtomicFile(File(c.filesDir,"last-crash-report.txt"))
    fun safeClass(name:String)=name.takeIf { it.length<=160 && it.matches(Regex("[A-Za-z0-9_.$]+")) } ?: "Unknown"
    fun markScreen(name:String) { screen=name.takeIf { it in setOf("Startup","Home","Library","Review","Activity","Settings","Diagnostics") } ?: "Unknown" }
    fun formatStack(error:Throwable):String=buildString {
        val seen=java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable,Boolean>())
        var cause:Throwable?=error
        var depth=0
        while(cause!=null && depth<5 && seen.add(cause)) {
            val current=cause
            if(depth>0) append("Caused by: ")
            append(safeClass(current.javaClass.name)).append('\n')
            current.stackTrace.take(40).forEach { frame ->
                append("  at ").append(safeClass(frame.className)).append('.').append(safeClass(frame.methodName)).append(':').append(frame.lineNumber).append('\n')
            }
            cause=current.cause; depth++
        }
    }.take(24000)
    private fun header(c:Context,at:Long):String {
        val version=runCatching { c.packageManager.getPackageInfo(c.packageName,0).versionName }.getOrNull().orEmpty()
        return "TraktBridge crash report\nVersion: "+version+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nTime (UTC): "+Instant.ofEpochMilli(at)+"\n"
    }
    private fun save(c:Context,at:Long,report:String) {
        val target=file(c)
        val stream=target.startWrite()
        try { stream.write(report.take(32000).toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch(e:Throwable) { target.failWrite(stream); throw e }
        prefs(c).edit().putLong("recorded_at",at).putBoolean("pending",true).commit()
    }
    fun install(context:Context) {
        if(!installed.compareAndSet(false,true)) return
        val app=context.applicationContext
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error ->
            try {
                if(saving.compareAndSet(false,true)) {
                    val at=System.currentTimeMillis()
                    val tracker=app.getSharedPreferences("tracker",Context.MODE_PRIVATE)
                    val info=header(app,at)+"Screen: "+screen+"\nThread: "+if(thread===android.os.Looper.getMainLooper().thread) "main\n" else "background\n"
                    val state="Tracking enabled: "+tracker.getBoolean("enabled",false)+"\nListener connected: "+PlaybackListener.liveConnected()+"\nForeground service: "+TrackingService.running+"\n\nSTACK TRACE (messages omitted for privacy)\n"
                    save(app,at,info+state+formatStack(error))
                }
            } catch(_:Throwable) {} finally {
                if(previous!=null) previous.uncaughtException(thread,error) else android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
    /** OS exit metadata is a fallback when a Java handler could not run (native crash or ANR). */
    fun recoverSystemExit(c:Context) {
        if(Build.VERSION.SDK_INT<30) return
        runCatching {
            val p=prefs(c)
            val cutoff=maxOf(p.getLong("recorded_at",0),p.getLong("exit_checked_at",0))
            val exits=c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName,0,12)
            val exit=exits.filter { it.timestamp>cutoff && it.reason in setOf(android.app.ApplicationExitInfo.REASON_CRASH,android.app.ApplicationExitInfo.REASON_CRASH_NATIVE,android.app.ApplicationExitInfo.REASON_ANR) }.maxByOrNull { it.timestamp }
            if(exit!=null) {
                val reason=when(exit.reason) { android.app.ApplicationExitInfo.REASON_ANR->"Application not responding (ANR)"; android.app.ApplicationExitInfo.REASON_CRASH_NATIVE->"Native crash"; else->"Java crash" }
                save(c,exit.timestamp,header(c,exit.timestamp)+"Android exit reason: "+reason+"\nStatus: "+exit.status+"\nPSS (KiB): "+exit.pss+"\nRSS (KiB): "+exit.rss+"\nNo Java stack was captured. System trace contents are omitted for privacy.")
            }
            p.edit().putLong("exit_checked_at",System.currentTimeMillis()).apply()
        }
    }
    fun report(c:Context):String?=runCatching { file(c).openRead().bufferedReader().use { it.readText().take(32000) } }.getOrNull()?.takeIf { it.isNotBlank() }
    fun pending(c:Context)=prefs(c).getBoolean("pending",false)
    fun acknowledge(c:Context) { prefs(c).edit().putBoolean("pending",false).apply() }
    fun clear(c:Context) { file(c).delete(); prefs(c).edit().putBoolean("pending",false).putLong("exit_checked_at",System.currentTimeMillis()).apply() }
    // Retain compatibility with older diagnostics callers.
    fun consume(context:Context):String? {
        val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val at=p.getLong("last_crash_at",0)
        if(at<=0) return null
        val label=safeClass(p.getString("last_crash_class","Unknown").orEmpty())
        p.edit().remove("last_crash_at").remove("last_crash_class").apply()
        return if(p.getBoolean("debug_capture",false)) label else null
    }
}
