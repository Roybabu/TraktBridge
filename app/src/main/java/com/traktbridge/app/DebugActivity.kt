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
        CrashRecovery.markScreen("Diagnostics")
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,32,24,24) }
        setContentView(ScrollView(this).apply { addView(root) })
        fun button(label: String,action: ()->Unit) { root.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
        root.addView(TextView(this).apply { text="Diagnostic recorder"; textSize=24f })
        root.addView(TextView(this).apply { text="Opt in to capture TraktBridge events. Logs stay on this device until you choose to export. No movie titles, account identifiers or credentials are recorded. Android restricts other apps’ logcat; use Developer options for a separate system bug report." })
        root.addView(Switch(this).apply {
            text="Record diagnostic events"; isChecked=DebugTrace.enabled(this@DebugActivity)
            setOnCheckedChangeListener { _,checked -> DebugTrace.setEnabled(this@DebugActivity,checked); refresh() }
        })
        root.addView(Switch(this).apply {
            text="JioHotstar title detection debugger"; isChecked=HotstarProbe.enabled(this@DebugActivity)
            setOnCheckedChangeListener { toggle,checked ->
                if(checked) android.app.AlertDialog.Builder(this@DebugActivity).setTitle("Capture JioHotstar titles?").setMessage("Records JioHotstar media and transport notification metadata, visible player control IDs and candidate player title labels, recent app usage events and global audio activity locally for 15 minutes. Title text may appear in the separate report. No screenshots or automatic uploads.").setNegativeButton("Cancel"){_,_->toggle.isChecked=false}.setOnCancelListener { toggle.isChecked=false }.setPositiveButton("Start"){_,_->HotstarProbe.start(this@DebugActivity);PlaybackListener.wake();refresh()}.show()
                else { HotstarProbe.stop(this@DebugActivity);refresh() }
            }
        })
        button("Copy JioHotstar title report") { val value=HotstarProbe.report(this); getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("JioHotstar diagnostic",value)); Toast.makeText(this,"JioHotstar report copied",Toast.LENGTH_SHORT).show() }
        button("Clear JioHotstar title report") { HotstarProbe.stop(this);HotstarProbe.clear(this);refresh() }
        button("Open JioHotstar") { packageManager.getLaunchIntentForPackage(HotstarPlayback.PACKAGE)?.let { startActivity(it) } ?: Toast.makeText(this,"JioHotstar not installed",Toast.LENGTH_SHORT).show() }
        button("Refresh diagnostic report") { refresh() }
        button("Recover playback sampling") {
            if(!prefs.getBoolean("enabled",false) || !TrackingRecovery.listenerGranted(this)) { Toast.makeText(this,"Enable tracking and grant notification access first",Toast.LENGTH_LONG).show(); return@button }
            if(prefs.getBoolean("privacy_lock",false)) { Toast.makeText(this,"Open and unlock TraktBridge to recover tracking",Toast.LENGTH_LONG).show(); return@button }
            runCatching { startForegroundService(Intent(this,TrackingService::class.java)); if(!PlaybackListener.wake()) android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this,PlaybackListener::class.java)) }.onFailure { DebugTrace.failure("manual-recovery",it) }
            report.postDelayed({ if(!isDestroyed) refresh() },2000)
        }
        button("Export report as text file") {
            if(prepared.isBlank()) { Toast.makeText(this,"Refresh report first",Toast.LENGTH_SHORT).show(); return@button }
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"TraktBridge-diagnostics.txt"),42)
        }
        button("Copy last crash report") {
            val crash=CrashRecovery.report(this)
            if(crash==null) Toast.makeText(this,"No saved crash report",Toast.LENGTH_SHORT).show()
            else { getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("TraktBridge crash report",crash)); Toast.makeText(this,"Crash report copied",Toast.LENGTH_SHORT).show() }
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
    override fun onResume() { super.onResume(); refresh() }
    private fun refresh() {
        if(!::report.isInitialized) return
        val last=prefs.getLong("last_sample",0)
        val pm=getSystemService(android.os.PowerManager::class.java)
        val manager=getSystemService(android.app.NotificationManager::class.java)
        val component=android.content.ComponentName(this,PlaybackListener::class.java)
        val access=if(Build.VERSION.SDK_INT>=27) manager.isNotificationListenerAccessGranted(component) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==component }
        val version=packageManager.getPackageInfo(packageName,0).versionName
        val summary=listOf("TraktBridge "+version,"Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")","Device "+Build.MANUFACTURER+" "+Build.MODEL,"Capture enabled: "+DebugTrace.enabled(this),"Sampling enabled: "+prefs.getBoolean("enabled",false),"Foreground tracking (live): "+TrackingService.running,"Foreground tracking (last reported): "+prefs.getBoolean("foreground_running",false),"YouTube player identification opt-in: "+prefs.getBoolean("title_optin",false),"Accessibility connected (last reported): "+prefs.getBoolean("title_connected",false),"Listener access: "+access,"Listener connected (live): "+PlaybackListener.liveConnected(),"Listener connected (last reported): "+prefs.getBoolean("listener_connected",false),"Last sample age seconds: "+if(last==0L) "never" else ((System.currentTimeMillis()-last)/1000).toString(),"Notifications enabled: "+ViewingNotifications.enabled(this),"Battery optimization exempt: "+pm.isIgnoringBatteryOptimizations(packageName),"Selected apps: "+(prefs.getStringSet("allowed",emptySet())?.size ?: 0),"YouTube selected: "+("com.google.android.youtube" in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())),"Active media session count: "+prefs.getInt("session_count",0),"Last sampling error: "+prefs.getString("sample_error","none")).joinToString("\n")
        report.text="Preparing report…"
        prepared=""
        Thread {
            val log=DebugTrace.snapshot(applicationContext)
            val cat=DebugTrace.ownLogcat()
            val result=summary+"\nJioHotstar probe active: "+HotstarProbe.enabled(applicationContext)+"\nJioHotstar title report is separate; use Copy JioHotstar title report.\n\nLOCAL EVENT TRACE\n"+log+"\n\nAPP LOGCAT (current process, diagnostic tag only)\n"+cat+"\n\nSystem-wide logcat is not accessible to normal apps. Use Android Developer options > Take bug report if needed."
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
