package com.traktbridge.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.*
import android.view.ViewGroup
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("tracker", MODE_PRIVATE) }
    private lateinit var output: TextView
    private lateinit var history: WatchHistory
    private lateinit var historyList: LinearLayout
    private lateinit var account: TextView
    private lateinit var auth: TraktAuth
    private lateinit var shell: LinearLayout
    private lateinit var settingsPage: ScrollView
    private lateinit var contentPage: ScrollView
    private lateinit var nav: LinearLayout
    private lateinit var dashboard: LinearLayout
    private var tab=0
    private var privacyPrompt=false
    private var unlocked=false
    private var historyFilter=HistoryFilter()
    private var historyLimit=100
    private var analyticsPeriod=30
    private var analyticsSource=""
    private var historyRequest=0L
    private val historyExecutor=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var historyTask:java.util.concurrent.Future<*>?=null
    private var calendarMonth=java.time.YearMonth.now()
    private val tvMode:Boolean get()=(getSystemService(android.app.UiModeManager::class.java).currentModeType==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
    private val wide:Boolean get()=tvMode || resources.configuration.screenWidthDp>=720
    private val ink:Int get()=if(prefs.getBoolean("high_contrast",false)) 0xffffffff.toInt() else 0xfff1f0f6.toInt()
    private val muted:Int get()=if(prefs.getBoolean("high_contrast",false)) 0xffe3e3ef.toInt() else 0xffa9a8ba.toInt()
    private val violet:Int get()=when(prefs.getString("accent","violet")) { "blue"->0xff80bfff.toInt(); "green"->0xff6ee7b7.toInt(); else->0xffb6a0ff.toInt() }
    private val surface=0xff1b1e2a.toInt()
    private val bg=0xff0d0f16.toInt()
    private fun showAuth(message: String = auth.status()) { account.text = message }
    private fun handleCallback(i: Intent) {
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.encodedAuthority != "callback" || !uri.encodedPath.isNullOrEmpty() || uri.fragment!=null) return
        i.data = null
        showAuth("Connecting to Trakt…")
        auth.complete(uri) { showAuth(it) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleCallback(intent); handleReview(intent); handleShared(intent); handleTrackingShortcut(intent); handleAutomation(intent) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==96) { privacyPrompt=false; unlocked=resultCode==RESULT_OK; if(unlocked) shell.visibility=android.view.View.VISIBLE else finish(); return }
        if(requestCode==57 && resultCode==RESULT_OK) data?.data?.let { inspectVideo(it) }
        if(requestCode==58 && resultCode==RESULT_OK) data?.data?.let { inspectImport(it) }
        if(resultCode==RESULT_OK && requestCode in 65..66) data?.data?.let { settingsTransfer(requestCode,it) }
        if(resultCode==RESULT_OK && requestCode in 63..64) data?.data?.let { encryptedTransfer(requestCode,it) }
        if(resultCode==RESULT_OK && requestCode in 60..62) data?.data?.let { historyTransfer(requestCode,it) }
    }
    private fun deviceTransferGuide() {
        android.app.AlertDialog.Builder(this).setTitle("Move your library to another device").setMessage("1. Export a password-encrypted history backup.\n2. Export personal settings separately.\n3. Transfer both files using your preferred private file-sharing method.\n4. Restore them on the other device.\n\nThis is manual transfer, not live synchronization. Account credentials and upload approvals never transfer. Reconnect Trakt separately.").setNegativeButton("Close",null).setNeutralButton("Export settings") { _,_->startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-settings.tbenc"),65) }.setPositiveButton("Export history") { _,_->startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-history.tbenc"),63) }.show()
    }
    private fun settingsTransfer(code:Int,uri:android.net.Uri) {
        val field=EditText(this).apply { hint="Password (10+ characters)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        android.app.AlertDialog.Builder(this).setTitle("Personal settings backup").setMessage("Includes default-profile collections, notes, favorites and selected display settings. Excludes tokens, tracking permissions, other profiles and learned mappings. Existing settings are not overwritten on import.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
            val password=field.text.toString().toCharArray(); field.text.clear()
            Thread {
                try {
                    if(code==65) {
                        val data=EncryptedBackup.encrypt(PortableSettings.snapshot(applicationContext),password)
                        contentResolver.openOutputStream(uri,"wt")?.use { it.write(data) } ?: error("Cannot write")
                        runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"Encrypted settings exported",Toast.LENGTH_LONG).show() }
                    } else {
                        val data=contentResolver.openInputStream(uri)?.use { input -> val out=java.io.ByteArrayOutputStream(); val buf=ByteArray(8192); while(true) { val n=input.read(buf); if(n<0) break; require(out.size()+n<=8_000_000); out.write(buf,0,n) }; out.toByteArray() } ?: error("Cannot read")
                        val stores=PortableSettings.parse(EncryptedBackup.decrypt(data,password))
                        runOnUiThread { if(!isDestroyed && !isFinishing) android.app.AlertDialog.Builder(this).setTitle("Merge personal settings?").setMessage("Existing entries remain unchanged. No account credentials or permissions are imported.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
                            Thread { val result=runCatching { PortableSettings.merge(applicationContext,stores) }; runOnUiThread { if(!isDestroyed && !isFinishing) { Toast.makeText(this,result.fold({ it.toString()+" entries imported" },{ "Merge failed" }),Toast.LENGTH_LONG).show(); recreate() } } }.start()
                        }.show() }
                    }
                } catch(_:Exception) { runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"Settings transfer failed",Toast.LENGTH_LONG).show() } }
                finally { password.fill('\u0000') }
            }.start()
        }.show()
    }
    private fun restoreDailyBackup() {
        if(!LocalBackupVault.exists(this)) { Toast.makeText(this,"No daily backup yet",Toast.LENGTH_LONG).show(); return }
        android.app.AlertDialog.Builder(this).setTitle("Restore daily device backup?").setMessage("Existing IDs are skipped. No sync approval is restored.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
            Thread {
                val result=runCatching { val rows=HistoryBackup.parse(LocalBackupVault.read(applicationContext)); WatchHistory(applicationContext).use { HistoryBackup.merge(it,rows) } }
                runOnUiThread { if(!isDestroyed && !isFinishing) { Toast.makeText(this,result.fold({ it.first.toString()+" restored • "+it.second+" skipped" },{ "Restore failed" }),Toast.LENGTH_LONG).show(); refreshHistory() } }
            }.start()
        }.show()
    }
    private fun encryptedTransfer(code:Int,uri:android.net.Uri) {
        val field=EditText(this).apply { hint="Backup password (10+ characters)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        android.app.AlertDialog.Builder(this).setTitle(if(code==63) "Encrypt history backup" else "Unlock history backup").setMessage("Keep the password safely. Lost passwords cannot be recovered. This backup contains viewing history only.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
            val password=field.text.toString().toCharArray(); field.text.clear()
            Thread {
                try {
                    if(code==63) {
                        val data=WatchHistory(applicationContext).use { EncryptedBackup.encrypt(HistoryBackup.snapshot(it).toString(),password) }
                        contentResolver.openOutputStream(uri,"wt")?.use { it.write(data) } ?: error("Cannot write backup")
                        runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"Encrypted backup saved",Toast.LENGTH_LONG).show() }
                    } else {
                        val data=contentResolver.openInputStream(uri)?.use { input ->
                            val out=java.io.ByteArrayOutputStream(); val buf=ByteArray(8192)
                            while(true) { val n=input.read(buf); if(n<0) break; require(out.size()+n<=EncryptedBackup.MAX_BYTES); out.write(buf,0,n) }; out.toByteArray()
                        } ?: error("Cannot open backup")
                        val rows=HistoryBackup.parse(EncryptedBackup.decrypt(data,password))
                        runOnUiThread {
                            if(isDestroyed || isFinishing) return@runOnUiThread
                            android.app.AlertDialog.Builder(this).setTitle("Merge decrypted history?").setMessage(rows.size.toString()+" records; existing IDs skipped. No upload approval is restored.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
                                Thread {
                                    val result=runCatching { WatchHistory(applicationContext).use { HistoryBackup.merge(it,rows) } }
                                    runOnUiThread { if(!isDestroyed && !isFinishing) { Toast.makeText(this,result.fold({ it.first.toString()+" restored • "+it.second+" skipped" },{ "Restore failed" }),Toast.LENGTH_LONG).show(); refreshHistory() } }
                                }.start()
                            }.show()
                        }
                    }
                } catch(_:Exception) { runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"Backup failed. Check password and file.",Toast.LENGTH_LONG).show() } }
                finally { password.fill('\u0000') }
            }.start()
        }.show()
    }
    private fun pickBackup(code:Int) {
        val intent=if(code==62) Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*") else Intent(Intent.ACTION_CREATE_DOCUMENT).setType(if(code==60) "application/json" else "text/csv").putExtra(Intent.EXTRA_TITLE,if(code==60) "traktbridge-history.json" else "traktbridge-history.csv")
        startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE),code)
    }
    private fun historyTransfer(code:Int,uri:android.net.Uri) {
        Thread {
            val result=runCatching {
                if(code==62) {
                    val text=contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val out=StringBuilder(); val buf=CharArray(8192)
                        while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=HistoryBackup.MAX_CHARS) { "Backup too large" }; out.append(buf,0,n) }
                        out.toString()
                    } ?: error("Cannot open backup")
                    val rows=HistoryBackup.parse(text)
                    runOnUiThread {
                        if(isDestroyed || isFinishing) return@runOnUiThread
                        android.app.AlertDialog.Builder(this).setTitle("Restore viewing history").setMessage(rows.size.toString()+" records. Existing IDs will be skipped. Restored entries will not be queued for Trakt upload.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
                            Thread {
                                val merged=runCatching { WatchHistory(applicationContext).use { HistoryBackup.merge(it,rows) } }
                                runOnUiThread { if(!isDestroyed && !isFinishing) { merged.fold({ counts -> Toast.makeText(this,counts.first.toString()+" restored; "+counts.second+" skipped",Toast.LENGTH_LONG).show(); showTab(1) },{ Toast.makeText(this,"Restore failed",Toast.LENGTH_LONG).show() }) } }
                            }.start()
                        }.show()
                    }
                } else {
                    val payload=WatchHistory(applicationContext).use { if(code==60) HistoryBackup.snapshot(it).toString() else HistoryBackup.csv(it) }
                    require(payload.length<=HistoryBackup.MAX_CHARS) { "Export too large" }
                    contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) } ?: error("Cannot write export")
                    runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"History exported",Toast.LENGTH_LONG).show() }
                }
            }
            result.onFailure { runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,it.message ?: "History transfer failed",Toast.LENGTH_LONG).show() } }
        }.start()
    }
    private fun checkHistoryIntegrity() {
        Thread {
            val ok=runCatching { WatchHistory(applicationContext).use { HistoryBackup.integrity(it) } }.getOrDefault(false)
            runOnUiThread { if(!isDestroyed && !isFinishing) android.app.AlertDialog.Builder(this).setTitle("Database integrity").setMessage(if(ok) "SQLite quick check passed" else "Integrity check failed or database unavailable. Existing files are preserved. Do not clear app data. Export or retain your encrypted backup before attempting repair.").setPositiveButton("OK",null).show() }
        }.start()
    }
    private var importSource="com.netflix.mediaclient"
    private fun externalHistoryImport() {
        val sources=ExternalHistoryImport.sources
        choose("Import external viewing history",listOf("Downloaded Trakt history")+sources.map { it+" • CSV interchange" }) { i ->
            if(i==0) {
                lookup("Checking Trakt account",{ done -> TraktLibrary.accountId(this,done) }) { account:String ->
                    val rows=ExternalHistoryImport.fromTrakt(TraktLibrary.cachedHistory(this,account),account)
                    android.app.AlertDialog.Builder(this).setTitle("Import cached Trakt events?").setMessage(rows.size.toString()+" local review records. Existing remote completions are not uploaded again automatically. No sampled viewing time is added.").setNegativeButton("Cancel",null).setPositiveButton("Import for review") { _,_->
                        Thread {
                            val result=runCatching { WatchHistory(applicationContext).use { it.importViewing(rows) } }
                            runOnUiThread { if(!isDestroyed && !isFinishing) result.fold({ counts -> showTab(2); Toast.makeText(this,counts.first.toString()+" added • "+counts.second+" duplicates",Toast.LENGTH_LONG).show() },{ Toast.makeText(this,"Import failed",Toast.LENGTH_LONG).show() }) }
                        }.start()
                    }.show()
                }
            } else {
                importSource=sources[i-1]
                android.app.AlertDialog.Builder(this).setTitle("CSV interchange format").setMessage("Use a CSV with Title (or Name) and a genuine viewing date (Date or Watched At). Optional Season and Episode columns are supported. Watchlists, ratings and anime progress counts are not viewing events. Convert unsupported export formats first.").setNegativeButton("Cancel",null).setPositiveButton("Choose file") { _,_->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),58) }.show()
            }
        }
    }
    private fun chooseImportProvider() {
        val providers=PlayerRules.apps.entries.toList()
        choose("Choose history source",providers.map { it.key }) { i ->
            importSource=providers[i].value
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("text/csv","text/plain","application/vnd.ms-excel","application/octet-stream")),58)
        }
    }
    private fun inspectImport(uri:android.net.Uri) {
        val source=importSource
        Thread {
            val result=runCatching {
                val input=contentResolver.openInputStream(uri) ?: error("Unable to open CSV")
                val text=input.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out=StringBuilder(); val buffer=CharArray(8192)
                    while(true) { val n=reader.read(buffer); if(n<0) break; require(out.length+n<=HistoryImport.MAX_CHARS) { "CSV exceeds size limit" }; out.append(buffer,0,n) }
                    out.toString()
                }
                if(source in ExternalHistoryImport.sources) ExternalHistoryImport.parseCsv(text,source) else HistoryImport.parse(text,source)
            }
            runOnUiThread {
                if(isFinishing || isDestroyed) return@runOnUiThread
                result.fold({ preview ->
                    val sample=preview.rows.take(5).joinToString("\n") { it.day.toString()+" • "+it.title }
                    android.app.AlertDialog.Builder(this).setTitle("Review history import").setMessage(appLabel(source)+"\n"+preview.rows.size+" valid rows • "+preview.skipped+" skipped\n\n"+sample+"\n\nImported activity does not prove playback or completion. Dates without times remain date-only. Nothing is uploaded to Trakt.").setNegativeButton("Cancel",null).setPositiveButton("Import") { _,_->
                        Thread {
                            val saved=runCatching { WatchHistory(applicationContext).use { it.importViewing(preview.rows) } }
                            runOnUiThread {
                                if(!isDestroyed && !isFinishing) saved.fold({ counts -> showTab(2); android.app.AlertDialog.Builder(this).setTitle("Import complete").setMessage(counts.first.toString()+" added • "+counts.second+" already imported").setPositiveButton("OK",null).show() },{ Toast.makeText(this,"Import failed",Toast.LENGTH_LONG).show() })
                            }
                        }.start()
                    }.show()
                },{ android.app.AlertDialog.Builder(this).setTitle("Cannot import CSV").setMessage(it.message ?: "Invalid history file").setPositiveButton("OK",null).show() })
            }
        }.start()
    }
    private fun reconcileImported(r:WatchRecord) {
        val zone=java.time.ZoneId.systemDefault()
        val day=java.time.Instant.ofEpochMilli(r.started).atZone(zone).toLocalDate()
        val row=ImportedViewing(r.source,r.title,r.subtitle,day,null,r.id)
        val matches=HistoryImport.candidates(row,history.records(0),zone)
        if(matches.isEmpty()) { android.app.AlertDialog.Builder(this).setTitle("No exact local match").setMessage("No local viewing with the same provider, date and title. Keep this record or identify it on Trakt.").setPositiveButton("OK",null).show(); return }
        choose("Reconcile with local viewing",matches.map { it.title+" • "+durationText(it.watched)+" sampled • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)) }) { index ->
            android.app.AlertDialog.Builder(this).setTitle("Link imported activity?").setMessage("Keep the existing local viewing record and hide this duplicate import. No watch time or completion will be added.").setNegativeButton("Cancel",null).setPositiveButton("Reconcile") { _,_->
                if(history.reconcileImport(r.id,matches[index].id)) refreshHistory()
            }.show()
        }
    }
    private fun armOcr() {
        if(android.os.Build.VERSION.SDK_INT<34) { Toast.makeText(this,"Window OCR requires Android 14 or later",Toast.LENGTH_LONG).show(); return }
        if(!prefs.getBoolean("enabled",false) || !prefs.getBoolean("title_optin",false) || !prefs.getBoolean("title_connected",false)) { Toast.makeText(this,"Enable tracking and Accessibility title detection first",Toast.LENGTH_LONG).show(); return }
        val packages=(prefs.getStringSet("allowed",emptySet()) ?: emptySet()).sortedBy { appLabel(it) }
        choose("Choose one player to scan",packages.map { appLabel(it) }) { index ->
            val pkg=packages[index]
            android.app.AlertDialog.Builder(this).setTitle("One-time on-device OCR").setMessage("TraktBridge will attempt one screenshot of the selected app's window, beginning in 8 seconds and expiring after 45 seconds. Open the video and reveal its controls. Only edge regions are read locally; the image is not saved or uploaded. Protected windows cannot be captured. Review any detected text before using it.").setNegativeButton("Cancel",null).setPositiveButton("Arm scan") { _,_->
                val now=System.currentTimeMillis()
                prefs.edit().putLong("ocr_generation",prefs.getLong("ocr_generation",0)+1).remove("ocr_choices").remove("ocr_source").remove("ocr_captured").putString("ocr_target",pkg).putLong("ocr_after",now+8000).putLong("ocr_until",now+45000).putString("ocr_status","Armed").apply()
                Toast.makeText(this,"Open the selected player and reveal controls after 8 seconds",Toast.LENGTH_LONG).show()
            }.show()
        }
    }
    private fun reviewOcr() {
        val status=prefs.getString("ocr_status","No OCR scan yet").orEmpty()
        val source=prefs.getString("ocr_source","").orEmpty()
        val choices=runCatching { org.json.JSONArray(prefs.getString("ocr_choices","[]")) }.getOrDefault(org.json.JSONArray())
        if(choices.length()==0) { android.app.AlertDialog.Builder(this).setTitle("OCR result").setMessage(status).setPositiveButton("OK",null).show(); return }
        val entries=(0 until choices.length()).map { choices.getJSONObject(it) }
        choose("Review OCR title candidates",entries.map { it.optString("title") }) { index ->
            val c=entries[index]
            val detail=c.optString("detail")
            val pos=c.optLong("position",-1); val dur=c.optLong("duration",0)
            val timer=if(PlaybackPosition.valid(pos,dur)) "\nVisible timer estimate: "+durationText(pos)+" / "+durationText(dur) else ""
            android.app.AlertDialog.Builder(this).setTitle("Use this title?").setMessage(c.optString("title")+"\n"+detail+timer+"\nOCR may misread text. Timers are not automatically used as verified progress.").setNegativeButton("Cancel",null).setPositiveButton("Identify") { _,_->
                presentShared(SharedCandidate(c.optString("title"),detail,source))
            }.show()
        }
    }
    private fun serverSettings() {
        val kinds=listOf("plex","jellyfin","emby","kodi")
        val names=listOf("Plex","Jellyfin","Emby","Kodi")
        choose("Media server integrations",kinds.mapIndexed { i,k -> names[i]+if(ServerStore.load(this,k)!=null) " • connected" else " • not configured" }) { index ->
            val kind=kinds[index]
            val old=ServerStore.load(this,kind)
            val url=EditText(this).apply { hint="HTTPS server base URL"; setSingleLine(true); setText(old?.url.orEmpty()) }
            val token=EditText(this).apply { hint=if(kind=="kodi") "Username:password (if required)" else "Server API token"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setText(old?.token.orEmpty()) }
            val client=EditText(this).apply { hint="Playback client / device ID"; setSingleLine(true); setText(old?.client.orEmpty()) }
            val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12); addView(url); addView(token); addView(client); addView(txt("Use a trusted HTTPS server. Discover clients while the intended device is connected. Only the selected client is tracked. Credentials stay encrypted on this device. Server playback may occur on another device.",14,muted)) }
            val d=android.app.AlertDialog.Builder(this).setTitle(names[index]+" connection").setView(box).setNegativeButton("Cancel",null).setNeutralButton(if(old!=null) "Disconnect" else "Discover clients",null).setPositiveButton("Discover and save",null).create()
            d.setOnShowListener {
                fun discover() {
                    val c=ServerConfig(kind,url.text.toString().trim(),token.text.toString(),client.text.toString().trim().ifBlank { "discover" })
                    val valid=runCatching { ServerStore.validate(c) }
                    if(valid.isFailure) { url.error=valid.exceptionOrNull()?.message ?: "Invalid configuration"; return }
                    d.dismiss()
                    val progress=android.app.ProgressDialog.show(this,"Connecting", "Discovering playback clients…",true,false)
                    Thread {
                        val result=runCatching { MediaServer.discover(c) }
                        runOnUiThread {
                            if(isFinishing || isDestroyed) return@runOnUiThread
                            progress.dismiss()
                            result.fold({ clients ->
                                if(clients.isEmpty()) { Toast.makeText(this,"No clients found. Start playback on the intended device and retry.",Toast.LENGTH_LONG).show(); return@fold }
                                choose("Select playback client",clients.map { it.second+" • "+it.first }) { j ->
                                    val saved=runCatching { ServerStore.save(this,c.copy(client=clients[j].first)) }
                                    Toast.makeText(this,if(saved.isSuccess) "Media server saved" else "Unable to save connection",Toast.LENGTH_LONG).show()
                                }
                            },{ Toast.makeText(this,"Connection failed. Check HTTPS URL, certificate and credentials.",Toast.LENGTH_LONG).show() })
                        }
                    }.start()
                }
                d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { discover() }
                d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { if(old!=null) { ServerStore.remove(this,kind); d.dismiss() } else discover() }
            }
            d.show()
        }
    }
    private fun handleShared(i:Intent) {
        if(i.action!=Intent.ACTION_SEND || prefs.getBoolean("privacy_lock",false) && !unlocked) return
        val type=i.type.orEmpty()
        i.action=null
        if(type.startsWith("video/")) {
            val uri=if(android.os.Build.VERSION.SDK_INT>=33) i.getParcelableExtra(Intent.EXTRA_STREAM,android.net.Uri::class.java) else {
                @Suppress("DEPRECATION")
                i.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            }
            if(uri!=null) inspectVideo(uri) else presentShared(null)
        } else if(type=="text/plain") {
            presentShared(SharedContent.fromText(i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()))
        }
    }
    private fun inspectVideo(uri:android.net.Uri) {
        if(uri.scheme!="content") { presentShared(null); return }
        Thread {
            val result=runCatching { SharedVideo.read(applicationContext,uri) }.getOrNull()
            runOnUiThread { if(!isDestroyed && !isFinishing) presentShared(result) }
        }.start()
    }
    private fun presentShared(candidate:SharedCandidate?) {
        if(candidate==null) { android.app.AlertDialog.Builder(this).setTitle("Content not identified").setMessage("Share a video file or a movie title with its link. Private downloads cannot be opened by TraktBridge.").setPositiveButton("OK",null).show(); return }
        val field=EditText(this).apply { setSingleLine(true); hint="Movie, show or anime title"; setText(candidate.title) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12); addView(field)
            addView(txt(listOfNotNull(candidate.detail.takeIf { it.isNotBlank() },candidate.providerId.takeIf { it.isNotBlank() }?.let { "Provider content ID: "+it },candidate.runtime.takeIf { it>0 }?.let { "File runtime: "+durationText(it) },"Confirm the title. Sharing content is not evidence of playback.").joinToString("\n"),14,muted)) }
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Identify shared content").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Continue",null).create()
        dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title=field.text.toString().trim()
            if(!ContentRecognition.usable(title)) { field.error="Enter the movie or show title"; return@setOnClickListener }
            dialog.dismiss()
            val now=System.currentTimeMillis()
            val recent=history.records(0).filter { it.status=="pending" && it.traktId==0L && now-it.ended in 0..1800000L && !it.id.startsWith("shared:") && !it.id.startsWith("import:") && (candidate.source.isBlank() || candidate.source=="local.file" && it.source in setOf("org.videolan.vlc","com.mxtech.videoplayer.ad","com.mxtech.videoplayer.pro") || it.source==candidate.source) }.take(10)
            fun create() { val id=history.addSharedCandidate(candidate.source.ifBlank { "shared.content" },title,candidate.detail,candidate.runtime); history.providerRef(id,RecognitionEngine.providerKey(candidate.source,candidate.providerId)); showTab(2); history.record(id)?.let { identify(it) } }
            if(recent.isEmpty()) create() else {
                val labels=recent.map { appLabel(it.source)+" • "+it.title.ifBlank { "Unidentified video" }+" • "+java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.ended)) }+"Create separate identification"
                choose("Attach to a recent viewing session?",labels) { index ->
                    if(index==recent.size) create() else {
                        val id=recent[index].id
                        if(history.attachSharedCandidate(id,title,candidate.detail,candidate.runtime)) { history.providerRef(id,RecognitionEngine.providerKey(candidate.source,candidate.providerId)); showTab(2); history.record(id)?.let { identify(it) } } else Toast.makeText(this,"Viewing record changed. Try again.",Toast.LENGTH_LONG).show()
                    }
                }
            }
        } }
        dialog.show()
    }
    private fun handleReview(i: Intent) {
        if(i.action==ViewingNotifications.USAGE_REVIEW) { i.action=null; if(!prefs.getBoolean("privacy_lock",false) || unlocked) reviewUsage(); return }
        if (i.action != ViewingNotifications.REVIEW) return
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.host != "review") return
        if(prefs.getBoolean("privacy_lock",false) && !unlocked) return
        val id = ComponentPolicy.reviewId(uri.toString()) ?: return
        i.action = null; i.data = null
        ViewingNotifications.cancel(this,id)
        val r = history.record(id) ?: return
        if (r.status == "pending" || r.status == "reviewed") {
            if(r.traktId>0) android.app.AlertDialog.Builder(this).setTitle("Review viewing").setMessage(r.matchLabel+"\nDid you finish this title?").setNegativeButton("Not finished",null).setNeutralButton("Wrong title") { _,_-> identify(r) }.setPositiveButton("Finished") { _,_->approveUpload(r) }.show() else identify(r)
        }
    }
    private val apps = PlayerRules.apps
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenProtection()
        DebugTrace.init(this)
        DebugTrace.event("activity","main opened")
        tab=(savedInstanceState?.getInt("tab") ?: 0).coerceIn(0,4)
        importSource=savedInstanceState?.getString("import_source") ?: "com.netflix.mediaclient"
        historyFilter=HistoryFilter(savedInstanceState?.getString("filter_q").orEmpty(),savedInstanceState?.getString("filter_source").orEmpty(),savedInstanceState?.getInt("filter_period") ?: 0,savedInstanceState?.getString("filter_type").orEmpty(),savedInstanceState?.getString("filter_status").orEmpty(),savedInstanceState?.getString("filter_sort") ?: "recent")
        analyticsPeriod=savedInstanceState?.getInt("analytics_period") ?: 30
        analyticsSource=savedInstanceState?.getString("analytics_source").orEmpty()
        calendarMonth=runCatching { java.time.YearMonth.parse(savedInstanceState?.getString("calendar_month").orEmpty()) }.getOrDefault(java.time.YearMonth.now())
        shell=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }
        shell.setOnApplyWindowInsetsListener { v,insets -> v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets }
        setContentView(shell)
        val header=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed); setContent { BridgeHeader(violet) { universalSearch() } } }
        shell.addView(header)
        val frame=android.widget.FrameLayout(this)
        val workspace=LinearLayout(this).apply { orientation=if(wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        shell.addView(workspace,LinearLayout.LayoutParams(-1,0,1f))
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(24)) }
        settingsPage=ScrollView(this).apply { addView(root) }; frame.addView(settingsPage)
        dashboard=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(24)) }
        contentPage=ScrollView(this).apply { addView(dashboard) }; frame.addView(contentPage)
        nav=LinearLayout(this).apply { orientation=if(wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; setPadding(dp(8),dp(8),dp(8),dp(8)); setBackgroundColor(surface) }
        if(wide) { workspace.addView(nav,LinearLayout.LayoutParams(dp(156),-1)); workspace.addView(frame,LinearLayout.LayoutParams(0,-1,1f)) }
        else { workspace.addView(frame,LinearLayout.LayoutParams(-1,0,1f)); workspace.addView(nav,LinearLayout.LayoutParams(-1,-2)) }
        listOf(getString(R.string.nav_home),getString(R.string.nav_library),getString(R.string.nav_review),getString(R.string.nav_activity),getString(R.string.nav_settings)).forEachIndexed { i,name -> nav.addView(TextView(this).apply { text=name; textSize=if(wide) 15f else 12f; gravity=android.view.Gravity.CENTER; minHeight=dp(52); isFocusable=true; setOnFocusChangeListener { view,focused -> view.alpha=if(focused) 1f else 0.82f; if(tvMode) view.scaleX=if(focused) 1.05f else 1f; if(tvMode) view.scaleY=if(focused) 1.05f else 1f }; setOnClickListener { showTab(i) } },if(wide) LinearLayout.LayoutParams(-1,dp(58)).apply { bottomMargin=dp(6) } else LinearLayout.LayoutParams(0,-2,1f)) }
        fun label(s: String) { root.addView(txt(s,15,muted)) }
        fun button(s: String, action: () -> Unit) { root.addView(actionButton(s,action)) }
        label("SETTINGS • Account and tracking")
        root.addView(Switch(this).apply { text="Download artwork only on unmetered networks"; isChecked=prefs.getBoolean("artwork_unmetered",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("artwork_unmetered",checked).apply() } })
        button("Clear offline catalogue") { getSharedPreferences("offline_catalogue",MODE_PRIVATE).edit().clear().apply() }
        button("Check for app updates") { checkUpdates() }
        button("Beta feedback and diagnostics") { betaFeedback() }
        root.addView(Switch(this).apply { text="Hide catalogue spoilers"; isChecked=prefs.getBoolean("hide_spoilers",true); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("hide_spoilers",checked).apply() } })
        root.addView(Switch(this).apply { text="High contrast text"; isChecked=prefs.getBoolean("high_contrast",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("high_contrast",checked).apply(); recreate() } })
        button("Text size") { choose("Reading size",listOf("System default","Large","Extra large")) { prefs.edit().putInt("reading_size",it).apply(); recreate() } }
        button("Monthly viewing goal and dashboard") { configureGoal() }
        button("Appearance accent") { choose("Accent color",listOf("Violet","Blue","Green")) { prefs.edit().putString("accent",listOf("violet","blue","green")[it]).apply(); recreate() } }
        button("Franchise viewing guides") { franchises() }
        button("Recently deleted viewing") { recentlyDeleted() }
        button("Transfer data to another device") { deviceTransferGuide() }
        button("Share or import collections") { shareCollections() }
        button("Smart collections") { smartCollections() }
        button("Custom smart watchlists") { smartWatchlists() }
        button("Personal collection profiles") { collectionProfiles() }
        button("Personal collections") { collections() }
        button("Organize library • tags and folders") { organizedLibrary() }
        button("Favorite titles") { favorites() }
        button("Automation shortcuts") { automationShortcuts() }
        button("Universal playback shortcuts") { playbackShortcut() }
        button("Companion status") { val summary=CompanionStatus.summarize(history.records(0)); android.app.AlertDialog.Builder(this).setTitle("Companion status").setMessage("Active sessions: "+summary.active+"\nAwaiting review: "+summary.pending+"\nSampled time: "+durationText(summary.watchedMs)).setPositiveButton("Close",null).show() }
        button("Cancel all episode reminders") { prefs.edit().putBoolean("episode_reminders",false).apply(); EpisodeReminders.cancelAll(this); Toast.makeText(this,"Reminders cancelled",Toast.LENGTH_SHORT).show() }
        button("Completion review threshold") { configureCompletionThreshold() }
        button("Playback compatibility profiles") { android.app.AlertDialog.Builder(this).setTitle("Playback profiles").setMessage(PlayerRules.apps.entries.joinToString("\n") { (name,pkg) -> name+": "+DetectionProfiles.forPackage(pkg).label }).setPositiveButton("Close",null).show() }
        button("Privacy dashboard and data controls") { privacyDashboard() }
        button("Privacy mode • stop tracking now") { privacyMode() }
        root.addView(Switch(this).apply { text="Require biometric or device credential on app resume"; isChecked=prefs.getBoolean("privacy_lock",false); setOnCheckedChangeListener { _,checked -> if(checked && !getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) { isChecked=false; Toast.makeText(this@MainActivity,"Set a device screen lock first",Toast.LENGTH_LONG).show() } else { prefs.edit().putBoolean("privacy_lock",checked).apply(); if(checked) { unlocked=false; requirePrivacyUnlock() } } } })
        root.addView(Switch(this).apply { text="Protect app screen from screenshots and recent-app previews"; isChecked=prefs.getBoolean("secure_screen",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("secure_screen",checked).apply(); applyScreenProtection() } })
        button("Guided tracking setup") { setupGuide() }
        root.addView(Switch(this).apply { text="Daily encrypted on-device history backup"; isChecked=prefs.getBoolean("daily_backup",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("daily_backup",checked).apply(); LocalBackupVault.schedule(this@MainActivity,checked) } })
        button("Restore daily device backup") { restoreDailyBackup() }
        button("Delete daily device backup") { LocalBackupVault.delete(this); Toast.makeText(this,"Device backup deleted",Toast.LENGTH_SHORT).show() }
        button("Export personal settings (encrypted)") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-settings.tbenc"),65) }
        button("Import personal settings (encrypted)") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),66) }
        button("Encrypted history backup") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-history.tbenc"),63) }
        button("Restore encrypted backup") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),64) }
        button("Back up viewing history (JSON)") { pickBackup(60) }
        button("Export viewing history (CSV)") { pickBackup(61) }
        button("Restore viewing history (JSON)") { pickBackup(62) }
        button("Check history database integrity") { checkHistoryIntegrity() }
        button("Import Letterboxd diary (CSV)") { importSource="letterboxd"; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),58) }
        button("Import streaming viewing history (CSV)") { chooseImportProvider() }
        button("Import external history (Trakt, IMDb, Simkl, anime)") { externalHistoryImport() }
        button("Identify local video file") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*"),57) }
        button("Identify shared movie link") { val field=EditText(this).apply { hint="Paste shared title or link" }; android.app.AlertDialog.Builder(this).setTitle("Identify shared content").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_-> presentShared(SharedContent.fromText(field.text.toString())) }.show() }
        button("Scan player title (one-shot OCR)") { armOcr() }
        button("Review OCR result") { reviewOcr() }
        button("Clear OCR result") { prefs.edit().putLong("ocr_generation",prefs.getLong("ocr_generation",0)+1).remove("ocr_target").remove("ocr_after").remove("ocr_until").remove("ocr_choices").remove("ocr_source").remove("ocr_captured").remove("ocr_status").apply() }
        button("Connect media servers") { serverSettings() }
        root.addView(Switch(this).apply { text="Automatically reuse confirmed local title mappings"; isChecked=prefs.getBoolean("learned_auto",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("learned_auto",checked).apply() } })
        button("Clear learned title mappings") { android.app.AlertDialog.Builder(this).setTitle("Clear learned mappings?").setMessage("Existing viewing history and Trakt matches stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_->history.clearLearning() }.show() }
        label("Remote playback is recognized when Android exposes a media session. Picture-in-picture can continue tracking through the same session; unsupported casts cannot be observed directly.")
        button("Tracking health and review center") { trackingHealth() }
        button("Live recognition inspector") { liveInspector() }
        button("Open diagnostic recorder") { startActivity(Intent(this,DebugActivity::class.java)) }
        auth = TraktAuth(applicationContext)
        account = TextView(this).apply { textSize = 16f }
        root.addView(account)
        showAuth()
        button("Connect Trakt") { try { startActivity(Intent(Intent.ACTION_VIEW, auth.begin())); showAuth("Complete sign-in in your browser") } catch (_: Exception) { showAuth("Unable to open sign-in") } }
        button("Check Trakt connection") { showAuth("Checking…"); auth.check { showAuth(it) } }
        button("Daily account history refresh") { configureAccountRefresh() }
        button("Refresh downloaded Trakt history") { lookup("Refreshing up to 2,000 remote watches",{ done -> TraktLibrary.refreshHistory(this,20,done) }) { n:Int -> Toast.makeText(this,n.toString()+" entries downloaded or updated",Toast.LENGTH_LONG).show() } }
        button("My anime progress (local)") { animeLocalProgress() }
        button("Browse public AniList account") { publicAnimeList() }
        button("Trakt account library") { openTraktLibrary() }
        button("Clear downloaded Trakt history") { android.app.AlertDialog.Builder(this).setTitle("Clear remote history cache?").setMessage("Local viewing records and Trakt account history are unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_->TraktLibrary.clearCache(this) }.show() }
        button("Disconnect Trakt locally") { RemoteRefreshSchedule.disable(this); auth.disconnect { TraktLibrary.clearCache(this); showAuth(it) } }
        label("Uses Trakt but is not endorsed or certified by Trakt.")
        label("Only selected apps are tracked locally. Catalogue search sends only the search text you choose to Trakt. Only explicitly approved completed watches are uploaded.")
        history = WatchHistory(this)
        button("Grant notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        button("Reconnect playback listener") { runCatching { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this,PlaybackListener::class.java)) }; refresh() }
        root.addView(Switch(this).apply {
            text="Viewing review notifications"; isChecked=prefs.getBoolean("review_notifications",false)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("review_notifications",checked).apply()
                if (checked && android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),41)
                if (!checked) history.records().forEach { ViewingNotifications.cancel(this@MainActivity,it.id) }
            }
        })
        button("Notification settings") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) }
        root.addView(Switch(this).apply {
            text="Enable playback sampling"; isChecked=prefs.getBoolean("enabled",false)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("enabled",checked).apply(); if(checked) startTracking() else stopService(Intent(this@MainActivity,TrackingService::class.java)); refresh() }
        })
        button("Start persistent tracking") { prefs.edit().putBoolean("enabled",true).apply(); startTracking(); recreate() }
        button("Stop persistent tracking") { prefs.edit().putBoolean("enabled",false).apply(); stopService(Intent(this,TrackingService::class.java)); recreate() }
        button("Battery optimization settings") { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        label("Optional title suggestions read recognized controls in selected apps. One-shot OCR requires separate explicit permission; no automatic uploads.")
        button("Enable title detection (Accessibility)") {
            android.app.AlertDialog.Builder(this).setTitle("Optional title detection").setMessage("Android Accessibility can expose screen content. TraktBridge checks selected video apps for player titles and video detail headings near play actions. Suggestions stay local and are never uploaded automatically. Enable only if you agree.").setNegativeButton("Cancel",null).setPositiveButton("Agree and open settings") { _,_ -> prefs.edit().putBoolean("title_optin",true).apply(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
        }
        button("Disable title detection") {
            val edit=prefs.edit().putLong("ocr_generation",prefs.getLong("ocr_generation",0)+1).putBoolean("title_optin",false).remove("ocr_target").remove("ocr_after").remove("ocr_until").remove("ocr_choices").remove("ocr_source").remove("ocr_captured").remove("ocr_status")
            prefs.all.keys.filter { it.startsWith("title_hint:") || it.startsWith("title_detail:") || it.startsWith("title_time:") }.forEach { edit.remove(it) }
            edit.apply(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        label("Choose apps to track • select only video apps. Use the installed-app picker for any service or regional variant not listed.")
        button("Choose additional installed apps") { chooseInstalledApps() }
        label("For Netflix or other apps without media metadata, enable optional app-activity reminders. These do not prove playback.")
        button("Enable app-activity fallback") {
            if (!prefs.contains("usage_since")) prefs.edit().putLong("usage_since",System.currentTimeMillis()).apply()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        button("Review recent app activity (not watch time)") { reviewUsage() }
        button("Disable app-activity fallback") { prefs.edit().remove("usage_since").apply(); startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        for ((name,pkg) in apps.filter { (_,pkg) -> runCatching { packageManager.getApplicationInfo(pkg,0) }.isSuccess || pkg in (prefs.getStringSet("allowed",emptySet()) ?: emptySet()) }) root.addView(CheckBox(this).apply {
            text=name; isChecked=prefs.getStringSet("allowed",emptySet())?.contains(pkg)==true
            setOnCheckedChangeListener { _, checked ->
                val selected=prefs.getStringSet("allowed",emptySet())!!.toMutableSet()
                if (checked) selected.add(pkg) else selected.remove(pkg)
                prefs.edit().putStringSet("allowed",selected).apply()
            }
        })
        label("Diagnostics & sync")
        button("Retry approved sync queue") { HistorySync.schedule(this); refreshHistory() }
        historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        button("Refresh diagnostics") { refresh() }
        button("Clear diagnostic data") { prefs.edit().remove("sessions").remove("last_nonempty_sessions").remove("sample_error").apply(); refresh() }
        output=TextView(this).apply { setTextIsSelectable(true); textSize=14f }
        root.addView(output)
        refresh()
        showTab(tab)
        if(tvMode) nav.getChildAt(tab)?.requestFocus()
        handleCallback(intent)
        handleReview(intent)
        handleShared(intent)
        handleTrackingShortcut(intent)
        handleAutomation(intent)
        if(!prefs.getBoolean("setup_seen",false)) { prefs.edit().putBoolean("setup_seen",true).apply(); setupGuide() }
    }
    private fun betaFeedback() {
        val field=EditText(this).apply { hint="Describe the issue and steps to reproduce"; minLines=4; maxLines=8 }
        val info=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
        android.app.AlertDialog.Builder(this).setTitle("Beta feedback").setMessage("Review your text before sharing. No viewing history, account credentials or diagnostic logs are attached automatically.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Share") { _,_->
            val report="TraktBridge "+info+"\nAndroid "+android.os.Build.VERSION.SDK_INT+"\n"+field.text.toString().trim().take(4000)
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Share beta feedback")) }.onFailure { Toast.makeText(this,"No sharing app available",Toast.LENGTH_LONG).show() }
        }.show()
    }
    private fun checkUpdates() {
        lookup("Checking GitHub",{ done -> UpdateCheck.check(done) }) { latest:String ->
            val current=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
            val newer=runCatching { UpdateCheck.compare(latest,current)>0 }.getOrDefault(false)
            android.app.AlertDialog.Builder(this).setTitle("App updates").setMessage("Installed: "+current+"\nRepository: "+latest+"\n"+if(newer) "Newer source version available. Download only a successful permanently signed build." else "No newer repository version detected.").setNegativeButton("Close",null).setPositiveButton("GitHub builds") { _,_->startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/Roybabu/TraktBridge/actions"))) }.show()
        }
    }
    private fun libraryStatus(type:String,id:Long):String=personalStore("library_status").getString(type+":"+id,"").orEmpty()
    private fun setLibraryStatus(type:String,id:Long,label:String) {
        val statuses=listOf("Watching","Completed locally","Plan to watch","On hold","Dropped","Rewatching","Clear status")
        choose("Personal status • "+label,statuses) { index ->
            val edit=personalStore("library_status").edit()
            if(index==statuses.lastIndex) edit.remove(type+":"+id) else edit.putString(type+":"+id,statuses[index])
            edit.apply(); refreshHistory()
        }
    }
    private fun shareCollections() {
        val store=personalStore("collections")
        val names=store.all.keys.sorted()
        choose("Collection exchange",listOf("Share a collection","Import collection text")) { action ->
            if(action==0) choose("Select collection",names) { index ->
                val name=names[index]
                val payload=org.json.JSONObject().put("format","traktbridge-collection-v1").put("name",name).put("items",JSONArray(store.getString(name,"[]"))).toString()
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,payload),"Share collection"))
            } else {
                val field=EditText(this).apply { hint="Paste a shared TraktBridge collection"; minLines=4 }
                android.app.AlertDialog.Builder(this).setTitle("Import collection").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Preview") { _,_->
                    val parsed=runCatching { CollectionExchange.parse(field.text.toString()) }
                    parsed.fold({ incoming ->
                        android.app.AlertDialog.Builder(this).setTitle("Import "+incoming.first+"?").setMessage(incoming.second.length().toString()+" titles. Existing collections will not be overwritten.").setNegativeButton("Cancel",null).setPositiveButton("Import") { _,_->
                            var name=incoming.first; var suffix=2
                            while(store.contains(name)) { name=incoming.first+" ("+suffix+")"; suffix++ }
                            store.edit().putString(name,incoming.second.toString()).apply()
                            Toast.makeText(this,"Imported "+name,Toast.LENGTH_LONG).show()
                        }.show()
                    },{ Toast.makeText(this,"Invalid collection format",Toast.LENGTH_LONG).show() })
                }.show()
            }
        }
    }
    private fun applyScreenProtection() {
        if(prefs.getBoolean("secure_screen",false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    }
    private fun privacyDashboard() {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        fun status(label:String,enabled:Boolean) { body.addView(txt(label+": "+if(enabled) "On" else "Off",15,ink)) }
        status("Playback tracking",prefs.getBoolean("enabled",false))
        status("Title suggestions",prefs.getBoolean("title_optin",false))
        status("Usage fallback",prefs.contains("usage_since") && UsageFallback.allowed(this))
        status("Device credential lock",prefs.getBoolean("privacy_lock",false))
        status("Screenshot protection",prefs.getBoolean("secure_screen",false))
        status("Daily encrypted backup",prefs.getBoolean("daily_backup",false))
        status("Automatic remote refresh",prefs.getString("auto_refresh_account","").orEmpty().isNotBlank())
        body.addView(txt("Local viewing SQLite is not encrypted. Remote history cache, OAuth credentials and optional encrypted backups use device-backed or password-based encryption.",14,muted))
        body.addView(actionButton("Pause tracking") { privacyMode() })
        body.addView(actionButton("Disable automatic remote refresh") { RemoteRefreshSchedule.disable(this); Toast.makeText(this,"Remote refresh disabled",Toast.LENGTH_SHORT).show() })
        body.addView(actionButton("Delete downloaded remote history") {
            android.app.AlertDialog.Builder(this).setTitle("Clear cached history?").setMessage("Local viewing records and your Trakt account are unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_->TraktLibrary.clearCache(this) }.show()
        })
        body.addView(actionButton("Export encrypted history") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-history.tbenc"),63) })
        android.app.AlertDialog.Builder(this).setTitle("Privacy dashboard").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun privacyMode() {
        android.app.AlertDialog.Builder(this).setTitle("Pause all playback tracking?").setMessage("Sampling, title hints and optional usage reminders stop. Existing history and already approved uploads are retained. Restart tracking manually in Settings.").setNegativeButton("Cancel",null).setPositiveButton("Pause tracking") { _,_->
            val generation=prefs.getLong("ocr_generation",0)+1
            val edit=prefs.edit().putBoolean("enabled",false).putBoolean("title_optin",false).remove("usage_since").remove("sessions").remove("last_nonempty_sessions")
            prefs.all.keys.filter { it.startsWith("ocr_") || it.startsWith("title_hint:") || it.startsWith("title_detail:") || it.startsWith("title_time:") }.forEach { edit.remove(it) }
            edit.putLong("ocr_generation",generation).apply(); stopService(Intent(this,TrackingService::class.java)); recreate()
        }.show()
    }
    private fun configureGoal() {
        val field=EditText(this).apply { hint="Monthly sampled hours (0 disables goal)"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("monthly_goal",0).toString()) }
        android.app.AlertDialog.Builder(this).setTitle("Optional viewing goal").setMessage("Track a personal time budget. There are no rewards or pressure to watch more.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
            val hours=field.text.toString().toIntOrNull()
            if(hours!=null && hours in 0..744) { prefs.edit().putInt("monthly_goal",hours).apply(); refreshHistory() }
        }.show()
    }
    private fun monthlyGoalCard(parent:LinearLayout,records:List<WatchRecord>) {
        val hours=prefs.getInt("monthly_goal",0)
        if(hours<=0) return
        val month=java.time.YearMonth.now()
        val total=records.filter { java.time.YearMonth.from(ViewingAnalytics.day(it.ended))==month }.sumOf { it.watched }
        val card=panel(parent)
        card.addView(txt("Monthly viewing budget",18,ink,true))
        card.addView(txt(durationText(total)+" / "+hours+" hours",14,muted))
        card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(total.toDouble()/(hours*3600000L)*100).toInt().coerceIn(0,100) })
        card.addView(actionButton("Adjust budget") { configureGoal() })
    }
    private fun smartWatchlists() {
        val store=personalStore("smart_watchlists")
        val lists=store.all.entries.mapNotNull { (key,value) -> runCatching {
            val o=org.json.JSONObject(value as String)
            SmartWatchlist(key,o.getString("rule"),o.optString("type"),o.optString("source"))
        }.getOrNull() }.filter { it.rule in SmartWatchlists.rules }.sortedBy { it.name }
        choose("Smart watchlists",lists.map { it.name }+listOf("Create automatic watchlist")) { i ->
            if(i==lists.size) {
                val field=EditText(this).apply { hint="Watchlist name"; setSingleLine(true) }
                android.app.AlertDialog.Builder(this).setTitle("New smart watchlist").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
                    val name=field.text.toString().trim().take(80)
                    if(name.isBlank() || store.contains(name) || lists.size>=50) { Toast.makeText(this,"Use a unique name; maximum 50 lists",Toast.LENGTH_LONG).show(); return@setPositiveButton }
                    choose("Automatic categorization",listOf("Unfinished","Under 90 minutes remaining","Needs review","Rewatches","Last 7 days","Completed")) { rule ->
                        choose("Content type",listOf("All","Movies","Episodes")) { type ->
                            val sources=listOf("")+history.records(0).map { it.source }.distinct().sorted()
                            choose("Platform",sources.map { if(it.isBlank()) "All platforms" else appLabel(it) }) { source ->
                                store.edit().putString(name,org.json.JSONObject().put("rule",SmartWatchlists.rules[rule]).put("type",listOf("","movie","episode")[type]).put("source",sources[source]).toString()).apply()
                                smartWatchlists()
                            }
                        }
                    }
                }.show()
            } else {
                val list=lists[i]
                val rows=SmartWatchlists.select(ViewingGroups.build(history.records(0)),list,System.currentTimeMillis())
                val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                body.addView(txt(rows.size.toString()+" matching titles • updated from local history",14,muted))
                rows.take(100).forEach { titleCard(body,it,false) }
                android.app.AlertDialog.Builder(this).setTitle(list.name).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Close",null).setNeutralButton("Delete list") { _,_->store.edit().remove(list.name).apply() }.show()
            }
        }
    }
    private fun smartCollections() {
        val groups=ViewingGroups.build(history.records(0))
        val choices=listOf("Unfinished viewing","Movies under 90 minutes","Favorites","Most watched","Needs identification","Rewatched titles")
        choose("Smart collections",choices) { index ->
            val rows=when(index) {
                0->SeriesLibrary.continueWatching(groups)
                1->groups.filter { it.representative.matchType=="movie" && it.durationMs in 1..5400000 }
                2->groups.filter { val r=it.representative; r.traktId>0 && isFavorite(r.matchType,r.traktId) }
                3->groups.sortedByDescending { it.sampledMs }
                4->groups.filter { it.representative.traktId<=0 }
                else->groups.filter { it.sessions.count { r->r.status=="synced" }>1 }
            }
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            body.addView(txt(rows.size.toString()+" titles • first 50 shown",14,muted))
            rows.take(50).forEach { titleCard(body,it,false) }
            android.app.AlertDialog.Builder(this).setTitle(choices[index]).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun collectionProfiles() {
        val store=getSharedPreferences("collection_profiles",MODE_PRIVATE)
        val names=(store.getStringSet("names",emptySet()) ?: emptySet()).sorted()
        choose("Collection profiles",listOf("Default")+names+listOf("Create profile")) { index ->
            if(index==names.size+1) {
                val field=EditText(this).apply { hint="Profile label" }
                android.app.AlertDialog.Builder(this).setTitle("New collection profile").setMessage("Only collections and personal notes are separated. Playback history and Trakt account remain shared on this device.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Create") { _,_->
                    val name=field.text.toString().trim().take(40)
                    if(name.isNotBlank() && name!="Default") {
                        store.edit().putStringSet("names",names.toSet()+name).putString("selected",name).apply()
                        recreate()
                    }
                }.show()
            } else { store.edit().putString("selected",if(index==0) "" else names[index-1]).apply(); recreate() }
        }
    }
    private fun personalStore(name:String):android.content.SharedPreferences {
        val profile=getSharedPreferences("collection_profiles",MODE_PRIVATE).getString("selected","").orEmpty()
        val suffix=if(profile.isBlank()) "" else ":"+java.security.MessageDigest.getInstance("SHA-256").digest(profile.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return getSharedPreferences(name+suffix,MODE_PRIVATE)
    }
    private fun organizedLibrary() {
        val tagsByKey=personalStore("title_notes").all.mapValues { (_,v) -> runCatching { LibraryOrganization.tags(org.json.JSONObject(v as String).optString("tags")) }.getOrDefault(emptyList()) }
        val store=personalStore("collections")
        val names=listOf("")+store.all.keys.sorted()
        val tags=listOf("")+tagsByKey.values.flatten().distinct().sorted()
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val q=EditText(this).apply { hint="Search titles, episode names and tags"; setSingleLine(true) }; body.addView(q)
        fun pick(label:String,values:List<String>):Spinner {
            body.addView(txt(label,14,muted))
            return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,values); body.addView(this) }
        }
        val tag=pick("Tag",tags.map { it.ifBlank { "All tags" } })
        val folder=pick("Collection folder",names.map { it.ifBlank { "All folders" } })
        val sorts=listOf("recent","title","watchtime","remaining","oldest")
        val sort=pick("Sort",listOf("Recent","Title A–Z","Most watched","Remaining time","Oldest first"))
        android.app.AlertDialog.Builder(this).setTitle("Organize library").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setNeutralButton("Manage folders") { _,_->collections() }.setPositiveButton("Search") { _,_->
            val name=names[folder.selectedItemPosition]
            val keys=if(name.isBlank()) null else runCatching { val a=JSONArray(store.getString(name,"[]")); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("key") }.toSet() }.getOrDefault(emptySet())
            val rows=LibraryOrganization.search(ViewingGroups.build(history.records(0)),q.text.toString(),tags[tag.selectedItemPosition],keys,tagsByKey,sorts[sort.selectedItemPosition])
            val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            results.addView(txt(rows.size.toString()+" titles • first 100 shown",14,muted))
            rows.take(100).forEach { titleCard(results,it,false) }
            android.app.AlertDialog.Builder(this).setTitle("Library search results").setView(ScrollView(this).apply { addView(results) }).setPositiveButton("Close",null).show()
        }.show()
    }
    private fun collections() {
        val store=personalStore("collections")
        val names=store.all.keys.sorted()
        choose("Collections",names+"Create collection") { i ->
            if(i==names.size) {
                val field=EditText(this).apply { hint="Collection name" }
                android.app.AlertDialog.Builder(this).setTitle("New collection").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Create") { _,_->
                    val name=LibraryOrganization.folder(field.text.toString())
                    if(name!=null && !store.contains(name) && store.all.size<200) store.edit().putString(name,"[]").apply()
                }.show()
            } else {
                val name=names[i]; val a=JSONArray(store.getString(name,"[]"))
                val rows=(0 until a.length()).map { a.getJSONObject(it) }
                choose(name,rows.map { it.optString("label") }) { j ->
                    android.app.AlertDialog.Builder(this).setTitle(rows[j].optString("label")).setNegativeButton("Close",null).setPositiveButton("Remove from collection") { _,_->
                        val next=JSONArray(); rows.filterIndexed { k,_->k!=j }.forEach { next.put(it) }; store.edit().putString(name,next.toString()).apply()
                    }.show()
                }
            }
        }
    }
    private fun addCollection(type:String,id:Long,label:String) {
        val store=personalStore("collections"); val names=store.all.keys.sorted()
        if(names.isEmpty()) { collections(); return }
        choose("Add to collection",names) { i ->
            val name=names[i]; val a=JSONArray(store.getString(name,"[]"))
            if((0 until a.length()).none { a.optJSONObject(it)?.optString("key")==type+":"+id }) {
                a.put(org.json.JSONObject().put("key",type+":"+id).put("label",label)); store.edit().putString(name,a.toString()).apply()
            }
            Toast.makeText(this,"Collection updated",Toast.LENGTH_SHORT).show()
        }
    }
    private fun isFavorite(type:String,id:Long)=personalStore("favorites").contains(type+":"+id)
    private fun toggleFavorite(type:String,id:Long,label:String) {
        val store=personalStore("favorites")
        val key=type+":"+id
        if(store.contains(key)) store.edit().remove(key).apply() else store.edit().putString(key,label).apply()
    }
    private fun favorites() {
        val store=personalStore("favorites")
        val items=store.all.entries.sortedBy { it.value.toString() }
        choose("Favorite titles",items.map { it.value.toString() }) { i ->
            val entry=items[i]; val type=entry.key.substringBefore(":"); val id=entry.key.substringAfter(":").toLongOrNull() ?: return@choose
            android.app.AlertDialog.Builder(this).setTitle(entry.value.toString()).setNegativeButton("Close",null).setNeutralButton("Remove") { _,_->store.edit().remove(entry.key).apply(); favorites() }.setPositiveButton("Local history") { _,_->historyFilter=HistoryFilter(query=entry.value.toString().substringBefore(" • ")); showTab(1) }.show()
        }
    }
    private fun setupGuide() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val nm=getSystemService(android.app.NotificationManager::class.java)
        val component=android.content.ComponentName(this,PlaybackListener::class.java)
        val access=if(android.os.Build.VERSION.SDK_INT>=27) nm.isNotificationListenerAccessGranted(component) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==component }
        fun step(title:String,detail:String,action:()->Unit) {
            box.addView(txt(title,17,ink,true)); box.addView(txt(detail,13,muted)); box.addView(actionButton("Open "+title,action))
        }
        step("Notification access",if(access) "Granted" else "Required for media-session detection.") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        step("Video app selection",(prefs.getStringSet("allowed",emptySet())?.size ?: 0).toString()+" selected. Choose only apps you want tracked.") { chooseInstalledApps() }
        step("Battery settings","Optional: allow background operation if your device interrupts tracking.") { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        box.addView(txt("Accessibility title detection and app-activity fallback are optional and require separate consent in Settings. Trakt sign-in is not required for local tracking.",14,muted))
        android.app.AlertDialog.Builder(this).setTitle("Set up TraktBridge").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Later",null).setPositiveButton("Done") { _,_->showTab(4) }.show()
    }
    private fun handleTrackingShortcut(intent:Intent) {
        if(intent.action!="com.traktbridge.app.OPEN_TRACKING") return
        intent.action=null
        showTab(4)
        android.app.AlertDialog.Builder(this).setTitle("Start playback tracking?").setMessage("Track selected video apps in the background.").setNegativeButton("Cancel",null).setPositiveButton("Start") { _,_->prefs.edit().putBoolean("enabled",true).apply(); startTracking(); recreate() }.show()
    }
    private fun startTracking() {
        runCatching {
            startForegroundService(Intent(this,TrackingService::class.java))
            android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this,PlaybackListener::class.java))
        }.onFailure { DebugTrace.failure("foreground",it); Toast.makeText(this,"Unable to start tracking",Toast.LENGTH_LONG).show() }
    }
    override fun onPause() { super.onPause(); if(prefs.getBoolean("privacy_lock",false) && !privacyPrompt) { unlocked=false; if(::shell.isInitialized) shell.visibility=android.view.View.INVISIBLE } }
    override fun onResume() { super.onResume(); if (::output.isInitialized) refresh(); if (::historyList.isInitialized) refreshHistory(); requirePrivacyUnlock() }
    private fun requirePrivacyUnlock() {
        if(!::shell.isInitialized) return
        if(!prefs.getBoolean("privacy_lock",false) || unlocked) { shell.visibility=android.view.View.VISIBLE; return }
        shell.visibility=android.view.View.INVISIBLE
        if(privacyPrompt || isFinishing) return
        val manager=getSystemService(android.app.KeyguardManager::class.java)
        val intent=manager.createConfirmDeviceCredentialIntent("Unlock TraktBridge","Verify your device credential to view personal history")
        if(intent==null) { finish(); return }
        privacyPrompt=true
        runCatching { startActivityForResult(intent,96) }.onFailure { privacyPrompt=false; finish() }
    }
    override fun onDestroy() { historyRequest++; historyTask?.cancel(true); historyExecutor.shutdownNow(); if (::history.isInitialized) history.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab",tab); outState.putString("import_source",importSource); outState.putString("filter_q",historyFilter.query); outState.putString("filter_source",historyFilter.source); outState.putInt("filter_period",historyFilter.period); outState.putString("filter_type",historyFilter.type); outState.putString("filter_status",historyFilter.status); outState.putString("filter_sort",historyFilter.sort); outState.putInt("analytics_period",analyticsPeriod); outState.putString("analytics_source",analyticsSource); outState.putString("calendar_month",calendarMonth.toString()); super.onSaveInstanceState(outState) }
    private fun dp(n:Int)= (n*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color:Int,radius:Int=16)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun txt(value:String,size:Int=15,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size.toFloat()*when(prefs.getInt("reading_size",0)) { 1->1.15f; 2->1.3f; else->1f }; setTextColor(color); if(bold) setTypeface(null,android.graphics.Typeface.BOLD)
    }
    private fun actionButton(label:String,action:()->Unit)=Button(this).apply {
        text=label; contentDescription=label; isAllCaps=false; isFocusable=true; if(tvMode) { minWidth=dp(120); setOnFocusChangeListener { view,focused -> view.alpha=if(focused) 1f else 0.85f; view.scaleX=if(focused) 1.03f else 1f; view.scaleY=if(focused) 1.03f else 1f } }; setTextColor(ink); textSize=14f*when(prefs.getInt("reading_size",0)) { 1->1.15f; 2->1.3f; else->1f }; background=rounded(0xff302a48.toInt(),12); minHeight=dp(48)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) }; setOnClickListener { action() }
    }
    private fun panel(parent:LinearLayout):LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; background=rounded(surface); setPadding(dp(16),dp(16),dp(16),dp(16))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun section(parent:LinearLayout,title:String) { parent.addView(txt(title,20,ink,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(12) }) }
    override fun onKeyDown(keyCode:Int,event:android.view.KeyEvent):Boolean {
        if(tvMode && keyCode==android.view.KeyEvent.KEYCODE_MENU && ::nav.isInitialized) {
            nav.getChildAt(tab)?.requestFocus()
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    private fun showTab(index:Int) {
        if(index !in 0..4) return
        tab=index; settingsPage.visibility=if(index==4) android.view.View.VISIBLE else android.view.View.GONE
        contentPage.visibility=if(index==4) android.view.View.GONE else android.view.View.VISIBLE
        for(i in 0 until nav.childCount) (nav.getChildAt(i) as TextView).apply { setTextColor(if(i==index) violet else muted); background=rounded(if(i==index) 0xff302a48.toInt() else surface,12) }
        refreshHistory(); contentPage.scrollTo(0,0)
    }
    private fun refreshHistory() {
        if(!::dashboard.isInitialized || tab==4 || isFinishing || isDestroyed) return
        val request=++historyRequest
        val selectedTab=tab
        historyTask?.cancel(true)
        dashboard.removeAllViews()
        dashboard.addView(txt("Loading viewing history…",15,muted))
        val app=applicationContext
        historyTask=historyExecutor.submit {
            val result=runCatching { WatchHistory(app).use { db -> val rows=db.records(0); rows to ViewingGroups.build(rows) } }
            if(!Thread.currentThread().isInterrupted) runOnUiThread {
                if(isDestroyed || isFinishing || request!=historyRequest || tab!=selectedTab) return@runOnUiThread
                result.fold({ (records,groups) -> renderHistory(records,groups) }, {
                    dashboard.removeAllViews()
                    dashboard.addView(txt("Viewing history unavailable. Existing data has not been cleared.",15,muted))
                    dashboard.addView(actionButton("Retry") { refreshHistory() })
                    dashboard.addView(actionButton("Check database integrity") { checkHistoryIntegrity() })
                })
            }
        }
    }
    private fun renderHistory(records:List<WatchRecord>,groups:List<ViewingTitle>) {
        dashboard.removeAllViews()
        val pending=groups.count { it.queued || it.pending }
        when(tab) {
            0 -> {
                val age=System.currentTimeMillis()-prefs.getLong("last_sample",0)
                val live=prefs.getBoolean("enabled",false) && prefs.getBoolean("listener_connected",false) && age in 0..20000
                val weekly=ViewingAnalytics.filter(records,HistoryFilter(period=7))
                dashboard.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeHomeSummary(violet,live,pending,durationText(weekly.sumOf { it.watched }),{ showTab(4) },{ showTab(2) },{ discover() },{ viewingPlanner() }) } })
                metricGrid(dashboard,listOf("This week" to durationText(weekly.sumOf { it.watched }),"Movies synced" to groups.count { it.confirmed && it.representative.matchType=="movie" }.toString(),"Episodes synced" to groups.count { it.confirmed && it.representative.matchType=="episode" }.toString(),"Needs review" to pending.toString()))
                if(prefs.getBoolean("show_monthly_goal",true)) monthlyGoalCard(dashboard,records)
                section(dashboard,"Now watching")
                dashboard.addView(actionButton("Open live Now Playing") { nowPlaying() })
                val active=groups.filter { it.active }
                if(active.isEmpty()) panel(dashboard).addView(txt("No verified active playback. Play a video in a selected app.",15,muted)) else cards(dashboard,active.take(2)) { parent,g -> titleCard(parent,g,false) }
                section(dashboard,"Continue watching")
                SeriesLibrary.continueWatching(groups).firstOrNull()?.let { heroCard(dashboard,it) }
                val continuing=SeriesLibrary.continueWatching(groups)
                if(continuing.isEmpty()) panel(dashboard).addView(txt("No unfinished sampled viewing yet.",15,muted)) else cards(dashboard,continuing.drop(1).take(6)) { parent,g -> titleCard(parent,g,false) }
                section(dashboard,"Review queue")
                val q=panel(dashboard); q.addView(txt("$pending awaiting action",22,ink,true)); q.addView(actionButton("Open review queue") { showTab(2) }); q.addView(actionButton("Review app activity") { reviewUsage() })
                dashboard.addView(actionButton("Ask your viewing assistant") { localAssistant() })
                dashboard.addView(actionButton("Upcoming episodes") { upcomingEpisodes() })
                dashboard.addView(actionButton("Recommendations from your history") { recommendFromHistory() })
                dashboard.addView(actionButton("Anime catalogue (AniList)") { animeSearch() })
                dashboard.addView(actionButton("Open Trakt account library") { openTraktLibrary() })
                section(dashboard,"Recently watched")
                val recent=groups.filter { !it.active }.take(5)
                if(recent.isEmpty()) panel(dashboard).addView(txt("Your viewing history will appear here.",15,muted)) else cards(dashboard,recent) { parent,g -> titleCard(parent,g,false) }
            }
            1,2 -> {
                if(tab==1) dashboard.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeLibraryToolbar(violet,groups.size.toString()+" identified titles and provisional groups",{ universalSearch() },{ editHistoryFilter(records) },{ manualLog() }) } })
                else { section(dashboard,"Needs your review"); dashboard.addView(actionButton("Log a watch manually") { manualLog() }) }
                if(tab==1) { dashboard.addView(actionButton("Unified movie, TV and anime library") { unifiedLibrary() }); dashboard.addView(actionButton("Advanced search • tags and folders") { organizedLibrary() }) }
                dashboard.addView(actionButton("Refresh") { refreshHistory() })
                if(tab==2) { dashboard.addView(actionButton("Review recent app activity") { reviewUsage() }); dashboard.addView(actionButton("Retry approved sync queue") { HistorySync.schedule(this); refreshHistory() }) }
                if(tab==1) {
                    dashboard.addView(txt("Filters: "+listOfNotNull(historyFilter.query.takeIf { it.isNotBlank() }?.let { "Search: "+it },historyFilter.source.takeIf { it.isNotBlank() }?.let { appLabel(it) },historyFilter.period.takeIf { it>0 }?.let { "Last "+it+" days" },historyFilter.type.takeIf { it.isNotBlank() },historyFilter.status.takeIf { it.isNotBlank() } ).joinToString(" • ").ifBlank { "All viewing" },13,muted))
                    if(historyFilter!=HistoryFilter()) dashboard.addView(actionButton("Reset filters") { historyFilter=HistoryFilter(); historyLimit=100; refreshHistory() })
                }
                val items=if(tab==1) ViewingAnalytics.sort(ViewingGroups.build(ViewingAnalytics.filter(records,historyFilter)),historyFilter.sort) else groups.filter { it.queued || it.pending }
                if(items.isEmpty()) panel(dashboard).addView(txt(if(tab==1) "No viewing records yet." else "You're all caught up.",16,muted))
                val visible=items.take(historyLimit)
                if(items.size>visible.size) dashboard.addView(actionButton("Show more ("+visible.size+" of "+items.size+")") { historyLimit+=100; refreshHistory() })
                if(tab==1) {
                    val series=SeriesLibrary.build(visible)
                    if(series.isNotEmpty()) { section(dashboard,"Series and anime"); cards(dashboard,series) { parent,item -> seriesCard(parent,item) } }
                    val movies=visible.filter { it.representative.matchType=="movie" }
                    if(movies.isNotEmpty()) { section(dashboard,"Movies"); cards(dashboard,movies) { parent,item -> titleCard(parent,item,true) } }
                    val other=visible.filter { it.representative.matchType!="movie" && !(it.representative.matchType=="episode" && it.representative.showId>0 && it.representative.season>=0 && it.representative.episode>0) }
                    if(other.isNotEmpty()) { section(dashboard,"Unidentified viewing"); cards(dashboard,other) { parent,item -> titleCard(parent,item,true) } }
                } else cards(dashboard,visible) { parent,item -> titleCard(parent,item,true) }
            }
            3 -> {
                section(dashboard,"Viewing activity")
                dashboard.addView(actionButton(if(analyticsPeriod==0) "Period: All time" else "Period: Last "+analyticsPeriod+" days") {
                    val periods=listOf(7,30,90,0); choose("Analytics period",listOf("Last 7 days","Last 30 days","Last 90 days","All time")) { analyticsPeriod=periods[it]; refreshHistory() }
                })
                val sources=records.map { it.source }.distinct().sortedBy { appLabel(it) }
                dashboard.addView(actionButton("Platform: "+analyticsSource.takeIf { it.isNotBlank() }?.let { appLabel(it) }.orEmpty().ifBlank { "All" }) {
                    choose("Filter viewing activity",listOf("All platforms")+sources.map { appLabel(it) }) { i -> analyticsSource=if(i==0) "" else sources[i-1]; refreshHistory() }
                })
                val scoped=ViewingAnalytics.filter(records,HistoryFilter(period=analyticsPeriod,source=analyticsSource))
                val scopedGroups=ViewingGroups.build(scoped)
                metricGrid(dashboard,listOf("Sampled watch time" to durationText(scoped.sumOf { it.watched }),"Movies synced" to scopedGroups.count { it.confirmed && it.representative.matchType=="movie" }.toString(),"Episodes synced" to scopedGroups.count { it.confirmed && it.representative.matchType=="episode" }.toString(),"Platforms" to scoped.filter { it.watched>0 }.map { it.source }.distinct().size.toString(),"Paused" to durationText(scoped.sumOf { it.pausedMs }),"Buffering" to durationText(scoped.sumOf { it.bufferingMs }),"Seeks" to scoped.sumOf { it.seeks }.toString(),"Uncertain" to durationText(scoped.sumOf { it.uncertainMs })))
                dashboard.addView(actionButton("Subscription costs") { subscriptions() })
                dashboard.addView(actionButton("Streaming service dashboard") { serviceDashboard() })
                section(dashboard,"Watch time by platform")
                val totals=ViewingAnalytics.platforms(scoped).map { it.source to it.sampledMs }
                val max=totals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                val chart=panel(dashboard)
                if(totals.isEmpty()) chart.addView(txt("No sampled activity yet.",15,muted))
                totals.forEach { (pkg,time) ->
                    chart.addView(actionButton(appLabel(pkg)+" • "+durationText(time)) { analyticsSource=pkg; refreshHistory() })
                    chart.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(time.toDouble()/max*100).toInt(); progressTintList=android.content.res.ColorStateList.valueOf(violet) },LinearLayout.LayoutParams(-1,dp(12)).apply { bottomMargin=dp(14) })
                }
                chart.addView(txt("App-activity and remote history are excluded from sampled time. Date filters use session end date.",13,muted))
                dashboard.addView(actionButton("Viewing journal and yearly recap") { viewingJournal() })
                section(dashboard,"Viewing calendar")
                calendarView(dashboard,if(analyticsSource.isBlank()) records else records.filter { it.source==analyticsSource })

            }
        }
    }
    private fun streamingAvailability(type:String,id:Long,title:String) {
        val initial=prefs.getString("availability_region",java.util.Locale.getDefault().country).orEmpty()
        val field=EditText(this).apply { hint="Country code, e.g. IN, AE, US"; setSingleLine(true); setText(initial) }
        android.app.AlertDialog.Builder(this).setTitle("Where to watch").setMessage("Choose your country. JustWatch searches by title; Trakt opens the exact catalogue ID. Availability, pricing and subscriptions must be verified on the destination site.").setView(field).setNegativeButton("Cancel",null).setNeutralButton("Trakt") { _,_->openTraktWatchPage(type,id) }.setPositiveButton("Search JustWatch") { _,_->
            val region=StreamingAvailability.region(field.text.toString())
            val url=region?.let { StreamingAvailability.search(title,it) }
            if(url==null) Toast.makeText(this,"Enter a valid two-letter country code and title.",Toast.LENGTH_LONG).show()
            else {
                prefs.edit().putString("availability_region",region).apply()
                runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }.onFailure { Toast.makeText(this,"No browser available",Toast.LENGTH_LONG).show() }
            }
        }.show()
    }
    private fun openTraktWatchPage(type:String,id:Long) {
        if(type !in setOf("movie","show") || id<=0) return
        // Resolve only through the official Trakt ID redirect; availability is not asserted.
        val url="https://trakt.tv/search/trakt/"+id+"?id_type="+type
        runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }.onFailure { Toast.makeText(this,"No browser available",Toast.LENGTH_LONG).show() }
    }
    private fun playbackShortcut() {
        val field=EditText(this).apply { hint="Netflix title ID or YouTube video ID"; setSingleLine(true) }
        choose("Playback shortcut",listOf("Netflix","YouTube","Open installed player")) { i ->
            if(i==2) { openPlaybackApp(PlayerRules.apps.values.toList()); return@choose }
            val source=if(i==0) "com.netflix.mediaclient" else "com.google.android.youtube"
            android.app.AlertDialog.Builder(this).setTitle("Open exact provider title").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Open") { _,_->
                val url=PlaybackShortcuts.providerLink(source,field.text.toString().trim())
                if(url==null) Toast.makeText(this,"Invalid provider ID",Toast.LENGTH_LONG).show()
                else runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }.onFailure { Toast.makeText(this,"No compatible app",Toast.LENGTH_LONG).show() }
            }.show()
        }
    }
    private fun openPlaybackApp(sources:List<String>) {
        val installed=sources.distinct().mapNotNull { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { pkg to it } }
        if(installed.isEmpty()) { Toast.makeText(this,"No recorded playback app is installed or launchable",Toast.LENGTH_LONG).show(); return }
        choose("Open recorded playback app",installed.map { appLabel(it.first) }) { i ->
            runCatching { startActivity(installed[i].second) }.onFailure { Toast.makeText(this,"Unable to open player",Toast.LENGTH_LONG).show() }
        }
    }
    private fun upcomingEpisodes() {
        lookup("Loading upcoming episodes",{ done -> TraktLibrary.upcoming(this,30,done) }) { rows:List<UpcomingEpisode> ->
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            body.addView(txt("Next 30 days • Trakt personal calendar. Air dates are not streaming availability.",14,muted))
            if(rows.isEmpty()) body.addView(txt("No upcoming episodes returned.",15))
            for(e in rows) {
                val card=panel(body)
                val date=java.time.Instant.parse(e.airs).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM • HH:mm"))
                card.addView(txt(date,14,violet,true)); card.addView(txt(e.show,18,ink,true));
                val at=java.time.Instant.parse(e.airs).toEpochMilli()
                if(at>System.currentTimeMillis()) {
                    card.addView(txt("Airs in "+durationText(at-System.currentTimeMillis()),13,muted))
                    card.addView(actionButton("Remind me near air time") {
                        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),42); Toast.makeText(this,"Grant notifications, then schedule again",Toast.LENGTH_LONG).show() }
                        else { prefs.edit().putBoolean("episode_reminders",true).apply(); EpisodeReminders.schedule(this,e); Toast.makeText(this,"Reminder scheduled; timing may vary",Toast.LENGTH_LONG).show() }
                    })
                }; card.addView(txt("S"+e.season+" E"+e.episode+if(prefs.getBoolean("hide_spoilers",true)) "" else " • "+e.title,14,muted))
            }
            android.app.AlertDialog.Builder(this).setTitle("Upcoming episodes").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun externalIdentity(record:WatchRecord) {
        val field=EditText(this).apply { hint="IMDb tt1234567 or TMDB numeric ID"; setSingleLine(true) }
        choose("External catalogue identifier",listOf("IMDb","TMDB")) { index ->
            val namespace=if(index==0) "imdb" else "tmdb"
            android.app.AlertDialog.Builder(this).setTitle("Enter "+namespace.uppercase()+" ID").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->
                val id=field.text.toString().trim()
                if(!id.matches(if(namespace=="imdb") Regex("tt[0-9]{5,12}") else Regex("[0-9]{1,12}"))) { Toast.makeText(this,"Invalid identifier",Toast.LENGTH_LONG).show(); return@setPositiveButton }
                lookup("Resolving external identifier",{ done -> TraktCatalogue.searchExternal(id,namespace,done) }) { rows:List<CatalogueItem> ->
                    choose("Confirm exact result",rows.map { it.label() }) { i ->
                        val item=rows[i]; if(item.type=="movie") confirmMatch(record,item) else selectSuggestedEpisode(record,item)
                    }
                }
            }.show()
        }
    }
    private fun editTitleNotes(type:String,id:Long,label:String) {
        val store=personalStore("title_notes")
        val key=type+":"+id
        val current=runCatching { org.json.JSONObject(store.getString(key,"{}")) }.getOrDefault(org.json.JSONObject())
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val notes=EditText(this).apply { hint="Your review or notes"; minLines=4; setText(current.optString("notes")) }
        val tags=EditText(this).apply { hint="Tags separated by commas"; setText(current.optString("tags")) }
        body.addView(notes); body.addView(tags)
        android.app.AlertDialog.Builder(this).setTitle(label).setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save privately") { _,_->
            store.edit().putString(key,org.json.JSONObject().put("label",label).put("notes",notes.text.toString().take(10000)).put("tags",LibraryOrganization.tags(tags.text.toString()).joinToString(",")).toString()).apply()
        }.show()
    }
    private fun franchises() {
        val guides=FranchiseTimeline.guides
        choose("Curated franchise timelines",guides.map { it.name }) { index ->
            val guide=guides[index]
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            body.addView(txt("Curated order of the listed films, not a complete franchise catalogue.",13,muted))
            guide.entries.forEachIndexed { i,entry ->
                body.addView(actionButton((i+1).toString()+". "+entry.title+" ("+entry.year+")") {
                    lookup("Checking catalogue identity",{ done -> TraktCatalogue.search(entry.title,"movie",done) }) { rows:List<CatalogueItem> ->
                        val exact=FranchiseTimeline.exact(entry,rows)
                        if(exact==null) choose("Verify exact film manually",rows.map { it.label() }) { j -> titleDetails("movie",rows[j].id,rows[j].title) }
                        else {
                            val watched=history.records(0).any { it.matchType=="movie" && it.traktId==exact.id && it.status=="synced" }
                            android.app.AlertDialog.Builder(this).setTitle(entry.title).setMessage(if(watched) "Synced completion in local history" else "No synced completion in local history").setNegativeButton("Close",null).setPositiveButton("Details") { _,_->titleDetails("movie",exact.id,exact.title) }.show()
                        }
                    }
                })
            }
            android.app.AlertDialog.Builder(this).setTitle(guide.name).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun trackingHealth() {
        val nm=getSystemService(android.app.NotificationManager::class.java)
        val listener=android.content.ComponentName(this,PlaybackListener::class.java)
        val access=if(android.os.Build.VERSION.SDK_INT>=27) nm.isNotificationListenerAccessGranted(listener) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==listener }
        val last=prefs.getLong("last_sample",0)
        val age=System.currentTimeMillis()-last
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        val power=getSystemService(android.os.PowerManager::class.java)
        metricGrid(body,listOf("Sampling enabled" to if(prefs.getBoolean("enabled",false)) "Yes" else "No","Listener access" to if(access) "Granted" else "Missing","Last sample" to if(last>0 && age>=0) (age/1000).toString()+"s ago" else "Unknown","Battery exemption" to if(power.isIgnoringBatteryOptimizations(packageName)) "Yes" else "No","Selected apps" to (prefs.getStringSet("allowed",emptySet())?.size ?: 0).toString(),"Review notifications" to if(ViewingNotifications.enabled(this)) "Enabled" else "Off"))
        body.addView(txt("Last sampling error: "+prefs.getString("sample_error","none"),14,muted))
        body.addView(actionButton("Open guided setup") { setupGuide() })
        body.addView(actionButton("Inspect live sessions") { liveInspector() })
        val groups=ViewingGroups.build(history.records(0)).filter { it.queued || it.pending }
        section(body,"Pending review and sync • "+groups.size)
        groups.take(20).forEach { titleCard(body,it,true) }
        android.app.AlertDialog.Builder(this).setTitle("Tracking health").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun recentlyDeleted() {
        val rows=history.trashed()
        choose("Recently deleted",rows.map { it.matchLabel.ifBlank { it.title.ifBlank { "Unidentified video" } } }) { i ->
            android.app.AlertDialog.Builder(this).setTitle("Restore viewing record?").setMessage("Restore for local review. Nothing will be uploaded.").setNegativeButton("Cancel",null).setPositiveButton("Restore") { _,_->history.restoreTrash(rows[i].id); refreshHistory() }.show()
        }
    }
    private fun peopleSearch(q:String) {
        lookup("Searching people",{ done -> TraktCatalogue.people(q,done) }) { rows:List<Pair<Long,String>> ->
            choose("Actors and directors",rows.map { it.second }) { index ->
                val p=rows[index]
                lookup("Loading movie credits",{ done -> TraktCatalogue.filmography(p.first,done) }) { movies:List<CatalogueItem> ->
                    choose(p.second+" • Movie credits",movies.map { it.label() }) { i -> titleDetails("movie",movies[i].id,movies[i].title) }
                }
            }
        }
    }
    private fun multiWatchPlan(budget:Long,candidates:List<ViewingTitle>) {
        var remaining=budget
        val chosen=mutableListOf<ViewingTitle>()
        for(g in candidates.sortedBy { it.remainingMs }) if(g.remainingMs in 1..remaining) { chosen.add(g); remaining-=g.remainingMs }
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt("Estimated plan: "+durationText(budget-remaining)+" • time left "+durationText(remaining),16,ink,true))
        if(chosen.isEmpty()) body.addView(txt("No known remaining times fit.",14,muted))
        chosen.forEachIndexed { i,g -> body.addView(txt((i+1).toString()+". "+g.representative.matchLabel.ifBlank { g.representative.title }+" • "+durationText(g.remainingMs),15)) }
        body.addView(txt("Based on last reported positions, not guaranteed runtime. No playback or uploads start automatically.",13,muted))
        android.app.AlertDialog.Builder(this).setTitle("Viewing plan").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun viewingPlanner() {
        val budgets=listOf(30,60,90,120,180)
        choose("Available viewing time",budgets.map { it.toString()+" minutes" }) { index ->
            val budget=budgets[index]*60000L
            val candidates=SeriesLibrary.continueWatching(ViewingGroups.build(history.records(0))).filter { it.positionMs>=0 && it.durationMs>0 && it.remainingMs in 1..budget }
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            body.addView(txt("Fits your time based on last reported position.",14,muted))
            if(candidates.isEmpty()) body.addView(txt("No unfinished titles with known remaining time fit this budget.",14))
            candidates.take(20).forEach { titleCard(body,it,false) }
            body.addView(actionButton("Build a multi-title viewing plan") { multiWatchPlan(budget,candidates) })
            body.addView(actionButton("Random movie from Trakt watchlist") {
                lookup("Loading watchlist",{ done -> TraktLibrary.page(this,"watchlist",1,done) }) { page:RemotePage ->
                    val movie=page.items.filter { it.type=="movie" }.randomOrNull()
                    if(movie==null) Toast.makeText(this,"No movies on the first watchlist page",Toast.LENGTH_LONG).show()
                    else titleDetails("movie",movie.id,movie.title)
                }
            })
            android.app.AlertDialog.Builder(this).setTitle("Watch planner").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun subscriptions() {
        val store=getSharedPreferences("subscriptions",MODE_PRIVATE)
        val sources=((prefs.getStringSet("allowed",emptySet()) ?: emptySet())+history.records(0).map { it.source }+store.all.keys).distinct().sortedBy { appLabel(it) }
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        val month=java.time.YearMonth.now()
        val rows=history.records(0).filter { java.time.YearMonth.from(ViewingAnalytics.day(it.ended))==month }
        var total=0.0
        for(pkg in sources) {
            val data=runCatching { org.json.JSONObject(store.getString(pkg,"{}")) }.getOrDefault(org.json.JSONObject())
            val cost=data.optDouble("cost",0.0)
            val hours=rows.filter { it.source==pkg }.sumOf { it.watched }/3600000.0
            total+=cost
            val card=panel(body); card.addView(txt(appLabel(pkg),18,ink,true))
            card.addView(txt("Monthly INR: "+String.format(java.util.Locale.US,"%.2f",cost)+" • "+durationText((hours*3600000).toLong()),14,muted))
            if(hours>0 && cost>0) card.addView(txt("Cost per sampled hour: ₹"+String.format(java.util.Locale.US,"%.2f",cost/hours),14,violet))
            if(data.optString("renewal").isNotBlank()) card.addView(txt("Renewal: "+data.optString("renewal"),14,muted))
            card.addView(actionButton("Edit monthly cost and renewal") {
                val fields=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
                val price=EditText(this).apply { hint="Monthly cost in INR"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(cost.toString()) }
                val renewal=EditText(this).apply { hint="Renewal date YYYY-MM-DD (optional)"; setText(data.optString("renewal")) }
                fields.addView(price); fields.addView(renewal)
                android.app.AlertDialog.Builder(this).setTitle(appLabel(pkg)).setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
                    val amount=price.text.toString().toDoubleOrNull()
                    val date=renewal.text.toString().trim()
                    if(amount==null || !amount.isFinite() || amount !in 0.0..1000000.0 || (date.isNotBlank() && runCatching { java.time.LocalDate.parse(date) }.isFailure)) Toast.makeText(this,"Invalid amount or date",Toast.LENGTH_LONG).show()
                    else { store.edit().putString(pkg,org.json.JSONObject().put("cost",amount).put("renewal",date).toString()).apply(); Toast.makeText(this,"Saved. Reopen dashboard to refresh.",Toast.LENGTH_SHORT).show() }
                }.show()
            })
        }
        body.addView(txt("Total monthly cost: ₹"+String.format(java.util.Locale.US,"%.2f",total),20,ink,true))
        body.addView(txt("Amounts are entered manually. Sampled hours can be incomplete; renewal dates are not billing confirmations.",13,muted))
        android.app.AlertDialog.Builder(this).setTitle("Subscription manager").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun serviceDashboard() {
        val rows=history.records(0)
        val selected=prefs.getStringSet("allowed",emptySet()) ?: emptySet()
        val sources=(rows.map { it.source }+selected).distinct().sortedBy { appLabel(it) }
        choose("My streaming services",sources.map { pkg -> appLabel(pkg)+" • "+durationText(ViewingAnalytics.filter(rows,HistoryFilter(source=pkg,period=30)).sumOf { it.watched }) }) { index ->
            val pkg=sources[index]
            val local=rows.filter { it.source==pkg }
            val month=ViewingAnalytics.filter(local,HistoryFilter(period=30))
            val groups=ViewingGroups.build(local)
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            metricGrid(body,listOf("Last 30 days" to durationText(month.sumOf { it.watched }),"Sessions" to month.size.toString(),"Synced titles" to groups.count { it.confirmed }.toString(),"Selected for tracking" to if(pkg in selected) "Yes" else "No"))
            body.addView(actionButton("Open player") { openPlaybackApp(listOf(pkg)) })
            body.addView(actionButton("Filter all history") { historyFilter=HistoryFilter(source=pkg); historyLimit=100; showTab(1) })
            section(body,"Continue watching")
            SeriesLibrary.continueWatching(groups).take(8).forEach { titleCard(body,it,false) }
            android.app.AlertDialog.Builder(this).setTitle(appLabel(pkg)).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun automationShortcuts() {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt("Use Tasker or a supported Android automation tool to open these app links. Tracking changes and Trakt uploads require your confirmation.",15,muted))
        for(action in listOf("home","review","manual","tracking")) {
            val link="traktbridge://shortcut/"+action
            body.addView(txt(link,14,ink))
            body.addView(actionButton("Copy "+action+" link") {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("TraktBridge shortcut",link))
            })
        }
        android.app.AlertDialog.Builder(this).setTitle("Automation shortcuts").setView(body).setPositiveButton("Close",null).show()
    }
    private fun handleAutomation(i:Intent) {
        val uri=i.data ?: return
        if(i.action!=Intent.ACTION_VIEW || uri.scheme!="traktbridge" || uri.host!="shortcut" || prefs.getBoolean("privacy_lock",false) && !unlocked) return
        i.data=null
        when(PlaybackShortcuts.shortcut(uri.toString())) {
            "home" -> showTab(0)
            "review" -> showTab(2)
            "manual" -> manualLog()
            "tracking" -> showTab(4)
            "library" -> showTab(1)
            "activity" -> showTab(3)
        }
    }
    private fun localAssistant() {
        val field=EditText(this).apply { hint="Try: unfinished movies or 90 minutes"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this).setTitle("Viewing assistant").setMessage("Local command matching, not a remote AI service. Supports watch history, time budgets, unfinished titles and subscription summaries.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Find") { _,_->
            val q=LocalAssistant.parse(field.text.toString())
            when(q.action) {
                "subscriptions"->subscriptions()
                "recommend"->recommendFromHistory()
                "history"->{ historyFilter=HistoryFilter(period=q.days); historyLimit=100; showTab(1) }
                "unfinished","budget"->{
                    val rows=SeriesLibrary.continueWatching(ViewingGroups.build(history.records(0))).filter { q.action!="budget" || it.remainingMs in 1..(q.minutes*60000L) }
                    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                    if(rows.isEmpty()) body.addView(txt("No matching unfinished viewing with available progress.",14,muted))
                    rows.take(30).forEach { titleCard(body,it,false) }
                    android.app.AlertDialog.Builder(this).setTitle("Your viewing matches").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
                }
                else->universalSearch()
            }
        }.show()
    }
    private fun universalSearch() {
        val field=EditText(this).apply { hint="Movie, show or anime title"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this).setTitle("Universal search").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->
            val q=field.text.toString().trim().take(200)
            if(q.isBlank()) return@setPositiveButton
            val local=ViewingGroups.build(ViewingAnalytics.filter(history.records(0),HistoryFilter(query=q)))
            val favorites=personalStore("favorites").all.values.map { it.toString() }.filter { it.contains(q,true) }
            val collections=personalStore("collections").all.filter { (name,value) -> name.contains(q,true) || value.toString().contains(q,true) }.keys.sorted()
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            section(body,"Local library • "+local.size)
            local.take(20).forEach { titleCard(body,it,true) }
            if(local.isEmpty()) body.addView(txt("No matching viewing history.",14,muted))
            if(favorites.isNotEmpty()) { section(body,"Favorites"); favorites.take(20).forEach { body.addView(txt(it,14)) } }
            if(collections.isNotEmpty()) { section(body,"Matching collections"); collections.forEach { body.addView(txt(it,14)) }; body.addView(actionButton("Open collections") { this@MainActivity.collections() }) }
            for(type in listOf("movie","show")) body.addView(actionButton("Search Trakt "+type+"s") {
                lookup("Searching Trakt",{ done -> TraktCatalogue.search(q,type,done) }) { rows:List<CatalogueItem> ->
                    choose("Trakt "+type+"s",rows.map { it.label() }) { index -> titleDetails(type,rows[index].id,rows[index].title) }
                }
            })
            body.addView(actionButton("Search actors and directors") { peopleSearch(q) })
            body.addView(actionButton("Search AniList anime") {
                lookup("Searching AniList",{ done -> AnimeCatalogue.search(q,done) }) { rows:List<AnimeResult> ->
                    choose("Anime matches",rows.map { it.english.ifBlank { it.romaji } }) { index -> startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://anilist.co/anime/"+rows[index].id))) }
                }
            })
            android.app.AlertDialog.Builder(this).setTitle("Search: "+q).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }.show()
    }
    private fun manualLog() {
        val field=EditText(this).apply { hint="Movie or episode title"; setSingleLine(true) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)); addView(field); addView(txt("Choose the date and time you finished. No measured watch time will be added.",13,muted)) }
        android.app.AlertDialog.Builder(this).setTitle("Manual watch log").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Choose date") { _,_->
            val title=field.text.toString().trim()
            if(title.isBlank()) return@setPositiveButton
            val now=java.time.LocalDateTime.now()
            android.app.DatePickerDialog(this,{ _,y,month,day ->
                android.app.TimePickerDialog(this,{ _,hour,minute ->
                    val timestamp=java.time.LocalDateTime.of(y,month+1,day,hour,minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    if(timestamp>System.currentTimeMillis()) Toast.makeText(this,"Choose a past time",Toast.LENGTH_LONG).show()
                    else { val id=history.addManualLog("manual",title,timestamp); showTab(2); history.record(id)?.let { identify(it) } }
                },now.hour,now.minute,true).show()
            },now.year,now.monthValue-1,now.dayOfMonth).show()
        }.show()
    }
    private fun viewingJournal() {
        val rows=history.records(0).filter { it.status!="active" }
        val years=rows.map { ViewingAnalytics.day(it.ended).year }.distinct().sortedDescending()
        choose("Viewing year",years.map { it.toString() }) { index ->
            val year=years[index]
            val records=rows.filter { ViewingAnalytics.day(it.ended).year==year }
            val groups=ViewingGroups.build(records)
            val recap=ViewingAnalytics.recap(records,year)
            val total=recap.sampledMs
            val report="TraktBridge • "+year+" viewing recap\nSampled time: "+durationText(total)+"\nMovies synced: "+groups.count { it.confirmed && it.representative.matchType=="movie" }+"\nEpisodes synced: "+groups.count { it.confirmed && it.representative.matchType=="episode" }+"\nSessions: "+records.size+"\nActive viewing days: "+recap.activeDays+"\nLongest streak: "+recap.longestStreak+" days"+"\nBusiest month: "+(recap.busiestMonth?.let { java.time.Month.of(it).name } ?: "None")+"\nTop service: "+(recap.topSource?.let { appLabel(it) } ?: "None")
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            body.addView(txt(report,18,ink,true))
            body.addView(actionButton("Share recap") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Share viewing recap")) })
            section(body,"Monthly viewing time")
            val months=ViewingAnalytics.monthly(records,year)
            val peak=months.maxOf { it.sampledMs }.coerceAtLeast(1)
            months.forEach { month ->
                body.addView(actionButton(java.time.Month.of(month.month).name+" • "+durationText(month.sampledMs)+" • "+month.sessions+" sessions") {
                    calendarMonth=java.time.YearMonth.of(year,month.month); showTab(3)
                })
                body.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(month.sampledMs.toDouble()/peak*100).toInt() },LinearLayout.LayoutParams(-1,dp(10)))
            }
            section(body,"Recent journal entries")
            records.groupBy { ViewingAnalytics.day(it.ended) }.toSortedMap(reverseOrder()).entries.take(30).forEach { (day,items) ->
                section(body,day.toString())
                items.take(20).forEach { r -> body.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } }+" • "+appLabel(r.source)+" • "+durationText(r.watched),14,muted)) }
                if(items.size>20) body.addView(txt("More entries in the viewing calendar.",13,muted))
            }
            android.app.AlertDialog.Builder(this).setTitle("Viewing journal").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun nowPlaying() {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)) }
        val status=txt("",16,ink,true); body.addView(status)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; body.addView(content)
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Now Playing").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val tick=object:Runnable {
            override fun run() {
                if(!dialog.isShowing || isDestroyed || isFinishing) return
                val age=System.currentTimeMillis()-prefs.getLong("last_sample",0)
                val live=prefs.getBoolean("enabled",false) && prefs.getBoolean("listener_connected",false) && age in 0..20000
                status.text=if(live) "Live observations • updated "+(age/1000)+" seconds ago" else "No fresh playback observation"
                content.removeAllViews()
                val sessions=if(live) runCatching { JSONArray(prefs.getString("sessions","[]")) }.getOrDefault(JSONArray()) else JSONArray()
                if(sessions.length()==0) content.addView(txt("No selected player session is visible to Android.",14,muted))
                for(i in 0 until sessions.length()) {
                    val row=sessions.optJSONObject(i) ?: continue
                    val card=panel(content)
                    val title=row.optString("title").ifBlank { "Unidentified video" }
                    card.addView(txt(title,20,ink,true)); card.addView(txt(appLabel(row.optString("package"))+" • "+row.optString("output","unknown")+" output",14,muted)); card.addView(txt(row.optString("sessionLabel","Session unavailable"),12,muted))
                    val state=when(row.optInt("state")) { 3->"Playing"; 2->"Paused"; 6,8->"Buffering or connecting"; else->"Playback state unknown or stopped" }
                    card.addView(txt(state,15,violet,true))
                    val position=row.optLong("positionMs",-1); val duration=row.optLong("durationMs",0)
                    if(PlaybackPosition.valid(position,duration)) {
                        card.addView(txt(durationText(position)+" / "+durationText(duration)+" • "+row.optString("positionSource","unknown"),14,muted))
                        card.addView(ProgressBar(this@MainActivity,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(position.toDouble()/duration*100).toInt().coerceIn(0,100) })
                    } else card.addView(txt("Position unavailable",14,muted))
                    card.addView(txt("Recognition evidence score: "+row.optInt("recognitionScore")+"/100"+if(row.optBoolean("recognitionConflict")) " • conflicting evidence" else "",14,muted))
                }
                handler.postDelayed(this,2500)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }; dialog.show(); handler.post(tick)
    }
    private fun liveInspector() {
        val body=TextView(this).apply { setPadding(dp(16),dp(12),dp(16),dp(12)); setTextIsSelectable(true) }
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Live recognition inspector").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val tick=object:Runnable {
            override fun run() {
                if(!dialog.isShowing || isDestroyed || isFinishing) return
                val last=prefs.getLong("last_sample",0)
                val age=if(last>0) ((System.currentTimeMillis()-last)/1000).coerceAtLeast(0).toString()+" seconds" else "Never"
                body.text="Last sample: "+age+"\nListener connected: "+prefs.getBoolean("listener_connected",false)+"\n\nCurrent sessions:\n"+runCatching { JSONArray(prefs.getString("sessions","[]")).toString(2) }.getOrDefault("[]")
                handler.postDelayed(this,2000)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        dialog.show(); handler.post(tick)
    }
    private fun unifiedLibrary() {
        val anime=personalStore("anime_progress").all.values.mapNotNull { value ->
            runCatching { val o=org.json.JSONObject(value.toString()); LocalAnimeEntry(o.optInt("id"),o.optString("title"),o.optInt("progress"),o.optInt("episodes")) }.getOrNull()
        }
        val field=EditText(this).apply { hint="Search movies, series and anime"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this).setTitle("Unified library").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Browse") { _,_->
            val library=UnifiedLibrary.build(history.records(0),anime,field.text.toString())
            choose("Library categories",listOf("Movies ("+library.movies.size+")","TV / Trakt series ("+library.series.size+")","AniList local anime ("+library.anime.size+")","Unidentified ("+library.unidentified.size+")")) { category ->
                when(category) {
                    0,3 -> { val rows=if(category==0) library.movies else library.unidentified; choose("Viewing titles",rows.take(200).map { it.representative.matchLabel.ifBlank { it.representative.title.ifBlank { "Unidentified" } } }) { showSessions(rows[it]) } }
                    1 -> choose("Series",library.series.take(200).map { it.title }) { showSeries(library.series[it].id) }
                    2 -> choose("Local anime",library.anime.take(200).map { it.title+" • "+it.progress+"/"+(if(it.episodes>0) it.episodes.toString() else "?") }+listOf("Find anime")) { index ->
                        if(index==minOf(200,library.anime.size)) animeSearch() else library.anime[index].let { editAnimeProgress(it.id,it.title,it.episodes) }
                    }
                }
            }
        }.show()
    }
    private fun animeLocalProgress() {
        val store=personalStore("anime_progress")
        val rows=store.all.values.mapNotNull { runCatching { org.json.JSONObject(it.toString()) }.getOrNull() }.sortedBy { it.optString("title") }
        choose("Local anime progress",rows.map { it.optString("title")+" • "+it.optInt("progress")+" episodes" }+listOf("Find anime to track")) { index ->
            if(index==rows.size) animeSearch()
            else {
                val row=rows[index]
                editAnimeProgress(row.optInt("id"),row.optString("title"),row.optInt("episodes"))
            }
        }
    }
    private fun editAnimeProgress(id:Int,title:String,episodes:Int) {
        if(id<=0) return
        val store=personalStore("anime_progress")
        val old=runCatching { org.json.JSONObject(store.getString(id.toString(),"{}")) }.getOrDefault(org.json.JSONObject())
        val field=EditText(this).apply { hint="Episodes completed"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText(old.optInt("progress",0).toString()) }
        android.app.AlertDialog.Builder(this).setTitle(title).setMessage("Local personal progress only. No anime account synchronization.").setView(field).setNegativeButton("Cancel",null).setNeutralButton("Remove") { _,_->store.edit().remove(id.toString()).apply() }.setPositiveButton("Save") { _,_->
            val n=field.text.toString().toIntOrNull()
            if(n!=null && n in 0..(if(episodes>0) episodes else 10000)) store.edit().putString(id.toString(),org.json.JSONObject().put("id",id).put("title",title).put("episodes",episodes).put("progress",n).toString()).apply()
            else Toast.makeText(this,"Invalid episode count",Toast.LENGTH_LONG).show()
        }.show()
    }
    private fun publicAnimeList() {
        val field=EditText(this).apply { hint="AniList username"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this).setTitle("Public AniList library").setMessage("Read-only public list. Private accounts require authentication; no viewing history is uploaded.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Load") { _,_->
            val name=field.text.toString().trim()
            lookup("Loading public anime list",{ done -> AnimeCatalogue.publicList(name,done) }) { rows:List<AnimeListEntry> ->
                choose("AniList • "+name,rows.take(500).map { it.title+" • "+it.progress+"/"+(if(it.episodes>0) it.episodes.toString() else "?")+" • "+it.status }) { i -> startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://anilist.co/anime/"+rows[i].id))) }
            }
        }.show()
    }
    private fun animeSearch() {
        val field=EditText(this).apply { hint="English, Japanese or romaji title"; setSingleLine(true) }
        android.app.AlertDialog.Builder(this).setTitle("Search AniList").setMessage("Your search text is sent to AniList. No account access or history upload.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->
            val q=field.text.toString().trim()
            if(q.isBlank()) return@setPositiveButton
            lookup("Searching anime",{ done -> AnimeCatalogue.search(q,done) }) { rows:List<AnimeResult> ->
                choose("Anime matches",rows.map { it.english.ifBlank { it.romaji }+" • "+it.romaji }) { i ->
                    val a=rows[i]
                    android.app.AlertDialog.Builder(this).setTitle(a.english.ifBlank { a.romaji }).setMessage(a.romaji+"\n"+a.native+"\nEpisodes: "+if(a.episodes>0) a.episodes.toString()+"\n\n"+a.description else "Unknown\n\n"+a.description).setNegativeButton("Close",null).setNeutralButton("Track locally") { _,_->editAnimeProgress(a.id,a.english.ifBlank { a.romaji },a.episodes) }.setPositiveButton("AniList details") { _,_->startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://anilist.co/anime/"+a.id))) }.show()
                }
            }
        }.show()
    }
    private fun recommendFromHistory() {
        val records=history.records(0)
        val seeds=RecommendationEngine.seeds(records,System.currentTimeMillis())
        if(seeds.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("Recommendations").setMessage("Confirm some viewing history first, or browse trending titles.").setNegativeButton("Close",null).setPositiveButton("Discover") { _,_->discover() }.show()
            return
        }
        val hiddenStore=personalStore("recommendation_hidden")
        android.app.AlertDialog.Builder(this).setTitle("Personalized recommendations").setMessage("Trakt receives up to five catalogue title IDs to find related content. Viewing times and local feedback stay on this device.").setNegativeButton("Cancel",null).setNeutralButton("Reset hidden titles") { _,_->hiddenStore.edit().clear().apply(); recommendFromHistory() }.setPositiveButton("Find titles") { _,_->
            lookup("Finding personal recommendations",{ done -> TraktCatalogue.personalized(seeds,done) }) { batches:List<RelatedBatch> ->
                val rows=RecommendationEngine.rank(batches,RecommendationEngine.seen(records),hiddenStore.all.keys)
                val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                if(batches.size<seeds.size) body.addView(txt("Some recommendation sources were unavailable.",14,muted))
                if(rows.isEmpty()) body.addView(txt("No new recommendations. Try Discover or reset hidden titles.",15,muted))
                cards(body,rows) { parent,row ->
                    val item=row.item
                    val card=panel(parent)
                    card.addView(txt(item.label(),17,ink,true))
                    card.addView(txt("Because you watched "+row.reasons.take(3).joinToString(", "),14,muted))
                    card.addView(actionButton("Details") { titleDetails(item.type,item.id,item.title) })
                    card.addView(actionButton("Not interested") {
                        if(hiddenStore.all.size>=500) { Toast.makeText(this,"Hidden list full. Reset it to add more.",Toast.LENGTH_LONG).show() }
                        else { hiddenStore.edit().putBoolean(item.type+":"+item.id,true).apply(); card.visibility=android.view.View.GONE }
                    })
                }
                android.app.AlertDialog.Builder(this).setTitle("For you").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
            }
        }.show()
    }
    private fun discover() {
        choose("Discover",listOf("Trending movies","Trending shows","Popular movies","Popular shows","Mood-based discovery")) { index ->
            if(index==4) { moodDiscovery(); return@choose }
            val type=if(index%2==0) "movie" else "show"; val mode=if(index<2) "trending" else "popular"
            lookup("Loading "+mode,{ done -> TraktCatalogue.discover(type,mode,done) }) { rows:List<CatalogueItem> ->
                val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                cards(body,rows) { parent,item ->
                    val card=panel(parent); val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Poster" }
                    card.addView(image,LinearLayout.LayoutParams(-1,dp(180))); Artwork.load(this,type,item.id,image)
                    card.addView(txt(item.label(),16,ink,true)); card.addView(actionButton("Details") { titleDetails(type,item.id,item.title) })
                }
                android.app.AlertDialog.Builder(this).setTitle(mode.replaceFirstChar { it.uppercase() }).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
            }
        }
    }
    private fun moodDiscovery() {
        choose("Choose your mood",DiscoveryEngine.moods.map { it.label }) { moodIndex ->
            val mood=DiscoveryEngine.moods[moodIndex]
            choose("Content type",listOf("Movies","TV shows")) { typeIndex ->
                val type=if(typeIndex==0) "movie" else "show"
                val times=listOf(0,30,60,90,120)
                choose("Maximum runtime (per episode for shows)",listOf("Any runtime","30 minutes","60 minutes","90 minutes","120 minutes")) { timeIndex ->
                    choose("Minimum Trakt rating",listOf("Any rating","7 / 10","8 / 10")) { ratingIndex ->
                        val excluded=RecommendationEngine.seen(history.records(0))+personalStore("recommendation_hidden").all.keys
                        lookup("Finding "+mood.label.lowercase()+" titles",{ done -> TraktCatalogue.moodDiscovery(type,mood,done) }) { candidates:List<DiscoveryItem> ->
                            val rows=DiscoveryEngine.filter(candidates,mood,times[timeIndex],listOf(0.0,7.0,8.0)[ratingIndex],excluded)
                            choose(mood.label+" • new to you",rows.map { row -> row.item.label()+" • "+(if(row.item.runtimeMinutes>0) row.item.runtimeMinutes.toString()+" min" else "Runtime unknown") }) { i ->
                                val item=rows[i].item; titleDetails(item.type,item.id,item.title)
                            }
                        }
                    }
                }
            }
        }
    }
    private fun titleDetails(type:String,id:Long,label:String) {
        val cache=getSharedPreferences("offline_catalogue",MODE_PRIVATE)
        val key=type+":"+id
        lookup("Loading title details",{ done -> TraktCatalogue.details(type,id) { result ->
            val cached=runCatching { org.json.JSONObject(cache.getString(key,"")) }.getOrNull()?.takeIf { TvNavigation.validCache(it.optLong("savedAt"),System.currentTimeMillis()) }
            if(result.isSuccess) {
                val d=result.getOrThrow(); if(cache.all.size>=200 && !cache.contains(key)) cache.edit().clear().apply(); cache.edit().putString(key,org.json.JSONObject().put("savedAt",System.currentTimeMillis()).put("title",d.title).put("overview",d.overview).put("year",d.year).put("runtime",d.runtime).put("rating",d.rating).put("genres",d.genres).put("trailer",d.trailer).put("released",d.released).toString()).apply(); done(result)
            } else if(cached!=null) done(Result.success(TitleDetails(cached.optString("title"),cached.optString("overview"),cached.optInt("year"),cached.optInt("runtime"),cached.optDouble("rating",0.0),cached.optString("genres"),cached.optString("trailer"),cached.optString("released")))) else done(result)
        } }) { d:TitleDetails ->
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)) }
            val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Poster" }
            body.addView(image,LinearLayout.LayoutParams(-1,dp(240))); Artwork.load(this,type,id,image)
            body.addView(txt(d.title.ifBlank { label },24,ink,true))
            body.addView(txt(listOf(d.year.takeIf { it>0 }?.toString().orEmpty(),d.runtime.takeIf { it>0 }?.let { it.toString()+" min" }.orEmpty(),d.genres).filter { it.isNotBlank() }.joinToString(" • "),14,muted))
            if(d.rating>0) body.addView(txt("Trakt rating: "+String.format(java.util.Locale.US,"%.1f",d.rating)+"/10",16,violet))
            if(prefs.getBoolean("hide_spoilers",true)) body.addView(actionButton("Reveal synopsis") { android.app.AlertDialog.Builder(this).setTitle(d.title).setMessage(d.overview.ifBlank { "No overview available" }).setPositiveButton("Close",null).show() }) else body.addView(txt(d.overview.ifBlank { "No overview available" },16))
            if(d.released.isNotBlank()) body.addView(txt("Release: "+d.released,13,muted))
            body.addView(actionButton("Cast and directors") { lookup("Loading credits",{ done -> TraktCatalogue.credits(type,id,done) }) { names:List<String> -> android.app.AlertDialog.Builder(this).setTitle("Cast and crew").setMessage(names.joinToString("\n").ifBlank { "No credits returned" }).setPositiveButton("Close",null).show() } })
            body.addView(actionButton("Where to watch • regional availability") { streamingAvailability(type,id,d.title.ifBlank { label }) })
            body.addView(actionButton("Watchlist / rating") { lookup("Checking account",{ done -> TraktLibrary.accountId(this,done) }) { account:String -> remoteActions(RemoteItem(type,id,d.title),account) } })
            if(d.trailer.startsWith("https://")) body.addView(actionButton("Open trailer") { runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(d.trailer))) } })
            android.app.AlertDialog.Builder(this).setTitle("Title details").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun heroCard(parent:LinearLayout,g:ViewingTitle) {
        val r=g.representative
        val card=panel(parent)
        val image=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xff343448.toInt()); contentDescription="Artwork for "+r.matchLabel.ifBlank { r.title } }
        card.addView(image,LinearLayout.LayoutParams(-1,dp(if(wide) 280 else 190)))
        if(r.traktId>0) Artwork.load(this,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,image)
        card.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },24,ink,true))
        card.addView(txt(g.platforms.joinToString(" • ") { appLabel(it) },14,muted))
        if(g.durationMs>0 && g.positionMs>=0) {
            card.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(g.positionMs.toDouble()/g.durationMs*100).toInt().coerceIn(0,100); progressTintList=android.content.res.ColorStateList.valueOf(violet) })
            card.addView(txt(durationText(g.remainingMs)+" remaining",14,muted))
        }
        if(r.traktId>0) card.addView(actionButton("Title details") { titleDetails(if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,r.matchLabel) })
        card.addView(actionButton("Open playback app") { openPlaybackApp(g.platforms) })
        card.addView(actionButton("View title and sessions") { showSessions(g) })
    }
    private fun <T> cards(parent:LinearLayout,items:List<T>,render:(LinearLayout,T)->Unit) {
        if(ComponentPolicy.columns(resources.configuration.screenWidthDp,resources.configuration.fontScale,tvMode)==1) { items.forEach { render(parent,it) }; return }
        items.chunked(2).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.TOP }
            parent.addView(row)
            pair.forEach { item ->
                val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                row.addView(column,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
                render(column,item)
            }
            if(pair.size==1) row.addView(android.view.View(this),LinearLayout.LayoutParams(0,1,1f))
        }
    }
    private fun metricGrid(parent:LinearLayout,metrics:List<Pair<String,String>>) {
        metrics.chunked(if(resources.configuration.fontScale>=1.5f) 1 else if(wide) 4 else 2).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            parent.addView(row)
            for((label,value) in pair) {
                val tile=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; background=rounded(surface,14); setPadding(dp(12),dp(14),dp(12),dp(14)); addView(txt(label,12,muted)); addView(txt(value,21,violet,true)) }
                row.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8); bottomMargin=dp(8) })
            }
        }
    }
    private fun editHistoryFilter(records:List<WatchRecord>) {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)) }
        val q=EditText(this).apply { hint="Search movie, show or episode"; setSingleLine(true); setText(historyFilter.query) }
        box.addView(q)
        fun spinner(label:String,values:List<String>,selected:Int):Spinner {
            box.addView(txt(label,13,muted))
            return Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,values); setSelection(selected.coerceAtLeast(0)); box.addView(this,LinearLayout.LayoutParams(-1,dp(48))) }
        }
        val sources=listOf("")+records.map { it.source }.distinct().sortedBy { appLabel(it) }
        val source=spinner("Platform",sources.map { if(it.isBlank()) "All platforms" else appLabel(it) },sources.indexOf(historyFilter.source))
        val periods=listOf(0,7,30,90)
        val period=spinner("Date range",listOf("All time","Last 7 days","Last 30 days","Last 90 days"),periods.indexOf(historyFilter.period))
        val types=listOf("","movie","episode","unknown")
        val type=spinner("Content",listOf("All content","Movies","Series / anime episodes","Unidentified"),types.indexOf(historyFilter.type))
        val statuses=listOf("","completed","unfinished","queued")
        val status=spinner("Status",listOf("All statuses","Synced","Unfinished","Approved / queued"),statuses.indexOf(historyFilter.status))
        val sorts=listOf("recent","title","watchtime")
        val sort=spinner("Sort",listOf("Most recent","Title A–Z","Most sampled watch time"),sorts.indexOf(historyFilter.sort))
        android.app.AlertDialog.Builder(this).setTitle("History filters").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel",null).setPositiveButton("Apply") { _,_->
            historyFilter=HistoryFilter(q.text.toString(),sources[source.selectedItemPosition],periods[period.selectedItemPosition],types[type.selectedItemPosition],statuses[status.selectedItemPosition],sorts[sort.selectedItemPosition]); historyLimit=100; refreshHistory()
        }.show()
    }
    private fun calendarView(parent:LinearLayout,records:List<WatchRecord>) {
        val card=panel(parent)
        val month=calendarMonth
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        card.addView(header)
        header.addView(Button(this).apply { text="‹"; contentDescription="Previous month"; setOnClickListener { calendarMonth=month.minusMonths(1); refreshHistory() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(txt(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM uuuu")),18,ink,true).apply { gravity=android.view.Gravity.CENTER },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply { text="›"; contentDescription="Next month"; setOnClickListener { calendarMonth=month.plusMonths(1); refreshHistory() } },LinearLayout.LayoutParams(dp(48),dp(48)))
        val days=ViewingAnalytics.calendar(records,month)
        val week=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        card.addView(week)
        listOf("M","T","W","T","F","S","S").forEach { week.addView(txt(it,12,muted).apply { gravity=android.view.Gravity.CENTER },LinearLayout.LayoutParams(0,dp(28),1f)) }
        val blanks=month.atDay(1).dayOfWeek.value-1
        val cells=List<ActivityDay?>(blanks) { null }+days
        cells.chunked(7).forEach { line ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; card.addView(row)
            for(index in 0..6) {
                val d=line.getOrNull(index)
                val cell=TextView(this).apply {
                    gravity=android.view.Gravity.CENTER; textSize=14f; setTextColor(ink); minHeight=dp(44)
                    if(d!=null) {
                        text=d.date.dayOfMonth.toString()+if(d.sessions>0) "\n•" else ""
                        background=rounded(if(d.sampledMs>0) 0xff40336a.toInt() else if(d.sessions>0) 0xff292c40.toInt() else surface,8)
                        contentDescription=d.date.toString()+", "+durationText(d.sampledMs)+" sampled, "+d.sessions+" sessions"
                        setOnClickListener { showCalendarDay(d,records) }
                    }
                }
                row.addView(cell,LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
            }
        }
        card.addView(txt("Dots indicate local records; purple indicates sampled playback. Sessions spanning midnight are attributed to their end date.",12,muted))
    }
    private fun showCalendarDay(day:ActivityDay,records:List<WatchRecord>) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt(durationText(day.sampledMs)+" sampled • "+day.sessions+" sessions",16,ink,true))
        ViewingGroups.build(records.filter { ViewingAnalytics.day(it.ended)==day.date }).forEach { titleCard(body,it,true) }
        android.app.AlertDialog.Builder(this).setTitle(day.date.toString()).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun seriesCard(parent:LinearLayout,series:SeriesGroup) {
        val card=panel(parent)
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }; card.addView(row)
        val poster=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); contentDescription="Poster for "+series.title }
        row.addView(poster,LinearLayout.LayoutParams(dp(94),dp(141)).apply { rightMargin=dp(14) })
        Artwork.load(this,"show",series.id,poster)
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(txt(series.title,20,ink,true))
        details.addView(txt(series.completed.toString()+" synced • "+series.approved+" awaiting sync • "+series.episodes.size+" identified episodes",13,muted))
        details.addView(txt(series.platforms.joinToString(" • ") { appLabel(it) },13,muted))
        details.addView(txt("Sampled: "+durationText(series.sampledMs),13,muted))
        series.resume?.let { details.addView(txt("Continue: S"+it.representative.season+" E"+it.representative.episode,14,violet,true)) }
        card.addView(actionButton("Personal status: "+libraryStatus("show",series.id).ifBlank { "Not set" }) { setLibraryStatus("show",series.id,series.title) })
        card.addView(actionButton("Series details") { titleDetails("show",series.id,series.title) })
        card.addView(actionButton(if(isFavorite("show",series.id)) "Remove favorite" else "Add favorite") { toggleFavorite("show",series.id,series.title); refreshHistory() })
        card.addView(actionButton("Browse seasons and episodes") { showSeries(series.id) })
    }
    private fun showSeries(id:Long) {
        val series=SeriesLibrary.build(ViewingGroups.build(history.records(0))).firstOrNull { it.id==id } ?: return
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt(series.completed.toString()+" episodes synced to Trakt. Local counts include only identified episodes, not the full catalogue.",14,muted))
        var dialog:android.app.AlertDialog?=null
        val show=CatalogueItem("show",series.id,series.title)
        body.addView(actionButton("Check account series progress") { dialog?.dismiss(); remoteSeriesProgress(series.id,series.title) })
        body.addView(actionButton("Load all catalogue seasons") {
            dialog?.dismiss()
            lookup("Loading catalogue seasons",{ done -> TraktCatalogue.seasons(show,done) }) { seasons:List<CatalogueSeason> ->
                choose(series.title,seasons.map { if(it.number==0) "Specials" else "Season "+it.number }) { index -> showCatalogueSeason(series.id,seasons[index].number) }
            }
        })
        for(season in series.seasons) {
            val label=if(season.number==0) "Specials" else "Season "+season.number
            section(body,label)
            body.addView(txt(season.completed.toString()+" synced / "+season.episodes.size+" locally identified",13,muted))
            body.addView(actionButton("View full "+label.lowercase()) { dialog?.dismiss(); showCatalogueSeason(series.id,season.number) })
            for(episode in season.episodes) titleCard(body,episode,true)
        }
        dialog=android.app.AlertDialog.Builder(this).setTitle(series.title).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close") { _,_->refreshHistory() }.create()
        dialog.show()
    }
    private fun showCatalogueSeason(id:Long,season:Int) {
        val series=SeriesLibrary.build(ViewingGroups.build(history.records(0))).firstOrNull { it.id==id } ?: return
        val show=CatalogueItem("show",series.id,series.title)
        lookup("Loading episodes",{ done -> TraktCatalogue.episodes(show,season,done) }) { catalogue:List<CatalogueItem> ->
            val rows=SeriesLibrary.seasonRows(series,season,catalogue)
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            val completed=rows.count { it.local?.confirmed==true }
            val missing=FranchiseTimeline.missing(id,season,catalogue,history.records(0))
            body.addView(actionButton("Missing local episode records: "+missing.size) { choose("No local record • may include unaired episodes",missing.map { it.label() }) { j -> titleDetails("show",id,missing[j].title.substringBefore(" — ")) } })
            body.addView(txt(completed.toString()+" of "+rows.size+" catalogue episodes synced to Trakt",17,ink,true))
            if(rows.isNotEmpty()) body.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=completed*100/rows.size; progressTintList=android.content.res.ColorStateList.valueOf(violet) })
            SeriesLibrary.nextEpisode(rows)?.let { body.addView(txt("Next unconfirmed: S"+it.item.season+" E"+it.item.episode,14,violet,true)) }
            body.addView(txt("Catalogue may include unaired episodes. Missing local history is not proof you have never watched an episode.",12,muted))
            for(row in rows) {
                if(row.local!=null) titleCard(body,row.local,true) else {
                    val card=panel(body)
                    card.addView(txt("E"+row.item.episode+" • "+row.item.title.substringAfter(" — "),16,ink,true))
                    card.addView(txt("No local viewing record",13,muted))
                }
            }
            android.app.AlertDialog.Builder(this).setTitle(series.title+" • "+if(season==0) "Specials" else "Season "+season).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Back") { _,_->showSeries(id) }.setPositiveButton("Close") { _,_->refreshHistory() }.show()
        }
    }
    private fun configureAccountRefresh() {
        val enabled=prefs.getString("auto_refresh_account","").orEmpty().isNotBlank()
        android.app.AlertDialog.Builder(this).setTitle("Daily Trakt history refresh").setMessage("Download the latest 100 remote watches approximately daily while connected. No upload, no sampled watch time added. Currently: "+if(enabled) "enabled" else "disabled").setNegativeButton("Close",null).setNeutralButton("Disable") { _,_->RemoteRefreshSchedule.disable(this); Toast.makeText(this,"Automatic refresh disabled",Toast.LENGTH_SHORT).show() }.setPositiveButton("Enable for connected account") { _,_->
            lookup("Verifying account",{ done -> TraktLibrary.accountId(this,done) }) { id:String ->
                RemoteRefreshSchedule.enable(this,id); Toast.makeText(this,"Daily refresh enabled",Toast.LENGTH_LONG).show()
            }
        }.show()
    }
    private fun openTraktLibrary() {
        val labels=listOf("Account watch history","Watchlist","Ratings","Saved playback progress","Downloaded history (local cache)")
        choose("Trakt account library",labels) { index ->
            if(index==4) lookup("Checking account",{ done -> TraktLibrary.accountId(this,done) }) { account:String ->
                val rows=TraktLibrary.cachedHistory(this,account)
                if(rows.isEmpty()) Toast.makeText(this,"No history downloaded for this account",Toast.LENGTH_LONG).show()
                else showRemotePage("cached",RemotePage(rows.take(100),1,1,account))
            } else loadRemotePage(listOf("history","watchlist","ratings","playback")[index],1)
        }
    }
    private fun loadRemotePage(kind:String,page:Int) {
        lookup("Loading Trakt "+kind,{ done -> TraktLibrary.page(this,kind,page,done) }) { result:RemotePage -> showRemotePage(kind,result) }
    }
    private fun showRemotePage(kind:String,page:RemotePage) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt("Trakt "+kind+" • Page "+page.page+(page.pages?.let { " of "+it } ?: ""),16,ink,true))
        body.addView(txt("Account history is separate from measured local playback.",13,muted))
        var dialog:android.app.AlertDialog?=null
        if(kind=="history") body.addView(actionButton("Download this page for local viewing") {
            val n=runCatching { TraktLibrary.cacheHistory(this,page) }.getOrDefault(-1)
            Toast.makeText(this,if(n>=0) "Saved "+n+" history entries (duplicates replaced)" else "Download failed",Toast.LENGTH_LONG).show()
        })
        if(page.items.isEmpty()) body.addView(txt("No entries on this page.",15,muted))
        val local=ViewingGroups.build(history.records(0)).associateBy { it.key }
        for(item in page.items) {
            val card=panel(body)
            card.addView(txt(item.title,17,ink,true))
            if(item.watchedAt.isNotBlank()) card.addView(txt("Watched on Trakt: "+item.watchedAt,12,muted))
            if(item.rating>0) card.addView(txt("Your rating: "+item.rating+"/10",13,violet))
            if(item.progress in 0.0..100.0) card.addView(txt("Trakt saved progress: "+item.progress.toInt()+"%",13,violet))
            local[item.type+":"+item.id]?.let { g -> card.addView(actionButton("View matching local sessions") { showSessions(g) }) }
            card.addView(actionButton("Watchlist / rating actions") { remoteActions(item,page.account) })
            val showId=if(item.type=="show") item.id else item.showId
            if(showId>0) card.addView(actionButton("Series progress") { remoteSeriesProgress(showId,item.title.substringBefore(" — ")) })
        }
        if(kind!="cached") {
            if(page.page>1) body.addView(actionButton("Previous page") { dialog?.dismiss(); loadRemotePage(kind,page.page-1) })
            if(page.items.isNotEmpty() && (page.pages==null || page.page<page.pages)) body.addView(actionButton("Next page") { dialog?.dismiss(); loadRemotePage(kind,page.page+1) })
        } else body.addView(txt("Showing the latest 100 cached entries.",12,muted))
        dialog=android.app.AlertDialog.Builder(this).setTitle("Trakt account library").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
        dialog.show()
    }
    private fun remoteSeriesProgress(showId:Long,title:String) {
        lookup("Loading account progress",{ done -> TraktLibrary.progress(this,showId,done) }) { result:RemoteProgress ->
            android.app.AlertDialog.Builder(this).setTitle(title).setMessage(result.completed.toString()+" of "+result.aired+" aired episodes completed on Trakt"+if(result.next.isBlank()) "" else "\nNext episode: "+result.next).setPositiveButton("OK",null).show()
        }
    }
    private fun remoteActions(item:RemoteItem,account:String) {
        choose(item.title,listOf("Add to Trakt watchlist","Remove from Trakt watchlist","Rate on Trakt","Remove Trakt rating")) { index ->
            fun confirm(action:String,rating:Int=0) {
                android.app.AlertDialog.Builder(this).setTitle("Confirm Trakt account change").setMessage(item.title+"\n"+action+if(rating>0) " "+rating+"/10" else "").setNegativeButton("Cancel",null).setPositiveButton("Confirm") { _,_->
                    lookup("Updating Trakt",{ done -> TraktLibrary.mutate(this,account,item,action,rating,done) }) { message:String -> Toast.makeText(this,message,Toast.LENGTH_LONG).show() }
                }.show()
            }
            when(index) {
                0->confirm("watchlist-add")
                1->confirm("watchlist-remove")
                2->choose("Choose rating",(1..10).map { it.toString()+"/10" }) { confirm("rate",it+1) }
                3->confirm("unrate")
            }
        }
    }
    private fun durationText(ms:Long):String {
        if(ms<0) return "Unknown"
        val min=ms/60000
        return if(min>=60) (min/60).toString()+"h "+(min%60)+"m" else if(min==0L && ms>0) (ms/1000).toString()+"s" else min.toString()+" min"
    }
    private fun titleCard(parent:LinearLayout,g:ViewingTitle,expanded:Boolean) {
        val r=g.representative
        val card=panel(parent)
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        card.addView(row)
        val poster=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0xff343448.toInt()); contentDescription="Poster for "+r.matchLabel.ifBlank { r.title } }
        row.addView(poster,LinearLayout.LayoutParams(dp(94),dp(141)).apply { rightMargin=dp(14) })
        if(r.traktId>0) Artwork.load(this,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,poster)
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },18,ink,true))
        details.addView(txt(g.platforms.joinToString(" • ") { appLabel(it) },13,muted))
        details.addView(txt(g.sessions.size.toString()+" viewing session(s) • "+java.text.DateFormat.getDateInstance().format(java.util.Date(g.latest.ended)),13,muted))
        val assessment=g.positionSession?.let { completionAssessment(it) }
        val completion=when { g.sessions.all { it.status=="restored_audit" } -> "Restored history • prior upload not repeated"; g.confirmed -> "Finished • synced to Trakt"; g.queued -> "Completion approved • sync pending"; g.active -> "Currently watching"; assessment?.suggested==true -> "Completion suggested • confirm manually"; g.positionMs>=0 -> "In progress"; else -> "Completion unknown" }
        details.addView(txt(completion,14,if(g.confirmed) 0xff6ee7b7.toInt() else 0xfffbbf24.toInt(),true))
        details.addView(txt("Total sampled watch time: "+if(g.sampledMs>0) durationText(g.sampledMs) else "Unknown",13,muted))
        if(g.durationMs>0 && g.positionMs>=0) {
            details.addView(txt(durationText(g.positionMs)+" / "+durationText(g.durationMs)+" • "+durationText(g.remainingMs)+" left",13,ink))
            details.addView(txt("Last observed position • "+when(g.positionSource) { "screen"->"visible player timer estimate"; "server"->"server reported"; "media"->"media session reported"; else->"source unverified" },12,muted))
            details.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(g.positionMs.toDouble()/g.durationMs*100).toInt().coerceIn(0,100); progressTintList=android.content.res.ColorStateList.valueOf(violet) })
        } else { if(g.catalogueRuntimeMs>0) details.addView(txt("Catalogue runtime: "+durationText(g.catalogueRuntimeMs),13,muted)); details.addView(txt("Remaining time unavailable • player position unknown",13,muted)) }
        if(g.bufferingMs>0 || g.pausedMs>0 || g.seeks>0 || g.uncertainMs>0) details.addView(txt("Paused: "+durationText(g.pausedMs)+" • Buffering: "+durationText(g.bufferingMs)+" • Seeks: "+g.seeks+" • Uncertain: "+durationText(g.uncertainMs),12,muted))
        if(r.traktId<=0) details.addView(txt("Identify exact title to combine sessions.",12,muted))
        if(r.traktId>0) card.addView(actionButton("Title details") { titleDetails(if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,r.matchLabel) })
        if(!g.confirmed) card.addView(actionButton("Open playback app") { openPlaybackApp(g.platforms) })
        if(r.traktId>0) card.addView(actionButton("Review possible duplicate titles") { duplicateTitles(r) })
        if(r.traktId>0) card.addView(actionButton("More title actions") {
            choose("Manage title",listOf("Personal status: "+libraryStatus(r.matchType,r.traktId).ifBlank { "Not set" },if(isFavorite(r.matchType,r.traktId)) "Remove favorite" else "Add favorite","Notes and tags","Add to collection")) { action ->
                when(action) { 0->setLibraryStatus(r.matchType,r.traktId,r.matchLabel); 1->{ toggleFavorite(r.matchType,r.traktId,r.matchLabel); refreshHistory() }; 2->editTitleNotes(r.matchType,r.traktId,r.matchLabel); 3->addCollection(r.matchType,r.traktId,r.matchLabel) }
            }
        })
        if(expanded || g.pending || g.queued) card.addView(actionButton(if(g.confirmed) "View sessions" else "Review / view sessions") { showSessions(g) })
        else card.addView(actionButton("View sessions") { showSessions(g) })
    }
    private fun duplicateTitles(target:WatchRecord) {
        val rows=LibraryDuplicates.candidates(history.records(0),target)
        choose("Possible duplicate titles",rows.map { it.title+" • "+appLabel(it.source)+" • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)) }) { i ->
            android.app.AlertDialog.Builder(this).setTitle("Link to same catalogue title?").setMessage(rows[i].title+" → "+target.matchLabel+"\nVerify the exact title and episode. Original sessions and sampled time remain unchanged. Nothing is approved or uploaded.").setNegativeButton("Cancel",null).setPositiveButton("Link title") { _,_->
                if(history.linkDuplicateTitle(target.id,rows[i].id)) { ViewingNotifications.cancel(this,rows[i].id); refreshHistory() } else Toast.makeText(this,"Record changed; review again",Toast.LENGTH_LONG).show()
            }.show()
        }
    }
    private fun showSessions(g:ViewingTitle) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt("Original viewing sessions are retained. Synced viewing events: "+g.sessions.count { it.status=="synced" },14,muted))
        g.sessions.forEach { session ->
            if(session.id in g.possibleRewatchIds) body.addView(txt("Possible rewatch — review before approving",14,muted))
            recordCard(body,session,true,g.confirmed || g.queued)
        }
        android.app.AlertDialog.Builder(this).setTitle(g.representative.matchLabel.ifBlank { g.representative.title.ifBlank { "Viewing sessions" } }).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close") { _,_->refreshHistory() }.show()
    }
    private fun recordCard(parent:LinearLayout,r:WatchRecord,expanded:Boolean,completionAlreadyRecorded:Boolean=false) {
        val card=panel(parent)
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.TOP }
        card.addView(row)
        val poster=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0xff343448.toInt()); contentDescription="Poster for "+r.matchLabel.ifBlank { r.title } }
        row.addView(poster,LinearLayout.LayoutParams(dp(94),dp(141)).apply { rightMargin=dp(14) })
        if(r.traktId>0) Artwork.load(this,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,poster)
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },18,ink,true))
        if(r.subtitle.isNotBlank()) details.addView(txt(r.subtitle,13,muted))
        details.addView(txt(appLabel(r.source)+" • "+java.text.DateFormat.getDateInstance().format(java.util.Date(r.started)),13,muted))
        val status=when(r.status) { "synced"->"Synced to Trakt"; "active"->"Watching"; "pending"->"Needs review"; "reviewed"->"Ready to confirm"; "restored_audit"->"Restored audit • no upload"; else->r.status.replaceFirstChar { it.uppercase() } }
        details.addView(txt(status,13,if(r.status=="synced") 0xff6ee7b7.toInt() else 0xfffbbf24.toInt(),true))
        details.addView(txt(if(r.watched<=0) "Watch time unknown" else durationText(r.watched)+" sampled",13,muted))
        if(r.status in setOf("pending","reviewed")) { val assessment=completionAssessment(r); details.addView(txt("Completion evidence: "+(assessment.percent?.let { it.toString()+"% • " } ?: "")+assessment.reason,12,muted)) }
        if(r.seeks>0 || r.bufferingMs>0 || r.pausedMs>0 || r.uncertainMs>0) details.addView(txt("Paused: "+durationText(r.pausedMs)+" • Buffering: "+durationText(r.bufferingMs)+" • Seeks: "+r.seeks+" (rewinds "+r.rewinds+") • Uncertain: "+durationText(r.uncertainMs),12,muted))
        if(r.duration>0 && r.position>=0) details.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(r.position.toDouble()/r.duration*100).toInt().coerceIn(0,100); progressTintList=android.content.res.ColorStateList.valueOf(violet) })
        if(!expanded) { card.addView(actionButton("View details") { android.app.AlertDialog.Builder(this).setTitle("Viewing details").setMessage(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } }+"\n"+r.subtitle).setNegativeButton("Close",null).setPositiveButton("Open history") { _,_->showTab(1) }.show() }); return }
            if (r.traktId > 0) card.addView(TextView(this).apply { text = "Matched: " + r.matchLabel + " • Trakt ID " + r.traktId })
            if(r.traktId>0) card.addView(actionButton("Trakt watchlist / rating") { lookup("Checking account",{ done -> TraktLibrary.accountId(this,done) }) { account:String -> remoteActions(RemoteItem(r.matchType,r.traktId,r.matchLabel,r.showId,r.season,r.episode),account) } })
            if(r.id.startsWith("import:") && r.status=="pending") card.addView(actionButton("Reconcile with local history") { reconcileImported(r) })
            if (r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = if (r.traktId > 0) "Change Trakt match" else "Identify on Trakt"; setOnClickListener { identify(r) } })
            if (r.traktId > 0 && r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = "Remove Trakt match"; setOnClickListener { history.clearMatch(r.id); refreshHistory() } })
            if (r.status == "reviewed" && r.traktId > 0) card.addView(Button(this).apply { text = if(completionAlreadyRecorded) "Confirm separate rewatch" else "Mark watched on Trakt"; setOnClickListener { approveUpload(r,completionAlreadyRecorded) } })
            if (r.status in setOf("queued","uncertain","blocked","synced")) card.addView(TextView(this).apply { text = "Sync: " + r.status + " — " + history.syncMessage(r.id) })
            if (r.status == "blocked") card.addView(Button(this).apply { text = "Retry blocked upload"; setOnClickListener { history.retryBlocked(r.id); HistorySync.schedule(this@MainActivity); refreshHistory() } })
            if (r.status in setOf("queued","blocked")) card.addView(Button(this).apply { text = "Cancel queued upload"; setOnClickListener { history.cancelQueued(r.id); refreshHistory() } })
            if (r.status == "uncertain") card.addView(Button(this).apply { text = "Reconcile with Trakt (no resend)"; setOnClickListener { HistorySync.schedule(this@MainActivity); refreshHistory() } })
            if (r.status == "pending") card.addView(Button(this).apply { text = "Review / edit title"; setOnClickListener {
                val field = EditText(this@MainActivity).apply { setText(r.title); hint = "Movie or episode title" }
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("Confirm locally (not synced)").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
                    val title = field.text.toString().trim(); if (title.isNotEmpty()) { history.review(r.id,title); ViewingNotifications.cancel(this@MainActivity,r.id); refreshHistory() }
                }.show()
            } })
            if (r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = "Delete viewing record"; setOnClickListener { android.app.AlertDialog.Builder(this@MainActivity).setTitle("Delete this local record?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> history.trash(r.id); ViewingNotifications.cancel(this@MainActivity,r.id); refreshHistory() }.show() } })

    }
    private fun appLabel(pkg: String): String = mapOf("server.plex" to "Plex server","server.jellyfin" to "Jellyfin server","server.emby" to "Emby server","server.kodi" to "Kodi server")[pkg] ?: apps.entries.firstOrNull { it.value == pkg }?.key ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString()
    }.getOrDefault(pkg)
    private fun chooseInstalledApps() {
        val selected=(prefs.getStringSet("allowed",emptySet()) ?: emptySet()).toMutableSet()
        val launch=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages=(packageManager.queryIntentActivities(launch,0).map { it.activityInfo.packageName } + selected + apps.values)
            .filter { it != packageName }.distinct().sortedBy { appLabel(it).lowercase() }
        val checked=packages.map { it in selected }.toBooleanArray()
        android.app.AlertDialog.Builder(this).setTitle("Select only video apps you want tracked")
            .setMultiChoiceItems(packages.map { appLabel(it)+" ("+it+")" }.toTypedArray(),checked) { _, index, enabled ->
                if(enabled) selected.add(packages[index]) else selected.remove(packages[index])
            }.setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
                prefs.edit().putStringSet("allowed",selected).apply(); recreate()
            }.show()
    }
    private fun reviewUsage() {
        if(!prefs.contains("usage_since") || !UsageFallback.allowed(this)) {
            android.app.AlertDialog.Builder(this).setMessage("Enable app-activity fallback and grant Usage access first. Only selected apps are queried, and app activity is not proof of video playback.").setPositiveButton("OK",null).show(); return
        }
        val selected=prefs.getStringSet("allowed",emptySet()) ?: emptySet()
        val items=runCatching { UsageFallback.recent(this,selected,prefs.getLong("usage_since",System.currentTimeMillis())) }.getOrDefault(emptyList())
            .filterNot { history.overlapsActivity(it.source,it.start,it.end) }
        choose("Recent app activity — not watch time",items.map { appLabel(it.source)+" • "+((it.end-it.start)/60000)+" min in app • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.start)) }) { i ->
            val item=items[i]
            val suggestion=TitleAccessibility.hint(this,item.source,item.start,item.end)
            val field=EditText(this).apply { hint="What did you watch?"; setSingleLine(true); setText(suggestion?.first.orEmpty()) }
            val dialog=android.app.AlertDialog.Builder(this).setTitle("Confirm you watched a video").setMessage("App activity may include browsing or idle time. Confirm or correct any suggested title only if you watched something. Actual watch time is unknown.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Add locally",null).create()
            dialog.setOnShowListener { dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title=field.text.toString().trim()
                if(title.isBlank()) { field.error="Enter title"; return@setOnClickListener }
                val id=history.addManualActivity(item.source,title,item.start,item.end)
                dialog.dismiss(); refreshHistory(); history.record(id)?.let { identify(it) }
            } }
            dialog.show()
        }
    }
    private fun configureCompletionThreshold() {
        val options=listOf(75,80,85,90,95,100)
        val current=CompletionPolicy.threshold(prefs.getInt("completion_threshold",CompletionPolicy.DEFAULT_THRESHOLD))
        android.app.AlertDialog.Builder(this).setTitle("Completion review threshold").setMessage("Current: "+current+"%. A suggestion never uploads a completed watch. You must still approve it.").setNeutralButton("Change") { _,_->
            choose("Suggest completion at",options.map { it.toString()+"%" }) { i -> prefs.edit().putInt("completion_threshold",options[i]).apply(); refreshHistory() }
        }.setPositiveButton("Close",null).show()
    }
    private fun completionAssessment(r:WatchRecord)=CompletionPolicy.assess(r,prefs.getInt("completion_threshold",CompletionPolicy.DEFAULT_THRESHOLD))
    private fun approveUpload(r: WatchRecord,rewatch:Boolean=false) {
        android.app.AlertDialog.Builder(this).setTitle(if(rewatch) "Confirm a separate completed rewatch?" else "Mark as fully watched?")
            .setMessage(r.matchLabel + "\nThis adds a completed watch to Trakt at the recorded end time. Only confirm if you finished the movie or episode. Partial viewing stays local. For app-activity records the end time is only the app exit time.")
            .setNegativeButton("Cancel",null).setPositiveButton("Verify account") { _,_ ->
                Thread {
                    val result=runCatching { HistorySync.accountForApproval(applicationContext) }
                    runOnUiThread {
                        if(!isFinishing && !isDestroyed) result.fold({ accountId ->
                            android.app.AlertDialog.Builder(this).setTitle("Approve upload to connected Trakt account?")
                                .setMessage(r.matchLabel + "\nAccount ID: " + accountId + "\nApproved records will retry when online.")
                                .setNegativeButton("Cancel",null).setPositiveButton("Approve and sync") { _,_ ->
                                    if(history.enqueue(r.id,accountId,rewatch,r.traktId,r.matchType,r.ended)) HistorySync.schedule(this) else Toast.makeText(this,"This title already has a completion or pending upload for this account.",Toast.LENGTH_LONG).show()
                                    refreshHistory()
                                }.show()
                        },{ android.app.AlertDialog.Builder(this).setTitle("Account verification required").setMessage("Connect to Trakt and verify the account online before approving uploads.").setPositiveButton("OK",null).show() })
                    }
                }.start()
            }.show()
    }
    private fun identify(r: WatchRecord) {
        val field = EditText(this).apply { setText(RecognitionEngine.query(r.title)); hint = "Movie, show or anime title"; setSingleLine(true) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,12,24,12); addView(field); addView(TextView(this@MainActivity).apply { text = "Search sends this title to Trakt. Select the exact movie or episode; nothing is uploaded to your history." }) }
        box.addView(actionButton("Search by IMDb / TMDB ID") { externalIdentity(r) })
        val learned=history.learned(r)
        if(learned!=null && r.traktId==0L) box.addView(actionButton("Use learned match: "+learned.label()) { confirmMatch(r,learned) })
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Identify viewing record").setView(box).setNegativeButton("Cancel",null).setNeutralButton("TV / Anime",null).setPositiveButton("Movie",null).create()
        dialog.setOnShowListener {
            fun launch(type: String) {
                val q = field.text.toString().trim()
                if (q.isBlank()) { field.error = "Enter a title"; return }
                dialog.dismiss()
                lookup("Searching Trakt", { done -> TraktCatalogue.search(q,type,done) }) { results: List<CatalogueItem> ->
                    val ranked=RecognitionEngine.rank(if(q==RecognitionEngine.query(r.title)) r.title else q,r.subtitle,r.duration,results)
                    choose("Select title • evidence score",ranked.map { it.item.label()+" • "+it.score+"/100\n"+it.reason }) { index ->
                        val item = ranked[index].item
                        if (item.type == "movie") confirmMatch(r,item) else selectSuggestedEpisode(r,item)
                    }
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { launch("movie") }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener { launch("show") }
        }
        dialog.show()
    }
    private fun <T> lookup(label: String, request: ((Result<T>) -> Unit) -> Unit, success: (T) -> Unit) {
        val progress = android.app.AlertDialog.Builder(this).setMessage(label + "…").setNegativeButton("Cancel",null).create()
        progress.show()
        try { request { result ->
            if (!isFinishing && !isDestroyed && progress.isShowing) {
                progress.dismiss()
                try { result.getOrThrow().let(success) } catch(e:Exception) { DebugTrace.failure("catalogue-ui",e); android.app.AlertDialog.Builder(this).setTitle("Catalogue unavailable").setMessage("Unable to complete this request. Try again later.").setPositiveButton("OK",null).show() }
            }
        } } catch(e:Exception) { progress.dismiss(); DebugTrace.failure("catalogue-request",e); if(!isFinishing && !isDestroyed) Toast.makeText(this,"Unable to start request. Try again.",Toast.LENGTH_LONG).show() }
    }
    private fun choose(title: String, labels: List<String>, selected: (Int) -> Unit) {
        if (labels.isEmpty()) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage("No results. Try another title or spelling.").setPositiveButton("OK",null).show(); return }
        android.app.AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index -> selected(index) }.setNegativeButton("Cancel",null).show()
    }
    private fun selectSuggestedEpisode(r:WatchRecord,show:CatalogueItem) {
        val evidence=r.title+" "+r.subtitle
        val ep=RecognitionEngine.episodeOf(evidence) ?: AnimeEpisodeMapping.specialNumber(evidence)
        if(ep==null) {
            val absolute=AnimeEpisodeMapping.absoluteNumber(evidence)
            if(absolute==null) selectSeason(r,show) else android.app.AlertDialog.Builder(this).setTitle("Episode numbering").setMessage("Detected episode "+absolute+". Absolute anime numbering may differ from catalogue season numbering. Choose the exact season and episode manually.").setPositiveButton("Choose episode") { _,_->selectSeason(r,show) }.setNegativeButton("Cancel",null).show()
            return
        }
        lookup("Checking detected episode", { done -> TraktCatalogue.episodes(show,ep.season,done) }) { episodes:List<CatalogueItem> ->
            val exact=RecognitionEngine.exactEpisode(show.id,ep,episodes)
            if(exact==null) selectSeason(r,show) else android.app.AlertDialog.Builder(this).setTitle("Detected episode").setMessage(exact.label()+"\nSeason and episode agree with the detected text. Confirm the exact episode.").setNegativeButton("Cancel",null).setNeutralButton("Choose another") { _,_->selectSeason(r,show) }.setPositiveButton("Review match") { _,_->confirmMatch(r,exact) }.show()
        }
    }
    private fun selectSeason(r: WatchRecord, show: CatalogueItem) {
        lookup("Loading seasons", { done -> TraktCatalogue.seasons(show,done) }) { seasons: List<CatalogueSeason> ->
            choose(show.title, seasons.map { if (it.number == 0) "Specials (Season 0)" else "Season " + it.number + if (it.title.isNotBlank()) " — " + it.title else "" }) { i ->
                val number = seasons[i].number
                lookup("Loading episodes", { done -> TraktCatalogue.episodes(show,number,done) }) { episodes: List<CatalogueItem> ->
                    choose("Choose exact episode",episodes.map { it.label() }) { j -> confirmMatch(r,episodes[j]) }
                }
            }
        }
    }
    private fun confirmMatch(r: WatchRecord, item: CatalogueItem) {
        android.app.AlertDialog.Builder(this).setTitle("Save local match?").setMessage(item.label() + "\nTrakt ID: " + item.id + "\nThis does not mark it watched on Trakt.").setNegativeButton("Cancel",null).setPositiveButton("Save match") { _,_ -> history.match(r.id,item); ViewingNotifications.cancel(this,r.id); refreshHistory() }.show()
    }
    private fun refresh() {
        val raw=prefs.getString("sessions","[]") ?: "[]"
        val manager=getSystemService(android.app.NotificationManager::class.java)
        val access=if(android.os.Build.VERSION.SDK_INT>=27) manager.isNotificationListenerAccessGranted(android.content.ComponentName(this,PlaybackListener::class.java)) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==android.content.ComponentName(this,PlaybackListener::class.java) }
        val last=prefs.getLong("last_sample",0)
        val age=if(last>0) ((System.currentTimeMillis()-last)/1000).coerceAtLeast(0) else -1
        val info=listOf("Sampling enabled: "+prefs.getBoolean("enabled",false),"Notification listener access: "+access,"Listener connected (last reported): "+prefs.getBoolean("listener_connected",false),"Last sample: "+(if(age<0) "never" else age.toString()+" sec ago"),"Selected apps: "+(prefs.getStringSet("allowed",emptySet())?.size ?: 0),"Usage fallback: "+(prefs.contains("usage_since") && UsageFallback.allowed(this)),"Review notifications permitted: "+ViewingNotifications.enabled(this),"Selected media sessions: "+prefs.getInt("session_count",0),"Tracked sessions: "+prefs.getInt("tracked_session_count",0),"Last error: "+prefs.getString("sample_error","none")).joinToString("\n")
        val current=runCatching { JSONArray(raw).toString(2) }.getOrDefault("[]")
        val previous=runCatching { JSONArray(prefs.getString("last_nonempty_sessions","[]") ?: "[]").toString(2) }.getOrDefault("[]")
        output.text=info+"\n\nCurrent selected media sessions:\n"+current+"\n\nLast nonempty selected media sessions:\n"+previous
    }
}
