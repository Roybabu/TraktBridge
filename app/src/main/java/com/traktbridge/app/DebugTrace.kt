package com.traktbridge.app

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant

// Explicitly enabled, bounded, local-only diagnostic events. Never log titles,
// tokens, URLs, account IDs, notification text or exception messages.
object DebugTrace {
    private const val TAG="TraktBridgeTrace"
    private const val MAX=192*1024
    private var app: Context?=null
    @Synchronized fun init(context: Context) { app=context.applicationContext }
    private fun file(c: Context)=File(c.filesDir,"diagnostic-trace.txt")
    fun enabled(c: Context)=c.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("debug_capture",false)
    @Synchronized fun setEnabled(c: Context,value: Boolean) {
        init(c)
        c.getSharedPreferences("tracker",Context.MODE_PRIVATE).edit().putBoolean("debug_capture",value).commit()
        if(value) event("debug","capture started")
    }
    @Synchronized fun event(component: String,message: String) {
        val c=app ?: return
        if(!enabled(c)) return
        val line=Instant.now().toString()+" ["+component+"] "+message.take(1000).replace('\n',' ')+"\n"
        Log.i(TAG,line.trimEnd())
        runCatching {
            val f=file(c)
            if(f.length()>MAX) {
                val old=f.readText().takeLast(MAX/2)
                f.writeText(old.substringAfter('\n',""))
            }
            f.appendText(line)
        }
    }
    fun failure(component: String,e: Throwable) {
        val frames=e.stackTrace.take(8).joinToString(" <- ") { it.className+"."+it.methodName+":"+it.lineNumber }
        event(component,e.javaClass.name+" at "+frames)
    }
    @Synchronized fun clear(c: Context) { file(c).delete() }
    @Synchronized fun snapshot(c: Context): String=runCatching { file(c).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    fun ownLogcat(): String {
        // Only our current process and our explicitly sanitized tag; never request READ_LOGS.
        return runCatching {
            val p=ProcessBuilder("logcat","-d","-t","300","--pid="+android.os.Process.myPid(),"-v","threadtime",TAG+":I","*:S").redirectErrorStream(true).start()
            try {
                if(!p.waitFor(3,java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); return "Logcat timed out" }
                if(p.exitValue()!=0) return "App logcat unavailable on this device"
                p.inputStream.bufferedReader().use { it.readText().takeLast(48000) }
            } finally { p.destroy() }
        }.getOrDefault("App logcat unavailable on this device")
    }
}
