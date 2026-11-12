package com.traktbridge.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.*
import android.view.ViewGroup
import android.os.Build

class DebugActivity: Activity() {
    private lateinit var report: TextView
    private var prepared=""
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DebugTrace.init(this)
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24) }
        setContentView(ScrollView(this).apply { addView(root) })
        fun button(label: String,action: ()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
        root.addView(TextView(this).apply { text="Diagnostic recorder"; textSize=24f })
        root.addView(TextView(this).apply { text="Opt in to capture TraktBridge events. Logs stay on this device until you choose to export. No movie titles, account identifiers or credentials are recorded. Android restricts other apps’ logcat; use Developer options for a separate system bug report." })
        root.addView(Switch(this).apply {
            text="Record diagnostic events"; isChecked=DebugTrace.enabled(this@DebugActivity)
            setOnCheckedChangeListener { _,checked -> DebugTrace.setEnabled(this@DebugActivity,checked); refresh() }
        })
        root.addView(Switch(this).apply { text="Player Inspector (control IDs only)"; isChecked=prefs.getBoolean("player_inspector",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("player_inspector",checked).apply(); refresh() } })
        button("Refresh diagnostic report") { refresh() }
        button("Export report as text file") {
            if(prepared.isBlank()) { Toast.makeText(this,"Refresh report first",Toast.LENGTH_SHORT).show(); return@button }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"TraktBridge-diagnostics.txt"),42)
        }
        button("Share report") {
            if(prepared.isBlank()) { Toast.makeText(this,"Refresh report first",Toast.LENGTH_SHORT).show(); return@button }
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,prepared),"Share diagnostic report"))
        }
        button("Clear recorded diagnostics") { DebugTrace.clear(this); refresh() }
        button("Open Android developer options") {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }.onFailure { Toast.makeText(this,"Open Settings and enable Developer options first",Toast.LENGTH_LONG).show() }
        }
        report=TextView(this).apply { setTextIsSelectable(true); textSize=13f }
        root.addView(report,ViewGroup.LayoutParams(-1,-2))
        refresh()
    }
    private fun refresh() {
        if(!::report.isInitialized) return
        val last=prefs.getLong("last_sample",0)
        val pm=getSystemService(android.os.PowerManager::class.java)
        val manager=getSystemService(android.app.NotificationManager::class.java)
        val component=android.content.ComponentName(this,PlaybackListener::class.java)
        val access=if(Build.VERSION.SDK_INT>=27) manager.isNotificationListenerAccessGranted(component) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==component }
        val version=packageManager.getPackageInfo(packageName,0).versionName
        val summary=listOf("TraktBridge "+version,"Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")","Device "+Build.MANUFACTURER+" "+Build.MODEL,"Capture enabled: "+DebugTrace.enabled(this),"Sampling enabled: "+prefs.getBoolean("enabled",false),"Foreground tracking (last reported): "+prefs.getBoolean("foreground_running",false),"Title detection opt-in: "+prefs.getBoolean("title_optin",false),"Accessibility connected (last reported): "+prefs.getBoolean("title_connected",false),"Listener access: "+access,"Listener connected (last reported): "+prefs.getBoolean("listener_connected",false),"Last sample age seconds: "+if(last==0L) "never" else ((System.currentTimeMillis()-last)/1000).toString(),"Usage access: "+UsageFallback.allowed(this),"Usage fallback enabled: "+prefs.contains("usage_since"),"Notifications enabled: "+ViewingNotifications.enabled(this),"Battery optimization exempt: "+pm.isIgnoringBatteryOptimizations(packageName),"Selected apps: "+(prefs.getStringSet("allowed",emptySet())?.size ?: 0),"Active media session count: "+prefs.getInt("session_count",0),"Last sampling error: "+prefs.getString("sample_error","none")).joinToString("\n")
        report.text="Preparing report…"
        prepared=""
        Thread {
            val log=DebugTrace.snapshot(applicationContext)
            val cat=DebugTrace.ownLogcat()
            val result=summary+"\n\nLOCAL EVENT TRACE\n"+log+"\n\nAPP LOGCAT (current process, diagnostic tag only)\n"+cat+"\n\nSystem-wide logcat is not accessible to normal apps. Use Android Developer options > Take bug report if needed."
            runOnUiThread { if(!isDestroyed && !isFinishing) { prepared=result; report.text=result } }
        }.start()
    }
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=42 || resultCode!=RESULT_OK) return
        val uri=data?.data ?: return
        val snapshot=prepared
        runCatching { contentResolver.openOutputStream(uri)?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: error("No output stream") }.onSuccess { Toast.makeText(this,"Diagnostic report saved",Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(this,"Could not save report",Toast.LENGTH_LONG).show() }
    }
}
