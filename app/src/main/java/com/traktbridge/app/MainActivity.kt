package com.traktbridge.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
    private lateinit var settingsRoot:LinearLayout
    private lateinit var settingsOverview:LinearLayout
    private val settingsGroups=linkedMapOf<String,LinearLayout>()
    private var settingsCategory=""
    private lateinit var contentPage: ScrollView
    private lateinit var nav: LinearLayout
    private var composeNav:ComposeView?=null
    private val bridgeModel by lazy { androidx.lifecycle.ViewModelProvider(this)[BridgeViewModel::class.java] }
    private val selectedNav get()=bridgeModel.selectedTab
    private lateinit var dashboard: LinearLayout
    private lateinit var libraryHost: LinearLayout
    private lateinit var libraryCompose: ComposeView
    private var libraryContent by androidx.compose.runtime.mutableStateOf<(@androidx.compose.runtime.Composable ()->Unit)?>(null)
    private var tab=0
    private var privacyPrompt=false
    private var unlocked=false
    private var historyFilter=HistoryFilter()
    private val lowRam get()=getSystemService(android.app.ActivityManager::class.java).isLowRamDevice
    private var historyLimit=100
    private var librarySelection=""
    private var libraryCategory="All"
    private var restoreContentFocus=false
    private var restoreScrollY=0
    private var analyticsPeriod=30
    private var analyticsSource=""
    private var calendarMonth=java.time.YearMonth.now()
    private val tvMode:Boolean get()=(getSystemService(android.app.UiModeManager::class.java).currentModeType==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
    private val adaptiveLayout get()=resources.configuration.let { AdaptiveUi.layout(it.screenWidthDp,it.screenHeightDp,it.fontScale,tvMode) }
    private val wide:Boolean get()=adaptiveLayout.sidebar
    private val ink:Int get()=if(prefs.getBoolean("high_contrast",false)) 0xffffffff.toInt() else BridgeDesign.ink
    private val muted:Int get()=if(prefs.getBoolean("high_contrast",false)) 0xffe3e3ef.toInt() else BridgeDesign.muted
    private val violet:Int get()=when(prefs.getString("accent","violet")) { "blue"->BridgeDesign.blue; "green"->BridgeDesign.green; else->BridgeDesign.violet }
    private val amoled:Boolean get()=AppearancePolicy.mode(prefs.getString("appearance_mode","dark").orEmpty())=="amoled"
    private val surface:Int get()=if(amoled) 0xff101010.toInt() else BridgeDesign.surface
    private val bg:Int get()=if(amoled) 0xff000000.toInt() else BridgeDesign.background
    private fun showAuth(message: String = auth.status()) { account.text = message; if(!auth.hasConnection()) { unifiedRows=emptyList(); bridgeModel.cancel(); if(::libraryHost.isInitialized) libraryContent=null; refreshHistory() } }
    private fun handleCallback(i: Intent) {
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.encodedAuthority != "callback" || !uri.encodedPath.isNullOrEmpty() || uri.fragment!=null) return
        i.data = null
        showAuth("Connecting to Trakt…")
        auth.complete(uri) { message ->
            if(!isDestroyed && !isFinishing) { showAuth(message); if(message.startsWith("Trakt connected.")) offerTraktSync() }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleCallback(intent); handleReview(intent); handleShared(intent); handleTrackingShortcut(intent); handleAutomation(intent) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==98 && resultCode==RESULT_OK) {
            @Suppress("DEPRECATION") val bitmap=data?.extras?.get("data") as? android.graphics.Bitmap
            if(bitmap==null) { Toast.makeText(this,"No camera image returned. Import a QR image instead.",Toast.LENGTH_LONG).show(); return }
            Thread { val result=runCatching { PairingQr.decode(bitmap) }; runOnUiThread { if(!isDestroyed && !isFinishing) result.fold({ approvePairingInvitation(it) },{ Toast.makeText(this,"QR unreadable. Try closer or import a full-resolution image.",Toast.LENGTH_LONG).show() }) } }.start(); return
        }
        if(requestCode==97 && resultCode==RESULT_OK) { data?.data?.let { readPairingQr(it) }; return }
        if(requestCode==96) { privacyPrompt=false; unlocked=resultCode==RESULT_OK; if(unlocked) { shell.visibility=android.view.View.VISIBLE; refreshHistory(); maybeShowCrashReport() } else finish(); return }
        if(requestCode==57 && resultCode==RESULT_OK) data?.data?.let { inspectVideo(it) }
        if(requestCode==58 && resultCode==RESULT_OK) data?.data?.let { inspectImport(it) }
        if(resultCode==RESULT_OK && requestCode in 65..66) data?.data?.let { settingsTransfer(requestCode,it) }
        if(resultCode==RESULT_OK && requestCode in 63..64) data?.data?.let { encryptedTransfer(requestCode,it) }
        if(resultCode==RESULT_OK && requestCode in 60..62) data?.data?.let { historyTransfer(requestCode,it) }
    }
    private fun capturePairingQr() {
        if(!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) { Toast.makeText(this,"No camera. Import a QR image or enter the signed invitation.",Toast.LENGTH_LONG).show(); return }
        runCatching { startActivityForResult(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),98) }.onFailure { Toast.makeText(this,"Camera unavailable. Use QR image import.",Toast.LENGTH_LONG).show() }
    }
    private fun showPairingInvitation() {
        runCatching {
            val token=DevicePairing.invite(this)
            val matrix=com.google.zxing.qrcode.QRCodeWriter().encode(token,com.google.zxing.BarcodeFormat.QR_CODE,512,512)
            val pixels=IntArray(512*512) { i -> if(matrix[i%512,i/512]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
            val bitmap=android.graphics.Bitmap.createBitmap(pixels,512,512,android.graphics.Bitmap.Config.ARGB_8888)
            val content=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                addView(ImageView(this@MainActivity).apply { setImageBitmap(bitmap); adjustViewBounds=true },LinearLayout.LayoutParams(-1,dp(250)))
                addView(txt("Verification PIN: "+DevicePairing.verificationCode(token)+"\nExpires in 2 minutes. Scan this QR on your phone or tablet, compare the PIN, then approve an invitation from the other device.",16))
                addView(EditText(this@MainActivity).apply { setText(token); keyListener=null; setTextIsSelectable(true); maxLines=3 })
            }
            val dialog=BridgeDialogBuilder(this).setTitle("Pair this device").setView(content).setPositiveButton("Close",null).create()
            dialog.show()
            content.postDelayed({ dialog.dismiss() },120000)
        }.onFailure { Toast.makeText(this,"Unable to create invitation",Toast.LENGTH_LONG).show() }
    }
    private fun approvePairingInvitation(initial: String="") {
        val token=EditText(this).apply { hint="Signed invitation"; setText(initial); maxLines=4; filters=arrayOf(android.text.InputFilter.LengthFilter(2048)) }
        val pin=EditText(this).apply { hint="8-digit PIN shown on other device"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; filters=arrayOf(android.text.InputFilter.LengthFilter(8)) }
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(token); addView(pin) }
        BridgeDialogBuilder(this).setTitle("Verify nearby device").setView(content).setNegativeButton("Cancel",null).setPositiveButton("Verify") { _,_->
            runCatching { DevicePairing.verify(token.text.toString().trim(),pin.text.toString()) }.fold({ peer ->
                BridgeDialogBuilder(this).setTitle("Authorize device?").setMessage(peer.label+"\n"+peer.id+"\nApprove only a device you recognize. The other device must also approve your invitation.").setNegativeButton("Cancel",null).setPositiveButton("Authorize") { _,_->
                    runCatching { DevicePairing.approve(this,peer) }.fold({ refreshDeviceSettings(); Toast.makeText(this,"Identity approved",Toast.LENGTH_SHORT).show() },{ Toast.makeText(this,"Authorization failed",Toast.LENGTH_SHORT).show() })
                }.show()
            },{ Toast.makeText(this,"Invalid or expired invitation/PIN",Toast.LENGTH_LONG).show() })
        }.show()
    }
    private fun readPairingQr(uri: android.net.Uri) {
        Thread {
            val result=runCatching {
                val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
                require(options.outWidth in 1..10000 && options.outHeight in 1..10000)
                options.inJustDecodeBounds=false
                options.inSampleSize=maxOf(1,maxOf(options.outWidth,options.outHeight)/1200)
                val bitmap=contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) } ?: error("Missing image")
                try {
                    PairingQr.decode(bitmap)
                } finally { bitmap.recycle() }
            }
            runOnUiThread { if(!isDestroyed && !isFinishing) result.fold({ approvePairingInvitation(it) },{ Toast.makeText(this,"QR image could not be read",Toast.LENGTH_LONG).show() }) }
        }.start()
    }
    private fun deviceTransferGuide() {
        BridgeDialogBuilder(this).setTitle("Move your library to another device").setMessage("1. Export a password-encrypted history backup.\n2. Export personal settings separately.\n3. Transfer both files using your preferred private file-sharing method.\n4. Restore them on the other device.\n\nThis is manual transfer, not live synchronization. Account credentials and upload approvals never transfer. Reconnect Trakt separately.").setNegativeButton("Close",null).setNeutralButton("Export settings") { _,_->startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-settings.tbenc"),65) }.setPositiveButton("Export history") { _,_->startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-history.tbenc"),63) }.show()
    }
    private fun settingsTransfer(code:Int,uri:android.net.Uri) {
        val field=EditText(this).apply { hint="Password (10+ characters)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        BridgeDialogBuilder(this).setTitle("Personal settings backup").setMessage("Includes default-profile collections, notes, favorites and selected display settings. Excludes tokens, tracking permissions, other profiles and learned mappings. Existing settings are not overwritten on import.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
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
                        runOnUiThread { if(!isDestroyed && !isFinishing) BridgeDialogBuilder(this).setTitle("Merge personal settings?").setMessage("Existing entries remain unchanged. No account credentials or permissions are imported.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
                            Thread { val result=runCatching { PortableSettings.merge(applicationContext,stores) }; runOnUiThread { if(!isDestroyed && !isFinishing) { Toast.makeText(this,result.fold({ it.toString()+" entries imported" },{ "Merge failed" }),Toast.LENGTH_LONG).show(); recreate() } } }.start()
                        }.show() }
                    }
                } catch(_:Exception) { runOnUiThread { if(!isDestroyed && !isFinishing) Toast.makeText(this,"Settings transfer failed",Toast.LENGTH_LONG).show() } }
                finally { password.fill('\u0000') }
            }.start()
        }.show()
    }
    private fun encryptionReadiness() {
        val app=applicationContext
        Thread {
            val result=runCatching { WatchHistory(app).use { db ->
                val rows=db.records(0)
                MigrationReadiness.message(rows.count { it.status=="active" },rows.count { it.status=="queued" },rows.count { it.status=="uncertain" },rows.count { it.status=="blocked" },HistoryBackup.integrity(db))
            } }
            runOnUiThread {
                if(!isDestroyed && !isFinishing) BridgeDialogBuilder(this).setTitle("Encryption migration readiness").setMessage(result.getOrDefault("Unable to validate database. No changes made.")+"\n\nThe live database remains plaintext. Export an encrypted recovery backup before future migration.").setNegativeButton("Close",null).setPositiveButton("Export encrypted backup") { _,_->startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,"traktbridge-history.tbenc"),63) }.show()
            }
        }.start()
    }
    private fun restoreDailyBackup() {
        if(!LocalBackupVault.exists(this)) { Toast.makeText(this,"No daily backup yet",Toast.LENGTH_LONG).show(); return }
        BridgeDialogBuilder(this).setTitle("Restore daily device backup?").setMessage("Existing IDs are skipped. No sync approval is restored.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
            Thread {
                val result=runCatching { val rows=HistoryBackup.parse(LocalBackupVault.read(applicationContext)); WatchHistory(applicationContext).use { HistoryBackup.merge(it,rows) } }
                runOnUiThread { if(!isDestroyed && !isFinishing) { Toast.makeText(this,result.fold({ it.first.toString()+" restored • "+it.second+" skipped" },{ "Restore failed" }),Toast.LENGTH_LONG).show(); refreshHistory() } }
            }.start()
        }.show()
    }
    private fun encryptedTransfer(code:Int,uri:android.net.Uri) {
        val field=EditText(this).apply { hint="Backup password (10+ characters)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        BridgeDialogBuilder(this).setTitle(if(code==63) "Encrypt history backup" else "Unlock history backup").setMessage("Keep the password safely. Lost passwords cannot be recovered. This backup contains viewing history only.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
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
                            BridgeDialogBuilder(this).setTitle("Merge decrypted history?").setMessage(rows.size.toString()+" records; existing IDs skipped. No upload approval is restored.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
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
                        BridgeDialogBuilder(this).setTitle("Restore viewing history").setMessage(rows.size.toString()+" records. Existing IDs will be skipped. Restored entries will not be queued for Trakt upload.").setNegativeButton("Cancel",null).setPositiveButton("Merge") { _,_->
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
            runOnUiThread { if(!isDestroyed && !isFinishing) BridgeDialogBuilder(this).setTitle("Database integrity").setMessage(if(ok) "SQLite quick check passed" else "Integrity check failed or database unavailable. Existing files are preserved. Do not clear app data. Export or retain your encrypted backup before attempting repair.").setPositiveButton("OK",null).show() }
        }.start()
    }
    private var importSource="com.netflix.mediaclient"
    private fun externalHistoryImport() {
        val sources=ExternalHistoryImport.sources
        choose("Import external viewing history",listOf("Downloaded Trakt history")+sources.map { it+" • CSV interchange" }) { i ->
            if(i==0) {
                lookup("Checking Trakt account",{ done -> TraktLibrary.accountId(this,done) }) { account:String ->
                    val rows=ExternalHistoryImport.fromTrakt(TraktLibrary.cachedHistory(this,account),account)
                    BridgeDialogBuilder(this).setTitle("Import cached Trakt events?").setMessage(rows.size.toString()+" local review records. Existing remote completions are not uploaded again automatically. No sampled viewing time is added.").setNegativeButton("Cancel",null).setPositiveButton("Import for review") { _,_->
                        Thread {
                            val result=runCatching { WatchHistory(applicationContext).use { it.importViewing(rows) } }
                            runOnUiThread { if(!isDestroyed && !isFinishing) result.fold({ counts -> showTab(2); Toast.makeText(this,counts.first.toString()+" added • "+counts.second+" duplicates",Toast.LENGTH_LONG).show() },{ Toast.makeText(this,"Import failed",Toast.LENGTH_LONG).show() }) }
                        }.start()
                    }.show()
                }
            } else {
                importSource=sources[i-1]
                BridgeDialogBuilder(this).setTitle("CSV interchange format").setMessage("Use a CSV with Title (or Name) and a genuine viewing date (Date or Watched At). Optional Season and Episode columns are supported. Watchlists, ratings and anime progress counts are not viewing events. Convert unsupported export formats first.").setNegativeButton("Cancel",null).setPositiveButton("Choose file") { _,_->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),58) }.show()
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
                    BridgeDialogBuilder(this).setTitle("Review history import").setMessage(appLabel(source)+"\n"+preview.rows.size+" valid rows • "+preview.skipped+" skipped\n\n"+sample+"\n\nImported activity does not prove playback or completion. Dates without times remain date-only. Nothing is uploaded to Trakt.").setNegativeButton("Cancel",null).setPositiveButton("Import") { _,_->
                        Thread {
                            val saved=runCatching { WatchHistory(applicationContext).use { it.importViewing(preview.rows) } }
                            runOnUiThread {
                                if(!isDestroyed && !isFinishing) saved.fold({ counts -> showTab(2); BridgeDialogBuilder(this).setTitle("Import complete").setMessage(counts.first.toString()+" added • "+counts.second+" already imported").setPositiveButton("OK",null).show() },{ Toast.makeText(this,"Import failed",Toast.LENGTH_LONG).show() })
                            }
                        }.start()
                    }.show()
                },{ BridgeDialogBuilder(this).setTitle("Cannot import CSV").setMessage(it.message ?: "Invalid history file").setPositiveButton("OK",null).show() })
            }
        }.start()
    }
    private fun reconcileImported(r:WatchRecord) {
        val zone=java.time.ZoneId.systemDefault()
        val day=java.time.Instant.ofEpochMilli(r.started).atZone(zone).toLocalDate()
        val row=ImportedViewing(r.source,r.title,r.subtitle,day,null,r.id)
        val matches=HistoryImport.candidates(row,history.records(0),zone)
        if(matches.isEmpty()) { BridgeDialogBuilder(this).setTitle("No exact local match").setMessage("No local viewing with the same provider, date and title. Keep this record or identify it on Trakt.").setPositiveButton("OK",null).show(); return }
        choose("Reconcile with local viewing",matches.map { it.title+" • "+durationText(it.watched)+" sampled • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)) }) { index ->
            BridgeDialogBuilder(this).setTitle("Link imported activity?").setMessage("Keep the existing local viewing record and hide this duplicate import. No watch time or completion will be added.").setNegativeButton("Cancel",null).setPositiveButton("Reconcile") { _,_->
                if(history.reconcileImport(r.id,matches[index].id)) refreshHistory()
            }.show()
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
        if(candidate==null) { BridgeDialogBuilder(this).setTitle("Content not identified").setMessage("Share a video file or a movie title with its link. Private downloads cannot be opened by TraktBridge.").setPositiveButton("OK",null).show(); return }
        val field=EditText(this).apply { setSingleLine(true); hint="Movie, show or anime title"; setText(candidate.title) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12); addView(field)
            addView(txt(listOfNotNull(candidate.detail.takeIf { it.isNotBlank() },candidate.providerId.takeIf { it.isNotBlank() }?.let { "Provider content ID: "+it },candidate.runtime.takeIf { it>0 }?.let { "File runtime: "+durationText(it) },"Confirm the title. Sharing content is not evidence of playback.").joinToString("\n"),14,muted)) }
        val dialog=BridgeDialogBuilder(this).setTitle("Identify shared content").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Continue",null).create()
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
        if(i.action==ViewingNotifications.USAGE_REVIEW) { i.action=null; return }
        if (i.action != ViewingNotifications.REVIEW) return
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.host != "review") return
        if(prefs.getBoolean("privacy_lock",false) && !unlocked) return
        val id = ComponentPolicy.reviewId(uri.toString()) ?: return
        i.action = null; i.data = null
        ViewingNotifications.cancel(this,id)
        val r = history.record(id) ?: return
        if (r.status == "pending" || r.status == "reviewed") {
            if(r.traktId>0) BridgeDialogBuilder(this).setTitle("Review viewing").setMessage(r.matchLabel+"\nDid you finish this title?").setNegativeButton("Not finished",null).setNeutralButton("Wrong title") { _,_-> identify(r) }.setPositiveButton("Finished") { _,_->approveUpload(r) }.show() else identify(r)
        }
    }
    private val apps = PlayerRules.apps
    override fun onCreate(savedInstanceState: Bundle?) {
        val startup=android.os.SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        PlaybackTrackingMigration.apply(this)
        historyLimit=MemoryPolicy.pageSize(lowRam)
        applyScreenProtection()
        DebugTrace.init(this)
        DebugTrace.event("activity","main opened")
        tab=(savedInstanceState?.getInt("tab") ?: DashboardPreferences.startTab(prefs.getInt("startup_tab",0))).coerceIn(0,4)
        settingsCategory=savedInstanceState?.getString("settings_category").orEmpty()
        librarySelection=savedInstanceState?.getString("library_selection").orEmpty()
        libraryCategory=savedInstanceState?.getString("library_category") ?: "All"
        reviewCategory=savedInstanceState?.getString("review_category") ?: "match"
        restoreContentFocus=savedInstanceState?.getBoolean("content_focus",false) ?: false
        restoreScrollY=savedInstanceState?.getInt("content_scroll",0) ?: 0
        importSource=savedInstanceState?.getString("import_source") ?: "com.netflix.mediaclient"
        historyFilter=HistoryFilter(savedInstanceState?.getString("filter_q").orEmpty(),savedInstanceState?.getString("filter_source").orEmpty(),savedInstanceState?.getInt("filter_period") ?: 0,savedInstanceState?.getString("filter_type").orEmpty(),savedInstanceState?.getString("filter_status").orEmpty(),savedInstanceState?.getString("filter_sort") ?: "recent")
        analyticsPeriod=(savedInstanceState?.getInt("analytics_period") ?: 30).let { if(it in setOf(0,7,30,365)) it else 30 }
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
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(adaptiveLayout.padding),dp(8),dp(adaptiveLayout.padding),dp(24)) }
        settingsPage=ScrollView(this).apply { isFillViewport=true; visibility=android.view.View.GONE; addView(root) }; frame.addView(settingsPage)
        dashboard=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(adaptiveLayout.padding),dp(8),dp(adaptiveLayout.padding),dp(24)) }
        contentPage=ScrollView(this).apply { isFillViewport=true; addView(dashboard) }; frame.addView(contentPage)
        libraryHost=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=android.view.View.GONE }
        libraryCompose=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed); setContent { libraryContent?.invoke() } }
        libraryHost.addView(libraryCompose,LinearLayout.LayoutParams(-1,0,1f))
        frame.addView(libraryHost,android.widget.FrameLayout.LayoutParams(-1,-1))
        nav=LinearLayout(this).apply { orientation=if(wide) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL; val padding=if(wide || tvMode) dp(8) else 0; setPadding(padding,padding,padding,padding); setBackgroundColor(if(wide || tvMode) surface else bg) }
        if(wide) { workspace.addView(nav,LinearLayout.LayoutParams(dp(adaptiveLayout.rail),-1)); workspace.addView(frame,LinearLayout.LayoutParams(0,-1,1f)) }
        else { workspace.addView(frame,LinearLayout.LayoutParams(-1,0,1f)); workspace.addView(nav,LinearLayout.LayoutParams(-1,-2)) }
        val navLabels=listOf(getString(R.string.nav_home),getString(R.string.nav_library),getString(R.string.nav_review),getString(R.string.nav_activity),getString(R.string.nav_settings))
        if(tvMode) {
            navLabels.forEachIndexed { i,name -> nav.addView(TextView(this).apply { text=name; textSize=if(tvMode) 18f else 15f; gravity=android.view.Gravity.CENTER; minHeight=dp(64); id=android.view.View.generateViewId(); isFocusable=true; setOnFocusChangeListener { view,focused -> view.alpha=if(focused) 1f else 0.82f; if(tvMode) view.scaleX=if(focused && !prefs.getBoolean("reduce_motion",false)) 1.05f else 1f; if(tvMode) view.scaleY=if(focused) 1.05f else 1f }; setOnClickListener { showTab(i) } },if(wide) LinearLayout.LayoutParams(-1,dp(68)).apply { bottomMargin=dp(6) } else LinearLayout.LayoutParams(0,-2,1f)) }
        } else {
            composeNav=ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { BridgeNavigation(violet,navLabels,selectedNav,wide) { showTab(it) } }
            }
            nav.addView(composeNav,LinearLayout.LayoutParams(-1,-2))
        }
        auth = TraktAuth(applicationContext)
        account = TextView(this).apply { textSize=16f }
        history = WatchHistory(this)
        historyList = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        output=TextView(this).apply { setTextIsSelectable(true); textSize=14f }
        showAuth()
        settingsRoot=root
        buildSettingsCategories()
        onBackPressedDispatcher.addCallback(this,object:androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(tab==4 && settingsCategory.isNotEmpty()) { showSettingsCategory(""); return }
                isEnabled=false
                try { onBackPressedDispatcher.onBackPressed() } finally { isEnabled=true }
            }
        })
        showSettingsCategory(settingsCategory,resetScroll=false)
        refresh()
        showTab(tab)
        if(tvMode) nav.getChildAt(tab)?.requestFocus()
        handleCallback(intent)
        handleReview(intent)
        handleShared(intent)
        handleTrackingShortcut(intent)
        handleAutomation(intent)
        if(!prefs.getBoolean("setup_seen",false)) { prefs.edit().putBoolean("setup_seen",true).apply(); setupGuide() }
        CrashRecovery.consume(this)?.let { DebugTrace.event("recovery","Previous process ended with "+it) }
        Thread { CrashRecovery.recoverSystemExit(applicationContext); runOnUiThread { if(!isDestroyed && !isFinishing) maybeShowCrashReport() } }.start()
        StartupTiming.report(startup,"activity-created")
        shell.viewTreeObserver.addOnPreDrawListener(object:android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw():Boolean { shell.viewTreeObserver.removeOnPreDrawListener(this); StartupTiming.report(startup,"first-pre-draw"); return true }
        })
    }

    private lateinit var settingsStatus:TextView
    private fun settingsIcon(name:String):android.view.View {
        return object:android.view.View(this@MainActivity) {
            private val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color=violet; style=android.graphics.Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=android.graphics.Paint.Cap.ROUND; strokeJoin=android.graphics.Paint.Join.ROUND }
            override fun onDraw(canvas:android.graphics.Canvas) {
                super.onDraw(canvas)
                canvas.save()
                canvas.translate(width/2f,height/2f)
                canvas.scale(dp(22)/24f,dp(22)/24f)
                canvas.translate(-12f,-12f)
                fun line(x:Float,y:Float,u:Float,v:Float)=canvas.drawLine(x,y,u,v,paint)
                fun rect(l:Float,t:Float,r:Float,b:Float)=canvas.drawRoundRect(l,t,r,b,2f,2f,paint)
                fun circle(x:Float,y:Float,r:Float)=canvas.drawCircle(x,y,r,paint)
                fun path(vararg points:Float) { val p=android.graphics.Path();p.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)p.lineTo(points[i],points[i+1]);canvas.drawPath(p,paint) }
                when(name) {
                    "account"->{circle(12f,8f,4f);canvas.drawArc(4f,13f,20f,25f,180f,180f,false,paint)}
                    "tracking"->{rect(3f,4f,21f,20f);path(10f,8f,16f,12f,10f,16f,10f,8f)}
                    "devices"->{rect(2f,4f,16f,15f);line(9f,15f,9f,20f);line(5f,20f,13f,20f);rect(15f,9f,22f,22f)}
                    "appearance"->{circle(12f,12f,9f);path(12f,3f,12f,21f);line(12f,7f,18f,7f);line(12f,12f,21f,12f);line(12f,17f,18f,17f)}
                    "library"->{rect(3f,4f,7f,20f);rect(9f,4f,13f,20f);path(15f,5f,19f,4f,22f,19f,18f,20f,15f,5f)}
                    "privacy"->{path(12f,2f,21f,6f,20f,15f,16f,20f,12f,22f,8f,20f,4f,15f,3f,6f,12f,2f);path(8f,12f,11f,15f,16f,9f)}
                    "data"->{rect(3f,4f,21f,20f);line(3f,9f,21f,9f);line(9f,15f,15f,15f)}
                    "advanced"->{circle(12f,12f,9f);line(12f,11f,12f,17f);circle(12f,7f,0.6f)}
                    else->{circle(12f,12f,8f);path(9f,12f,12f,15f,16f,9f)}
                }
                canvas.restore()
            }
        }.apply { background=rounded((violet and 0x00ffffff) or 0x22000000,12); importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    }
    private fun updateSettingsStatus() {
        if(!::settingsStatus.isInitialized) return
        val enabled=prefs.getBoolean("enabled",false)
        val selected=PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty()).isNotEmpty()
        val connected=PlaybackListener.liveConnected()
        settingsStatus.text=if(enabled && !selected) "Choose YouTube to start tracking" else TrackingRecovery.status(this)
        settingsStatus.setTextColor(if(enabled && selected && connected) violet else muted)
    }
    private fun buildSettingsCategories() {
        val definitions=SettingsCatalog.categories
        settingsOverview=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        settingsRoot.addView(settingsOverview)
        settingsOverview.addView(txt("Settings",28,ink,true))
        settingsOverview.addView(txt("Manage your viewing experience",14,muted))
        val status=panel(settingsOverview).apply { setPadding(dp(14),dp(12),dp(14),dp(12)) }
        settingsStatus=txt("",15,violet,true)
        status.addView(settingsStatus)
        status.addView(txt(if(auth.hasConnection()) "Trakt account connected" else "Trakt account not connected",13,muted))
        updateSettingsStatus()
        val sections=listOf(
            "Account & playback" to listOf("account","tracking","devices"),
            "Personalization" to listOf("appearance","library"),
            "Privacy & data" to listOf("privacy","data"),
            "Help & support" to listOf("advanced")
        )
        for((title,ids) in sections) {
            val card=settingGroup(settingsOverview,title)
            for(id in ids) {
                val category=definitions.first { it.id==id }
                settingRow(card,category.title,category.description,icon=id) { showSettingsCategory(category.id) }
            }
        }
        val version=runCatching { packageManager.getPackageInfo(packageName,0).versionName }.getOrNull().orEmpty()
        settingsOverview.addView(txt("TraktBridge • "+version,12,muted).apply { gravity=android.view.Gravity.CENTER; setPadding(0,dp(10),0,dp(12)) })
        for(category in definitions) {
            val page=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=android.view.View.GONE }
            settingsRoot.addView(page)
            settingsGroups[category.id]=page
            page.addView(TextView(this).apply { text="‹  Settings"; contentDescription="All settings"; setTextColor(violet); textSize=16f; isFocusable=true; isClickable=true; minimumHeight=dp(adaptiveLayout.touch); gravity=android.view.Gravity.CENTER_VERTICAL; setOnClickListener { showSettingsCategory("") } })
            page.addView(txt(category.title,28,ink,true))
            page.addView(txt(category.description,14,muted))
        }
        for(category in definitions) populateSettings(category.id,requireNotNull(settingsGroups[category.id]))
    }

    private fun showSettingsCategory(id:String,resetScroll:Boolean=true) {
        settingsCategory=id.takeIf { it in settingsGroups }.orEmpty()
        settingsOverview.visibility=if(settingsCategory.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        settingsGroups.forEach { (key,page) -> page.visibility=if(key==settingsCategory) android.view.View.VISIBLE else android.view.View.GONE }
        updateSettingsStatus()
        if(settingsCategory in setOf("account","devices","tracking","privacy")) refreshSettingsState(settingsCategory)
        if(resetScroll) settingsPage.scrollTo(0,0)
        if(tvMode && tab==4) settingsPage.post { if(tab==4) TvFocus.firstContent(settingsPage) }
    }

    private fun settingGroup(parent:LinearLayout,title:String):LinearLayout {
        parent.addView(txt(title,13,violet,true).apply { setPadding(dp(4),dp(14),dp(4),dp(8)) })
        return panel(parent).apply { setPadding(dp(10),dp(2),dp(10),dp(2)) }
    }
    private fun settingDivider(parent:LinearLayout) {
        if(parent.childCount>0) parent.addView(android.view.View(this).apply { setBackgroundColor(0x167F7F7F) },LinearLayout.LayoutParams(-1,dp(1)).apply { marginStart=dp(4); marginEnd=dp(4) })
    }
    private fun settingRow(parent:LinearLayout,title:String,detail:String="",action:()->Unit) = settingRow(parent,title,detail,null,action)
    private fun settingRow(parent:LinearLayout,title:String,detail:String="",icon:String?,action:()->Unit) {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL
            isFocusable=true; isClickable=true; contentDescription=if(detail.isBlank()) title else "$title. $detail"
            minimumHeight=dp(adaptiveLayout.touch); setPadding(dp(6),dp(12),dp(6),dp(12))
            background=if(tvMode) TvFocus.background(this@MainActivity,surface,violet) else android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((violet and 0x00ffffff) or 0x22000000),rounded(surface,10),null)
            setOnClickListener { BridgeHaptics.tap(this); action() }
        }
        if(icon!=null) row.addView(settingsIcon(icon),LinearLayout.LayoutParams(dp(40),dp(40)).apply { marginEnd=dp(12) })
        val text=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(txt(title,16,ink,true)); if(detail.isNotBlank()) addView(txt(detail,13,muted).apply { setPadding(0,dp(3),0,0) }) }
        row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(txt("›",24,muted).apply { setPadding(dp(12),0,0,0); importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO })
        settingDivider(parent)
        parent.addView(row,LinearLayout.LayoutParams(-1,-2))
    }
    private fun settingToggle(parent:LinearLayout,title:String,detail:String,checked:Boolean,onChange:(Switch,Boolean)->Unit) {
        settingDivider(parent)
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL
            minimumHeight=dp(adaptiveLayout.touch); setPadding(dp(6),dp(12),dp(6),dp(12))
        }
        val labels=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            addView(txt(title,16,ink,true))
            addView(txt(detail,13,muted).apply { setPadding(0,dp(3),0,0) })
        }
        val states=arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf())
        val toggle=Switch(this).apply {
            contentDescription="$title. $detail"; isChecked=checked; isFocusable=true
            thumbTintList=android.content.res.ColorStateList(states,intArrayOf(violet,muted))
            trackTintList=android.content.res.ColorStateList(states,intArrayOf((violet and 0x00ffffff) or 0x66000000,0xff45454a.toInt()))
        }
        toggle.setOnCheckedChangeListener { _,value -> onChange(toggle,value) }
        row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(toggle,LinearLayout.LayoutParams(-2,-2))
        labels.setOnClickListener { toggle.isChecked=!toggle.isChecked }
        parent.addView(row,LinearLayout.LayoutParams(-1,-2))
    }
    private fun refreshSettingsState(id:String) {
        val page=settingsGroups[id] ?: return
        if(id=="account") (account.parent as? ViewGroup)?.removeView(account)
        if(page.childCount>3) page.removeViews(3,page.childCount-3)
        populateSettings(id,page)
        updateSettingsStatus()
    }
    private fun refreshDeviceSettings() = refreshSettingsState("devices")
    private fun disableTitleDetection() {
        prefs.edit().putBoolean("title_optin",false).remove("segment:"+YouTubePlayback.PACKAGE).remove("segment_time:"+YouTubePlayback.PACKAGE).apply()
        YouTubePlayback.clear()
        PlaybackListener.wake()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        recreate()
    }
    private fun pairDeviceMenu() {
        val labels=mutableListOf("Show my pairing QR","Enter invitation and PIN","Import QR image")
        if(!tvMode) labels.add("Scan QR with camera")
        choose("Pair a device",labels) { i ->
            when(i) {
                0->showPairingInvitation()
                1->approvePairingInvitation()
                2->startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),97)
                3->capturePairingQr()
            }
        }
    }
    private fun deviceAdvancedMenu() {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(12)) }
        val advanced=settingGroup(body,"Network & maintenance")
        settingRow(advanced,"Cloud relay","Optional self-hosted relay for remote networks") { configureDeviceRelay() }
        settingRow(advanced,"Discover nearby devices","Search the local network; identities remain unverified") {
                    val status=TextView(this).apply { text="Searching local network…"; setPadding(dp(20),dp(12),dp(20),dp(12)) }
                    val scanner=DeviceDiscovery(this) { names -> status.text=if(names.isEmpty()) "No nearby devices found. Open discovery on another device." else names.joinToString("\n") }
                    val dialog=BridgeDialogBuilder(this).setTitle("Nearby devices (unverified)").setView(status).setPositiveButton("Close",null).create()
                    dialog.setOnDismissListener { scanner.close() }
                    dialog.show()
                    runCatching { scanner.start() }.onFailure { status.text="Local discovery unavailable" }
                    status.postDelayed({ dialog.dismiss() },60000)
                }
        settingRow(advanced,"Clear received history","Delete only copies received from paired devices") {
                    BridgeDialogBuilder(this).setTitle("Clear received history?").setMessage("Local viewing history and Trakt approvals are unchanged. If sharing remains enabled, paired devices may send history again.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_-> Thread { runCatching { DeviceHistory.clear(applicationContext) } }.start() }.show()
                }
        if(DevicePairing.peers(this).isNotEmpty()) settingRow(advanced,"Revoke all devices","Remove all paired-device trust") { confirmDeviceRevocation(null) }
        BridgeDialogBuilder(this).setTitle("Advanced device settings").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun populateSettings(id:String,root:LinearLayout) {
        fun label(s:String) { root.addView(txt(s,14,muted)) }
        var group:LinearLayout?=null
        fun heading(title:String) { group=settingGroup(root,title) }
        fun button(s:String,action:()->Unit) { settingRow(group ?: root,s,action=action) }
        when(id) {
            "account" -> {
                heading("Trakt account")
                (group ?: root).addView(account.apply { setPadding(dp(4),dp(10),dp(4),dp(10)) })
                settingRow(group ?: root,"Manage connection","Sign in, verify or disconnect your account") {
                    val entries=if(auth.hasConnection()) listOf("Verify connection","Reconnect Trakt","Disconnect locally") else listOf("Connect Trakt")
                    choose("Trakt connection",entries) { index ->
                        when(entries[index]) {
                            "Verify connection" -> { showAuth("Checking…"); auth.check { showAuth(it) } }
                            "Disconnect locally" -> BridgeDialogBuilder(this).setTitle("Disconnect Trakt?").setMessage("Removes local authorization and downloaded account data, and stops daily sync. Your Trakt account and local viewing history are unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Disconnect") { _,_-> RemoteRefreshSchedule.disable(this); auth.disconnect { showAuth(it) } }.show()
                            else -> try { startActivity(Intent(Intent.ACTION_VIEW,auth.begin())); showAuth("Complete sign-in in your browser") } catch (_:Exception) { showAuth("Unable to open sign-in") }
                        }
                    }
                }
                heading("Synchronization")
                settingRow(group ?: root,"Automatic sync","Refresh your downloaded Trakt library daily") { configureAccountRefresh() }
                settingRow(group ?: root,"Sync now","Download existing watches, watchlists, ratings and more") { offerTraktSync() }
                settingRow(group ?: root,"Trakt library","Browse downloaded watchlist, ratings, favorites, collections and playback") { openDownloadedTrakt() }
                val cacheAccount=RemoteAccountCache.active(this)
                val lastHistory=cacheAccount?.let { RemoteAccountCache.status(this,it,TraktData.HISTORY) }
                settingRow(group ?: root,"History sync status",lastHistory?.let { it.count.toString()+" watches • "+(if(it.complete) "Complete" else "Partial")+" • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.updated)) } ?: "Not downloaded") { offerTraktSync() }
                heading("Data & maintenance")
                settingRow(group ?: root,"Sync maintenance","Retry approved uploads or clear downloaded account data") {
                    choose("Sync maintenance",listOf("Retry approved uploads","Clear downloaded library")) { index ->
                        if(index==0) { HistorySync.schedule(this); refreshHistory(); Toast.makeText(this,"Approved sync queue scheduled",Toast.LENGTH_SHORT).show() }
                        else BridgeDialogBuilder(this).setTitle("Clear downloaded Trakt library?").setMessage("Stops automatic refresh and deletes downloaded account data on this device. Local viewing records and your Trakt account are unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_-> RemoteRefreshSchedule.disable(this); TraktLibrary.clearCache(this); bridgeModel.cancel(); libraryContent=null; unifiedRows=emptyList(); refreshSettingsState("account"); refreshHistory() }.show()
                    }
                }
                settingRow(group ?: root,"About Trakt sync","Data usage and upload permissions") {
                    BridgeDialogBuilder(this).setTitle("About Trakt sync").setMessage("Uses Trakt but is not endorsed or certified by Trakt. Only selected apps are tracked locally. Automatic and manual catalogue searches send title queries to Trakt to identify viewing records. Suggestions do not upload watches. Only explicitly approved completed watches are uploaded. Downloaded account history is separate from measured local viewing.").setPositiveButton("Close",null).show()
                }
            }
            "tracking" -> {
                heading("Playback")
                settingToggle(group ?: root,"Playback tracking","Record viewing from supported apps",prefs.getBoolean("enabled",false)) { _,checked ->
                    prefs.edit().putBoolean("enabled",checked).apply()
                    if(checked) startTracking() else stopService(Intent(this@MainActivity,TrackingService::class.java))
                    refresh(); updateSettingsStatus()
                }
                val selected=PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty())
                settingRow(group ?: root,"Tracked apps",if(selected.isEmpty()) "No apps selected" else "YouTube • regular videos only") { chooseInstalledApps() }
                settingToggle(group ?: root,"Automatically sync completed rewatches","Only after sufficient measured playback; original watched dates stay unchanged",prefs.getBoolean("auto_rewatch_sync",false)) { _,checked -> prefs.edit().putBoolean("auto_rewatch_sync",checked).apply(); RewatchAutomation.request(this) }
                settingRow(group ?: root,"Completion review threshold",CompletionPolicy.threshold(prefs.getInt("completion_threshold",CompletionPolicy.DEFAULT_THRESHOLD)).toString()+"% • confirmation still required") { configureCompletionThreshold() }
                heading("YouTube player identification")
                settingRow(group ?: root,"Accessibility access",if(!prefs.getBoolean("title_optin",false)) "Off • needed to distinguish Shorts and ads when metadata is incomplete" else if(prefs.getBoolean("title_connected",false)) "On • reads player control IDs only" else "Enabled in app • connect in Android settings") {
                    if(prefs.getBoolean("title_optin",false)) {
                        choose("YouTube player identification",listOf("Open Android Accessibility settings","Turn off player identification")) { i -> if(i==0) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else disableTitleDetection() }
                    } else {
                        BridgeDialogBuilder(this).setTitle("YouTube player identification").setMessage("Allow TraktBridge to read visible YouTube player control IDs to distinguish regular videos from Shorts and ads. It does not read screen titles or capture screenshots.").setNegativeButton("Cancel",null).setPositiveButton("Agree and open settings") { _,_ -> prefs.edit().putBoolean("title_optin",true).apply(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
                    }
                }
                heading("Android permissions")
                settingRow(group ?: root,"Notification access",if(TrackingRecovery.listenerGranted(this)) "Granted" else "Required for MediaSession tracking") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                settingRow(group ?: root,"Battery optimization",if(getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) "Exempt • background tracking allowed" else "Manage background battery restrictions") { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
                heading("Notifications")
                settingToggle(group ?: root,"Viewing review notifications","Notify when a viewing record needs attention",prefs.getBoolean("review_notifications",false)) { _,checked ->
                    prefs.edit().putBoolean("review_notifications",checked).apply()
                    if(checked && android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),41)
                    if(!checked) history.records().forEach { ViewingNotifications.cancel(this@MainActivity,it.id) }
                }
                settingRow(group ?: root,"Android notification settings","Manage channels and notification permissions") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) }
                heading("Setup & troubleshooting")
                settingRow(group ?: root,"Guided tracking setup","Step-by-step permission and app setup") { setupGuide() }
                settingRow(group ?: root,"Repair tracking",TrackingRecovery.status(this)) { repairTracking() }
            }

            "devices" -> {
                val peers=DevicePairing.peers(this).sortedBy { it.label }
                val identity=runCatching { DeviceIdentity.register(this) }.getOrNull()
                val devices=settingGroup(root,"Your devices")
                settingRow(devices,"This device",identity?.label ?: "Name and fingerprint") {
                    runCatching { DeviceIdentity.register(this) }.fold({ device ->
                        val field=EditText(this).apply { setText(device.label); filters=arrayOf(android.text.InputFilter.LengthFilter(80)) }
                        BridgeDialogBuilder(this).setTitle("This device")
                            .setMessage("Device fingerprint: "+device.id+"\nLocal registration only. No playback is shared yet.")
                            .setView(field).setNegativeButton("Close",null).setPositiveButton("Rename") { _,_->
                                runCatching { DeviceIdentity.rename(this,field.text.toString()) }.onSuccess { refreshDeviceSettings() }.onFailure { Toast.makeText(this,"Invalid device name",Toast.LENGTH_SHORT).show() }
                            }.show()
                    }, { Toast.makeText(this,"Device registration unavailable",Toast.LENGTH_LONG).show() })
                }
                settingRow(devices,"Pair a device","Show your QR or scan/enter another device’s invitation") { pairDeviceMenu() }
                if(peers.isEmpty()) devices.addView(txt("No paired devices yet. Pair your phone, tablet or TV to get started.",13,muted))
                else {
                    for(peer in peers.take(30)) settingRow(devices,peer.label,"Paired • manage access") { confirmDeviceRevocation(peer) }
                    if(peers.size>30) settingRow(devices,"All paired devices",peers.size.toString()+" paired") { managePairedDevices() }
                }
                val sharing=settingGroup(root,"Sharing & permissions")
                settingRow(sharing,"Live playback sharing",if(prefs.getBoolean("device_sharing",false)) "On • share current playback" else "Off • keep playback on this device") {
                    val enabled=prefs.getBoolean("device_sharing",false)
                    BridgeDialogBuilder(this).setTitle(if(enabled) "Stop device sharing?" else "Enable device sharing?")
                        .setMessage("Share live playback title, episode detail, provider and progress only with approved devices over encrypted local Wi-Fi. Tracking must be running on each device. No video or Trakt credentials are shared.")
                        .setNegativeButton("Cancel",null).setPositiveButton(if(enabled) "Disable" else "Enable") { _,_->
                            prefs.edit().putBoolean("device_sharing",!enabled).apply()
                            if(enabled) RemotePlayback.clear()
                            if(prefs.getBoolean("enabled",false)) startTracking()
                            refreshDeviceSettings()
                            Toast.makeText(this,if(enabled) "Sharing disabled" else "Sharing enabled while tracking is active",Toast.LENGTH_LONG).show()
                        }.show()
                }
                settingRow(sharing,"Viewing history",if(prefs.getBoolean("device_history",false)) "On • share recent sessions" else "Off • keep history on this device") {
                    val enabled=prefs.getBoolean("device_history",false)
                    BridgeDialogBuilder(this).setTitle(if(enabled) "Disable history sharing?" else "Share viewing history with paired devices?")
                        .setMessage("Separate opt-in: exchange up to 100 recent sampled sessions from the last 90 days. Remote history stays read-only and never inherits Trakt approval. Disabling hides received history; clear it separately if desired.")
                        .setNegativeButton("Cancel",null).setPositiveButton(if(enabled) "Disable" else "Enable") { _,_-> prefs.edit().putBoolean("device_history",!enabled).apply(); refreshDeviceSettings() }.show()
                }
                settingRow(sharing,"Remote playback controls",if(prefs.getBoolean("device_controls",false)) "On • paired devices may control playback" else "Off • no remote control") {
                    val enabled=prefs.getBoolean("device_controls",false)
                    BridgeDialogBuilder(this).setTitle(if(enabled) "Disable remote controls?" else "Allow paired devices to control playback?")
                        .setMessage("Paired devices may play or pause supported selected media sessions. Controls require a separate opt-in and can be disabled at any time.")
                        .setNegativeButton("Cancel",null).setPositiveButton(if(enabled) "Disable" else "Enable") { _,_-> prefs.edit().putBoolean("device_controls",!enabled).apply(); refreshDeviceSettings() }.show()
                }
                settingRow(sharing,"View shared history","Read-only viewing history from paired devices") { showDeviceHistory() }
                val advanced=settingGroup(root,"Advanced")
                settingRow(advanced,"Network & device maintenance","Relay, discovery and received history") { deviceAdvancedMenu() }
            }
            "appearance" -> {
                val themes=listOf("Classic dark","AMOLED black","Dynamic dark (Android 12+)")
                val themeKeys=listOf("dark","amoled","dynamic")
                val accents=listOf("Violet","Blue","Green")
                val accentKeys=listOf("violet","blue","green")
                val sizes=listOf("System default","Large","Extra large")
                fun preferenceRow(title:String,detail:String,action:()->Unit) {
                    settingRow(group ?: root,title,detail,action)
                }
                fun appearanceToggle(title:String,detail:String,key:String,default:Boolean,onChange:(android.view.View,Boolean)->Unit={ _,_-> }) {
                    settingToggle(group ?: root,title,detail,prefs.getBoolean(key,default)) { view,checked ->
                        prefs.edit().putBoolean(key,checked).apply(); onChange(view,checked)
                    }
                }
                heading("Theme & colors")
                preferenceRow("Theme",themes[themeKeys.indexOf(prefs.getString("appearance_mode","dark").orEmpty()).coerceAtLeast(0)]) { choose("Theme",themes) { prefs.edit().putString("appearance_mode",themeKeys[it]).apply(); recreate() } }
                preferenceRow("Accent color",accents[accentKeys.indexOf(prefs.getString("accent","violet").orEmpty()).coerceAtLeast(0)]) { choose("Accent color",accents) { prefs.edit().putString("accent",accentKeys[it]).apply(); recreate() } }
                heading("Display")
                preferenceRow("Text size",sizes[prefs.getInt("reading_size",0).coerceIn(0,sizes.lastIndex)]) { choose("Reading size",sizes) { prefs.edit().putInt("reading_size",it).apply(); recreate() } }
                appearanceToggle("High contrast text","Improve text visibility","high_contrast",false) { _,_-> recreate() }
                heading("Interaction")
                if(!tvMode) appearanceToggle("Haptic feedback","Feel a light vibration when interacting",BridgeHaptics.PREF,true) { view,checked -> if(checked) BridgeHaptics.tap(view) }
                appearanceToggle("Reduce motion","Minimize interface animations","reduce_motion",false) { _,_-> recreate() }
                heading("Content preferences")
                appearanceToggle("Hide spoilers","Keep catalogue plot details hidden","hide_spoilers",true)
            }
            "library" -> {
                fun libraryRow(title:String,detail:String,action:()->Unit) {
                    settingRow(group ?: root,title,detail,action)
                }
                fun libraryMenu(title:String,entries:List<Triple<String,String,()->Unit>>) {
                    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(4),dp(12),dp(12)) }
                    val card=panel(body).apply { setPadding(dp(12),dp(4),dp(12),dp(4)) }
                    val dialog=BridgeDialogBuilder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
                    entries.forEach { (name,detail,action) -> settingRow(card,name,detail) { dialog.dismiss(); action() } }
                    dialog.show()
                }
                heading("Personalization")
                libraryRow("Dashboard & viewing goal","Home layout, widget and monthly time budget") {
                    libraryMenu("Dashboard & viewing goal",listOf(
                        Triple("Customize dashboard","Choose visible sections, order and widget") { customizeDashboard() },
                        Triple("Monthly viewing goal","Set an optional viewing time budget") { configureGoal() }
                    ))
                }
                heading("Library organization")
                libraryRow("Collections & watchlists","Personal lists, automatic rules, profiles and sharing") {
                    libraryMenu("Collections & watchlists",listOf(
                        Triple("Personal collections","Create and manage your own collections") { collections() },
                        Triple("Smart collections","Browse automatically grouped viewing history") { smartCollections() },
                        Triple("Smart watchlists","Create saved automatic watchlist rules") { smartWatchlists() },
                        Triple("Collection profiles","Manage separate collections and personal notes") { collectionProfiles() },
                        Triple("Share or import","Exchange personal collections") { shareCollections() }
                    ))
                }
                libraryRow("Tags & folders","Find and organize titles by tags or collection") { organizedLibrary() }
                libraryRow("Favorites","Manage saved favorite titles") { favorites() }
                heading("Discovery")
                libraryRow("Franchise guides","Explore curated movie viewing orders") { franchises() }
                libraryRow("Anime","Local progress and public AniList libraries") {
                    libraryMenu("Anime",listOf(
                        Triple("My anime progress","Track episodes locally") { animeLocalProgress() },
                        Triple("Public AniList library","Browse a public account without signing in") { publicAnimeList() }
                    ))
                }
                heading("Shortcuts & reminders")
                libraryRow("Shortcuts","Automation links and direct playback shortcuts") {
                    libraryMenu("Shortcuts",listOf(
                        Triple("Automation shortcuts","Copy links for Android automation tools") { automationShortcuts() },
                        Triple("Playback shortcuts","Open a specific title or installed player") { playbackShortcut() }
                    ))
                }
                libraryRow("Episode reminders","Manage or cancel scheduled reminders") {
                    BridgeDialogBuilder(this).setTitle("Episode reminders").setMessage("Cancel all scheduled episode reminders on this device?").setNegativeButton("Keep reminders",null).setPositiveButton("Cancel all reminders") { _,_->
                        prefs.edit().putBoolean("episode_reminders",false).apply()
                        EpisodeReminders.cancelAll(this)
                        Toast.makeText(this,"Reminders cancelled",Toast.LENGTH_SHORT).show()
                    }.show()
                }
            }
            "privacy" -> {
                heading("Tracking privacy")
                val trackingActive=prefs.getBoolean("enabled",false) || prefs.getBoolean("title_optin",false) || prefs.getBoolean("device_sharing",false) || prefs.getBoolean("device_controls",false) || prefs.getBoolean("device_history",false)
                settingRow(group ?: root,"Pause all tracking",if(trackingActive) "Tracking or sharing is enabled" else "Tracking and sharing are paused") { privacyMode() }
                settingRow(group ?: root,"Privacy overview","Review tracking, data storage and account privacy") { privacyDashboard() }
                heading("App protection")
                settingToggle(group ?: root,"App lock","Require device authentication when returning to the app",prefs.getBoolean("privacy_lock",false)) { toggle,checked ->
                    if(checked && !getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
                        toggle.isChecked=false
                        Toast.makeText(this,"Set a device screen lock first",Toast.LENGTH_LONG).show()
                    } else {
                        prefs.edit().putBoolean("privacy_lock",checked).apply()
                        if(checked) { unlocked=false; requirePrivacyUnlock() }
                    }
                }
                settingToggle(group ?: root,"Screen protection","Block screenshots and recent-app previews",prefs.getBoolean("secure_screen",false)) { _,checked ->
                    prefs.edit().putBoolean("secure_screen",checked).apply(); applyScreenProtection()
                }
            }
            "data" -> {
                fun dataRow(title:String,detail:String,action:()->Unit) { settingRow(group ?: root,title,detail,action) }
                fun dataMenu(title:String,entries:List<Triple<String,String,()->Unit>>) {
                    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(4),dp(12),dp(12)) }
                    val card=panel(body).apply { setPadding(dp(12),dp(4),dp(12),dp(4)) }
                    val dialog=BridgeDialogBuilder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
                    entries.forEach { (name,detail,action) -> settingRow(card,name,detail) { dialog.dismiss(); action() } }
                    dialog.show()
                }
                fun encryptedFile(request:Int,name:String="") {
                    val intent=if(name.isNotEmpty()) Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,name)
                        else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    startActivityForResult(intent,request)
                }
                heading("Backup & recovery")
                settingToggle(group ?: root,"Automatic daily backup","Encrypted viewing history stored on this device",prefs.getBoolean("daily_backup",false)) { _,checked ->
                    prefs.edit().putBoolean("daily_backup",checked).apply(); LocalBackupVault.schedule(this,checked)
                }
                dataRow("Create backup","Encrypted history or portable JSON file") {
                    dataMenu("Create backup",listOf(
                        Triple("Encrypted history","Password-protected backup") { encryptedFile(63,"traktbridge-history.tbenc") },
                        Triple("JSON history","Unencrypted portable backup") { pickBackup(60) }
                    ))
                }
                dataRow("Restore backup","Daily device backup, encrypted file or JSON") {
                    dataMenu("Restore backup",listOf(
                        Triple("Daily device backup","Restore the encrypted on-device copy") { restoreDailyBackup() },
                        Triple("Encrypted history file","Choose a protected backup file") { encryptedFile(64) },
                        Triple("JSON history file","Choose a portable history backup") { pickBackup(62) }
                    ))
                }
                dataRow("Transfer to another device","Move data using the device transfer guide") { deviceTransferGuide() }
                heading("Import & export")
                dataRow("Import viewing history","Letterboxd, streaming services, Trakt and other sources") {
                    dataMenu("Import viewing history",listOf(
                        Triple("Letterboxd diary","Import a CSV diary") { importSource="letterboxd"; startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),58) },
                        Triple("Streaming history","Import provider CSV data") { chooseImportProvider() },
                        Triple("Other services","Trakt, IMDb, Simkl and anime history") { externalHistoryImport() }
                    ))
                }
                dataRow("Export viewing history","CSV file for spreadsheets and other tools") { pickBackup(61) }
                dataRow("Personal settings","Import or export encrypted preferences") {
                    dataMenu("Personal settings",listOf(
                        Triple("Export settings","Create an encrypted settings file") { encryptedFile(65,"traktbridge-settings.tbenc") },
                        Triple("Import settings","Restore preferences from an encrypted file") { encryptedFile(66) }
                    ))
                }
                heading("Storage & maintenance")
                settingToggle(group ?: root,"Artwork on unmetered networks only","Avoid artwork downloads on metered connections",prefs.getBoolean("artwork_unmetered",false)) { _,checked ->
                    prefs.edit().putBoolean("artwork_unmetered",checked).apply()
                }
                dataRow("Recently deleted","Review recoverable viewing records") { recentlyDeleted() }
                dataRow("Manage storage","Clear cached data or delete the daily backup") {
                    dataMenu("Manage storage",listOf(
                        Triple("Clear offline catalogue","Remove cached catalogue information") { getSharedPreferences("offline_catalogue",MODE_PRIVATE).edit().clear().apply() },
                        Triple("Clear artwork cache","Remove downloaded artwork") { Artwork.clear(this); Toast.makeText(this,"Artwork cache cleanup requested",Toast.LENGTH_SHORT).show() },
                        Triple("Delete daily backup","Remove the on-device backup file") {
                            BridgeDialogBuilder(this).setTitle("Delete daily backup?").setMessage("This deletes the current backup copy, not your viewing history. Automatic backups may create a new copy if enabled.").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_-> LocalBackupVault.delete(this); Toast.makeText(this,"Device backup deleted",Toast.LENGTH_SHORT).show() }.show()
                        }
                    ))
                }
            }
            "advanced" -> {
                fun supportRow(title:String,detail:String,action:()->Unit) { settingRow(group ?: root,title,detail,action) }
                fun supportMenu(title:String,entries:List<Triple<String,String,()->Unit>>) {
                    val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(4),dp(12),dp(12)) }
                    val card=panel(body).apply { setPadding(dp(12),dp(4),dp(12),dp(4)) }
                    val dialog=BridgeDialogBuilder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
                    entries.forEach { (name,detail,action) -> settingRow(card,name,detail) { dialog.dismiss(); action() } }
                    dialog.show()
                }
                heading("App & support")
                supportRow("App updates","Check for a newer version") { checkUpdates() }
                supportRow("Send feedback","Report a problem or suggest an improvement") { betaFeedback() }
                heading("Tracking status")
                supportRow("Tracking health","Permissions, sampling, battery and review status") { trackingHealth() }
                supportRow("Companion status","Active sessions, pending reviews and measured viewing") {
                    val summary=CompanionStatus.summarize(history.records(0))
                    BridgeDialogBuilder(this).setTitle("Companion status").setMessage("Active sessions: "+summary.active+"\nAwaiting review: "+summary.pending+"\nSampled time: "+durationText(summary.watchedMs)).setPositiveButton("Close",null).show()
                }
                heading("Advanced tools")
                supportRow("Database & encryption","Integrity checks and encryption migration readiness") {
                    supportMenu("Database & encryption",listOf(
                        Triple("History database integrity","Check stored viewing records") { checkHistoryIntegrity() },
                        Triple("Encryption readiness","Check migration requirements") { encryptionReadiness() }
                    ))
                }
                supportRow("Diagnostics","Live recognition, recording, technical status and cleanup") {
                    supportMenu("Diagnostics",listOf(
                        Triple("Live recognition","Inspect current playback detection") { liveInspector() },
                        Triple("Diagnostic recorder","Open the diagnostic recording tool") { startActivity(Intent(this,DebugActivity::class.java)) },
                        Triple("Last crash report","View, copy or clear the saved crash report") { showCrashReport() },
                        Triple("Technical status","Refresh and view detailed diagnostic information") {
                            refresh()
                            val detail=TextView(this).apply { text=output.text; setTextIsSelectable(true); setPadding(dp(16),dp(12),dp(16),dp(12)); setTextColor(ink) }
                            BridgeDialogBuilder(this).setTitle("Technical status").setView(ScrollView(this).apply { addView(detail) }).setPositiveButton("Close",null).show()
                        },
                        Triple("Clear diagnostic data","Remove stored session snapshots and last error") {
                            BridgeDialogBuilder(this).setTitle("Clear diagnostic data?").setMessage("Removes diagnostic session snapshots and the last sampling error. Saved viewing history is unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_-> prefs.edit().remove("sessions").remove("last_nonempty_sessions").remove("sample_error").apply(); refresh() }.show()
                        }
                    ))
                }
            }
        }
    }
    private var crashDialogShowing=false
    private fun maybeShowCrashReport() {
        if(!isDestroyed && !isFinishing && (!prefs.getBoolean("privacy_lock",false) || unlocked) && CrashRecovery.pending(this)) showCrashReport()
    }
    private fun showCrashReport() {
        if(crashDialogShowing || isDestroyed || isFinishing || (prefs.getBoolean("privacy_lock",false) && !unlocked)) return
        val report=CrashRecovery.report(this)
        if(report==null) {
            CrashRecovery.acknowledge(this)
            BridgeDialogBuilder(this).setTitle("Crash report").setMessage("No saved crash report is available.").setPositiveButton("OK",null).show()
            return
        }
        crashDialogShowing=true
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(18),dp(8),dp(18),dp(12)) }
        body.addView(txt("TraktBridge stopped unexpectedly. Copy this report to help diagnose the issue. Nothing is sent automatically.",14,ink))
        body.addView(txt(report,12,muted).apply { setTextIsSelectable(true); typeface=android.graphics.Typeface.MONOSPACE; setPadding(0,dp(12),0,0) })
        val dialog=BridgeDialogBuilder(this).setTitle("Previous crash report").setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Later",null).setNeutralButton("Clear",null).setPositiveButton("Copy report",null).create()
        dialog.setOnDismissListener { crashDialogShowing=false; CrashRecovery.acknowledge(this) }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("TraktBridge crash report",report))
                Toast.makeText(this,"Crash report copied",Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                CrashRecovery.clear(this); dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun betaFeedback() {
        val field=EditText(this).apply { hint="Describe the issue and steps to reproduce"; minLines=4; maxLines=8 }
        val info=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
        BridgeDialogBuilder(this).setTitle("Beta feedback").setMessage("Review your text before sharing. No viewing history, account credentials or diagnostic logs are attached automatically.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Share") { _,_->
            val report="TraktBridge "+info+"\nAndroid "+android.os.Build.VERSION.SDK_INT+"\n"+field.text.toString().trim().take(4000)
            runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Share beta feedback")) }.onFailure { Toast.makeText(this,"No sharing app available",Toast.LENGTH_LONG).show() }
        }.show()
    }
    private fun checkUpdates() {
        lookup("Checking GitHub",{ done -> UpdateCheck.check(done) }) { latest:String ->
            val current=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
            val newer=runCatching { UpdateCheck.compare(latest,current)>0 }.getOrDefault(false)
            BridgeDialogBuilder(this).setTitle("App updates").setMessage("Installed: "+current+"\nRepository: "+latest+"\n"+if(newer) "Newer source version available. Download only a successful permanently signed build." else "No newer repository version detected.").setNegativeButton("Close",null).setPositiveButton("GitHub builds") { _,_->startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/Roybabu/TraktBridge/actions"))) }.show()
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
                BridgeDialogBuilder(this).setTitle("Import collection").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Preview") { _,_->
                    val parsed=runCatching { CollectionExchange.parse(field.text.toString()) }
                    parsed.fold({ incoming ->
                        BridgeDialogBuilder(this).setTitle("Import "+incoming.first+"?").setMessage(incoming.second.length().toString()+" titles. Existing collections will not be overwritten.").setNegativeButton("Cancel",null).setPositiveButton("Import") { _,_->
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
        status("YouTube player identification",prefs.getBoolean("title_optin",false))
        status("Device credential lock",prefs.getBoolean("privacy_lock",false))
        status("Screenshot protection",prefs.getBoolean("secure_screen",false))
        status("Daily encrypted backup",prefs.getBoolean("daily_backup",false))
        status("Automatic remote refresh",prefs.getString("auto_refresh_account","").orEmpty().isNotBlank())
        body.addView(txt("Local viewing SQLite is not encrypted. Downloaded Trakt account data, OAuth credentials and optional encrypted backups use device-backed or password-based encryption.",14,muted))
        body.addView(actionButton("Disable automatic remote refresh") { RemoteRefreshSchedule.disable(this); Toast.makeText(this,"Remote refresh disabled",Toast.LENGTH_SHORT).show() })
        body.addView(actionButton("Delete downloaded Trakt account data") {
            BridgeDialogBuilder(this).setTitle("Clear downloaded Trakt data?").setMessage("Stops automatic refresh and deletes the downloaded account library. Local viewing records and your Trakt account are unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_->RemoteRefreshSchedule.disable(this); TraktLibrary.clearCache(this) }.show()
        })
        BridgeDialogBuilder(this).setTitle("Privacy dashboard").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun privacyMode() {
        BridgeDialogBuilder(this).setTitle("Pause all playback tracking?").setMessage("YouTube playback sampling, player identification and device sharing stop. Existing history and already approved uploads are retained. Restart tracking manually in Settings.").setNegativeButton("Cancel",null).setPositiveButton("Pause tracking") { _,_->
            val edit=prefs.edit().putBoolean("enabled",false).putBoolean("device_sharing",false).putBoolean("device_controls",false).putBoolean("device_history",false).putBoolean("title_optin",false).remove("sessions").remove("last_nonempty_sessions")
            edit.remove("segment:"+YouTubePlayback.PACKAGE).remove("segment_time:"+YouTubePlayback.PACKAGE).apply(); YouTubePlayback.clear(); PlaybackListener.wake(); stopService(Intent(this,TrackingService::class.java)); recreate()
        }.show()
    }
    private fun configureGoal() {
        val field=EditText(this).apply { hint="Monthly sampled hours (0 disables goal)"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.getInt("monthly_goal",0).toString()) }
        BridgeDialogBuilder(this).setTitle("Optional viewing goal").setMessage("Track a personal time budget. There are no rewards or pressure to watch more.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
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
                BridgeDialogBuilder(this).setTitle("New smart watchlist").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_->
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
                BridgeDialogBuilder(this).setTitle(list.name).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Close",null).setNeutralButton("Delete list") { _,_->store.edit().remove(list.name).apply() }.show()
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
            BridgeDialogBuilder(this).setTitle(choices[index]).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun collectionProfiles() {
        val store=getSharedPreferences("collection_profiles",MODE_PRIVATE)
        val names=(store.getStringSet("names",emptySet()) ?: emptySet()).sorted()
        choose("Collection profiles",listOf("Default")+names+listOf("Create profile")) { index ->
            if(index==names.size+1) {
                val field=EditText(this).apply { hint="Profile label" }
                BridgeDialogBuilder(this).setTitle("New collection profile").setMessage("Only collections and personal notes are separated. Playback history and Trakt account remain shared on this device.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Create") { _,_->
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
        BridgeDialogBuilder(this).setTitle("Organize library").setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Cancel",null).setNeutralButton("Manage folders") { _,_->collections() }.setPositiveButton("Search") { _,_->
            val name=names[folder.selectedItemPosition]
            val keys=if(name.isBlank()) null else runCatching { val a=JSONArray(store.getString(name,"[]")); (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("key") }.toSet() }.getOrDefault(emptySet())
            val rows=LibraryOrganization.search(ViewingGroups.build(history.records(0)),q.text.toString(),tags[tag.selectedItemPosition],keys,tagsByKey,sorts[sort.selectedItemPosition])
            val results=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            results.addView(txt(rows.size.toString()+" titles • first 100 shown",14,muted))
            rows.take(100).forEach { titleCard(results,it,false) }
            BridgeDialogBuilder(this).setTitle("Library search results").setView(ScrollView(this).apply { addView(results) }).setPositiveButton("Close",null).show()
        }.show()
    }
    private fun collections() {
        val store=personalStore("collections")
        val names=store.all.keys.sorted()
        choose("Collections",names+"Create collection") { i ->
            if(i==names.size) {
                val field=EditText(this).apply { hint="Collection name" }
                BridgeDialogBuilder(this).setTitle("New collection").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Create") { _,_->
                    val name=LibraryOrganization.folder(field.text.toString())
                    if(name!=null && !store.contains(name) && store.all.size<200) store.edit().putString(name,"[]").apply()
                }.show()
            } else {
                val name=names[i]; val a=JSONArray(store.getString(name,"[]"))
                val rows=(0 until a.length()).map { a.getJSONObject(it) }
                choose(name,rows.map { it.optString("label") }) { j ->
                    BridgeDialogBuilder(this).setTitle(rows[j].optString("label")).setNegativeButton("Close",null).setPositiveButton("Remove from collection") { _,_->
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
            BridgeDialogBuilder(this).setTitle(entry.value.toString()).setNegativeButton("Close",null).setNeutralButton("Remove") { _,_->store.edit().remove(entry.key).apply(); favorites() }.setPositiveButton("Local history") { _,_->historyFilter=HistoryFilter(query=entry.value.toString().substringBefore(" • ")); showTab(1) }.show()
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
        for(check in PermissionDiagnostics.read(this)) box.addView(txt((if(check.ready) "Ready" else if(check.required) "Action needed" else "Optional / unavailable")+" • "+check.name+"\n"+check.detail,14,if(check.ready) ink else muted))
        step("Notification access",if(access) "Granted" else "Required for media-session detection.") { runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.onFailure { Toast.makeText(this,"This device does not expose the notification access settings screen.",Toast.LENGTH_LONG).show() } }
        step("Video app selection",(prefs.getStringSet("allowed",emptySet())?.size ?: 0).toString()+" selected. Choose only apps you want tracked.") { chooseInstalledApps() }
        step("Battery settings",BatteryCompatibility.guidance(android.os.Build.MANUFACTURER)+" Optional; no setting is changed automatically.") { runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        step("Start tracking","Enable background sampling after selecting apps and granting access.") { if(TrackingRecovery.listenerGranted(this) && !prefs.getStringSet("allowed",emptySet()).isNullOrEmpty()) { prefs.edit().putBoolean("enabled",true).apply(); startTracking(); if(settingsCategory=="tracking") refreshSettingsState("tracking") } else Toast.makeText(this,"Grant notification access and choose playback apps first.",Toast.LENGTH_LONG).show() }
        box.addView(txt("YouTube player identification requires separate Accessibility consent in Settings when the media session does not identify the player type. Trakt sign-in is not required for local tracking.",14,muted))
        BridgeDialogBuilder(this).setTitle("Set up TraktBridge").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Later",null).setPositiveButton("Done") { _,_->showTab(4) }.show()
    }
    private fun handleTrackingShortcut(intent:Intent) {
        if(intent.action!="com.traktbridge.app.OPEN_TRACKING") return
        intent.action=null
        showTab(4)
        BridgeDialogBuilder(this).setTitle("Start playback tracking?").setMessage("Track selected video apps in the background.").setNegativeButton("Cancel",null).setPositiveButton("Start") { _,_->prefs.edit().putBoolean("enabled",true).apply(); startTracking(); recreate() }.show()
    }
    private fun startTracking() {
        runCatching {
            startForegroundService(Intent(this,TrackingService::class.java))
            TrackingRecovery.request(this,"tracking started")
        }.onFailure { DebugTrace.failure("foreground",it); Toast.makeText(this,"Unable to start tracking",Toast.LENGTH_LONG).show() }
    }
    override fun onPause() { AutoMatchSuggestions.store(this).unregisterOnSharedPreferenceChangeListener(suggestionListener); super.onPause(); if(prefs.getBoolean("privacy_lock",false) && !privacyPrompt) { unlocked=false; if(::shell.isInitialized) shell.visibility=android.view.View.INVISIBLE } }
    override fun onResume() { super.onResume(); AutoMatchSuggestions.store(this).registerOnSharedPreferenceChangeListener(suggestionListener); if (::output.isInitialized) refresh(); if (::historyList.isInitialized) refreshHistory(); requirePrivacyUnlock(); recoverTrackingWhenVisible(); if(::settingsOverview.isInitialized) { updateSettingsStatus(); if(settingsCategory in setOf("devices","tracking","privacy")) refreshSettingsState(settingsCategory) } }
    private fun repairTracking() {
        if(prefs.getBoolean("enabled",false) && TrackingRecovery.listenerGranted(this)) {
            if(!TrackingService.running) startTracking() else TrackingRecovery.request(this,"manual repair")
        }
        BridgeDialogBuilder(this).setTitle("Repair tracking").setMessage("Android controls notification listener binding. If tracking remains disconnected, open Notification access, turn TraktBridge off and back on, then return here. This does not delete your history.").setNegativeButton("Close",null).setPositiveButton("Notification access"){_,_->startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}.show()
    }
    private fun recoverTrackingWhenVisible() {
        val permitted=!prefs.getBoolean("privacy_lock",false) || unlocked
        if(TrackingRecovery.shouldStart(prefs.getBoolean("enabled",false),permitted,TrackingService.running,TrackingRecovery.listenerGranted(this))) startTracking()
        else if(permitted && prefs.getBoolean("enabled",false) && !PlaybackListener.liveConnected()) TrackingRecovery.request(this,"app resumed")
    }
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
    override fun onDestroy() { bridgeModel.cancel(); if (::history.isInitialized) history.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("content_scroll",if(::contentPage.isInitialized) (if(tab==4) settingsPage else contentPage).scrollY else 0); outState.putBoolean("content_focus",tvMode && ::nav.isInitialized && !nav.hasFocus()); outState.putString("settings_category",settingsCategory); outState.putString("library_selection",librarySelection); outState.putString("library_category",libraryCategory); outState.putString("review_category",reviewCategory); outState.putInt("tab",tab); outState.putString("import_source",importSource); outState.putString("filter_q",historyFilter.query); outState.putString("filter_source",historyFilter.source); outState.putInt("filter_period",historyFilter.period); outState.putString("filter_type",historyFilter.type); outState.putString("filter_status",historyFilter.status); outState.putString("filter_sort",historyFilter.sort); outState.putInt("analytics_period",analyticsPeriod); outState.putString("analytics_source",analyticsSource); outState.putString("calendar_month",calendarMonth.toString()); super.onSaveInstanceState(outState) }
    private fun dp(n:Int)= (n*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color:Int,radius:Int=16)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun txt(value:String,size:Int=15,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size.toFloat()*UiAccessibility.readingScale(prefs.getInt("reading_size",0),tvMode); setTextColor(color); if(bold) setTypeface(null,android.graphics.Typeface.BOLD)
    }
    private fun actionButton(label:String,action:()->Unit)=Button(this).apply {
        text=label; contentDescription=label; isAllCaps=false; isFocusable=true; if(tvMode) { minWidth=dp(120); setOnFocusChangeListener { view,focused -> view.alpha=if(focused) 1f else 0.85f; view.scaleX=if(focused && !prefs.getBoolean("reduce_motion",false)) 1.03f else 1f; view.scaleY=if(focused && !prefs.getBoolean("reduce_motion",false)) 1.03f else 1f } }; setTextColor(ink); textSize=14f*UiAccessibility.readingScale(prefs.getInt("reading_size",0),tvMode); background=if(tvMode) TvFocus.background(this@MainActivity,BridgeDesign.action,violet) else rounded(BridgeDesign.action,12); minHeight=dp(adaptiveLayout.touch)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) }; setOnClickListener { BridgeHaptics.tap(this); action() }
    }
    private fun panel(parent:LinearLayout):LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; background=rounded(surface); setPadding(dp(16),dp(16),dp(16),dp(16))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun section(parent:LinearLayout,title:String) { parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeSection(violet,title) } },LinearLayout.LayoutParams(-1,-2)) }
    override fun onKeyDown(keyCode:Int,event:android.view.KeyEvent):Boolean {
        if(tvMode && ::nav.isInitialized && keyCode==android.view.KeyEvent.KEYCODE_DPAD_RIGHT && nav.hasFocus()) {
            val page=if(tab==4) settingsPage else contentPage
            if(TvFocus.firstContent(page)) return true
        }
        if(tvMode && keyCode==android.view.KeyEvent.KEYCODE_MENU && ::nav.isInitialized) {
            nav.getChildAt(tab)?.requestFocus()
            return true
        }
        return super.onKeyDown(keyCode,event)
    }
    private fun showTab(index:Int) {
        if(index !in 0..4) return
        if(tab!=index) { restoreContentFocus=false; restoreScrollY=0 }
        tab=index; CrashRecovery.markScreen(listOf("Home","Library","Review","Activity","Settings")[index]); bridgeModel.select(index); settingsPage.visibility=if(index==4) android.view.View.VISIBLE else android.view.View.GONE
        contentPage.visibility=if(index==4 || index==1) android.view.View.GONE else android.view.View.VISIBLE
        libraryHost.visibility=if(index==1) android.view.View.VISIBLE else android.view.View.GONE
        if(tvMode) for(i in 0 until nav.childCount) (nav.getChildAt(i) as TextView).apply { setTextColor(if(i==index) violet else muted); background=TvFocus.background(this@MainActivity,if(i==index) BridgeDesign.action else surface,violet,i==index); isSelected=i==index }
        clearHistoryProgress(); refreshHistory(navigation=true); contentPage.scrollTo(0,0)
        if(index==4 && restoreScrollY>0) { val y=restoreScrollY; restoreScrollY=0; settingsPage.post { if(tab==4) settingsPage.scrollTo(0,y) } }
        if(tvMode && index==4 && (!nav.hasFocus() || restoreContentFocus)) { settingsPage.post { if(tab==4) TvFocus.firstContent(settingsPage) }; restoreContentFocus=false }
    }
    private fun emptyState(parent:LinearLayout,title:String,detail:String,label:String,action:()->Unit) {
        parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeEmptyState(violet,title,detail,label,action) } })
    }
    private var historyLoadPanel:LinearLayout?=null
    private var historyLoadLabel:TextView?=null
    private var historyLoadBar:ProgressBar?=null
    private var renderedHistoryTab=-1
    private fun showHistoryProgress(progress:HistoryLoadProgress) {
        if(tab==4 || isDestroyed || isFinishing) return
        val host=if(tab==1) libraryHost else dashboard
        if(historyLoadPanel?.parent!=host || historyLoadBar==null) {
            clearHistoryProgress()
            val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
            val label=txt("",13,muted)
            val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000; progressTintList=android.content.res.ColorStateList.valueOf(violet); indeterminateTintList=android.content.res.ColorStateList.valueOf(violet) }
            panel.addView(label); panel.addView(bar,LinearLayout.LayoutParams(-1,dp(8)))
            host.addView(panel,0); historyLoadPanel=panel; historyLoadLabel=label; historyLoadBar=bar
        }
        val known=progress.total>0
        historyLoadLabel?.text=progress.stage+if(known) " • "+progress.completed+" / "+progress.total+" records ("+(progress.completed.toLong()*100/progress.total)+"%)" else "…"
        historyLoadBar?.isIndeterminate=!known
        if(known) historyLoadBar?.progress=(progress.completed.toLong()*1000/progress.total).toInt().coerceIn(0,1000)
    }
    private fun clearHistoryProgress() {
        historyLoadPanel?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        historyLoadPanel=null; historyLoadLabel=null; historyLoadBar=null
    }
    private var historyProgressEpoch=0L
    private val historyProgressHandler=android.os.Handler(android.os.Looper.getMainLooper())
    private fun refreshHistory(navigation:Boolean=false) {
        if(!::dashboard.isInitialized || tab==4 || isFinishing || isDestroyed) return
        val selectedTab=tab
        val epoch=++historyProgressEpoch
        var completed=false
        var pendingProgress:HistoryLoadProgress?=null
        var progressVisible=false
        historyProgressHandler.postDelayed({ if(!completed && epoch==historyProgressEpoch && tab==selectedTab && !isDestroyed && !isFinishing) { progressVisible=true; pendingProgress?.let { showHistoryProgress(it) } } },700)
        if(renderedHistoryTab!=selectedTab) { dashboard.removeAllViews(); clearHistoryProgress() }
        if(tvMode && contentPage.hasFocus()) restoreContentFocus=true
        bridgeModel.load({ result,finished ->
            if(!isDestroyed && !isFinishing && tab==selectedTab && epoch==historyProgressEpoch) {
                if(finished) completed=true
                result.fold({ snapshot ->
                    val y=if(restoreScrollY>0) restoreScrollY else if(renderedHistoryTab==selectedTab) contentPage.scrollY else 0
                    val drawn=runCatching { renderHistory(snapshot.records,snapshot.groups,snapshot.unified) }
                    if(drawn.isSuccess) {
                        renderedHistoryTab=selectedTab
                        clearHistoryProgress()
                        if(!finished && progressVisible) pendingProgress?.let { showHistoryProgress(it) }
                        restoreScrollY=0
                        if(y>0) contentPage.post { if(tab==selectedTab) contentPage.scrollTo(0,y) }
                        if(tvMode && restoreContentFocus) { restoreContentFocus=false; contentPage.post { if(tab==selectedTab) TvFocus.firstContent(contentPage) } }
                    } else {
                        clearHistoryProgress()
                        emptyState(if(tab==1) libraryHost else dashboard,"Unable to display history","Existing viewing records are unchanged.","Retry"){refreshHistory()}
                    }
                }, {
                    clearHistoryProgress()
                    val warning=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)) }
                    warning.addView(txt("History loading interrupted",16,ink,true))
                    warning.addView(txt("Some history may be unavailable. Existing records have not been cleared.",13,muted))
                    warning.addView(actionButton("Retry loading"){refreshHistory()})
                    (if(tab==1) libraryHost else dashboard).addView(warning,0); historyLoadPanel=warning
                })
            }
        },{ progress -> if(tab==selectedTab && epoch==historyProgressEpoch && !isDestroyed && !isFinishing) { pendingProgress=progress; completed=false; if(progressVisible) showHistoryProgress(progress) } },navigation=navigation)
    }

    private fun customizeDashboard() {
        val keys=DashboardPreferences.order(prefs.getString("dashboard_order","").orEmpty())
        val hidden=DashboardPreferences.visible(prefs.getString("dashboard_hidden","").orEmpty())
        modernDetailWindow { close -> ModernDashboardSettings(violet,keys,hidden,DashboardPreferences.startTab(prefs.getInt("startup_tab",0)),DashboardPreferences.widgetMode(prefs.getString("widget_mode","private").orEmpty()),{ order,disabled,start,widget ->
            prefs.edit().putString("dashboard_order",order.joinToString(",")).putString("dashboard_hidden",disabled.joinToString(",")).putInt("startup_tab",start).putString("widget_mode",widget).apply()
            val manager=android.appwidget.AppWidgetManager.getInstance(this)
            val ids=manager.getAppWidgetIds(android.content.ComponentName(this,ViewingWidget::class.java))
            if(ids.isNotEmpty()) sendBroadcast(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(android.content.ComponentName(this,ViewingWidget::class.java)).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS,ids))
            close(); refreshHistory()
        },close) }
    }
    private var unifiedRows:List<WatchRecord> = emptyList()
    private fun renderHistory(records:List<WatchRecord>,groups:List<ViewingTitle>,unified:List<WatchRecord> = records) {
        unifiedRows=unified
        val allGroups=if(tab==0) ViewingGroups.build(unified) else groups
        dashboard.removeAllViews()
        val pending=allGroups.count { it.queued || (it.pending && !it.rewatching) }
        if(tab in 0..2) requestReviewSuggestions(records)
        when(tab) {
            0 -> renderModernHome(records,allGroups,pending)
            1 -> renderPosterLibrary(unified)
            2 -> renderModernReview(ViewingGroups.build(unified))
            3 -> renderModernActivity(unified)
        }
    }
    private fun modernContent(content:@androidx.compose.runtime.Composable ()->Unit) {
        dashboard.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent(content) })
    }
    private fun renderModernHome(records:List<WatchRecord>,groups:List<ViewingTitle>,pending:Int) {
        val age=System.currentTimeMillis()-prefs.getLong("last_sample",0)
        val live=TrackingRecovery.status(this)=="Tracking active"
        modernContent { ModernPageHeader(violet,"Home",TrackingRecovery.status(this),onMenu={ modernActionDialog("Home options","Manage your dashboard",listOf("Customize dashboard","Refresh library")) { i -> if(i==0) customizeDashboard() else refreshHistory() } }) }
        if(!live && prefs.getBoolean("enabled",false)) modernContent { ModernActionRow(violet,"Repair tracking",TrackingRecovery.status(this)){repairTracking()} }
        val hidden=DashboardPreferences.visible(prefs.getString("dashboard_hidden","").orEmpty())
        val active=groups.filter { it.active }
        val continuing=SeriesLibrary.continueWatching(groups)
        val continuingKeys=continuing.map { it.key }.toSet()
        val recent=groups.filter { !it.active && it.key !in continuingKeys }.take(5)
        for(key in DashboardPreferences.order(prefs.getString("dashboard_order","").orEmpty())) when(key) {
            "now" -> {
        if("now" !in hidden) {
            modernContent { ModernActionRow(violet,"Now playing",active.firstOrNull()?.representative?.title ?: "View playback on connected devices"){ nowPlaying() } }
        }

            }
            "continue" -> {
        if("continue" !in hidden && continuing.isNotEmpty()) {
            section(dashboard,"Continue watching")
            val items=continueCards(groups,limit=7)
            modernContent { PosterLibraryShelf(violet,items) }
        }
        if("continue" !in hidden) {
            val next=SeriesLibrary.build(groups).filter { it.completed>0 }.take(5)
            if(next.isNotEmpty()) {
                section(dashboard,"Up next")
                next.forEach { series -> modernContent { ModernActionRow(violet,series.title,"Check next unwatched episode"){ remoteSeriesProgress(series.id,series.title) } } }
            }
        }

            }
            "metrics" -> {
        if("metrics" !in hidden) modernContent { ModernMetricTiles(violet,listOf("This week" to durationText(ViewingAnalytics.filter(records,HistoryFilter(period=7)).sumOf { it.watched }),"Titles tracked" to groups.size.toString(),"To review" to pending.toString())) }

            }
            "review" -> {
        if("review" !in hidden) modernContent { ModernActionRow(violet,"Review your viewing",pending.toString()+" records awaiting action"){ showTab(2) } }

            }
            "recent" -> {
        if("recent" !in hidden && recent.isNotEmpty()) {
            section(dashboard,"Recently watched")
            recent.forEach { g -> val item=groupPoster(g); modernContent { ModernActionRow(violet,item.title,if(g.confirmed) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(g.latest.ended))+" · "+appLabel(g.latest.source) else item.subtitle){ showSessions(g) } } }
        }

            }
            "discover" -> {
        if("discover" !in hidden) {
            section(dashboard,"Explore")
            listOf(UiAction("For you"){ recommendFromHistory() },UiAction("Upcoming episodes"){ upcomingEpisodes() },UiAction("Trending titles"){ discover() },UiAction("Anime"){ animeSearch() }).chunked(2).forEach { batch ->
                modernContent { BridgeTheme(violet) { androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxWidth(),horizontalArrangement=androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) { batch.forEach { item -> androidx.compose.material3.OutlinedButton(onClick=item.run,modifier=androidx.compose.ui.Modifier.weight(1f)) { androidx.compose.material3.Text(item.label) } } } } }
            }
        }

            }
            "goal" -> {
        if("goal" !in hidden && prefs.getBoolean("show_monthly_goal",true)) monthlyGoalCard(dashboard,records)
            }
        }
    }
    private var reviewCategory="match"
    private fun reviewBucket(g:ViewingTitle):String {
        if(g.queued) return "sync"
        if(g.rewatching) return "rewatch"
        if(g.representative.traktId<=0) return "match"
        if(g.positionSession?.let { completionAssessment(it).suggested }==true) return "sync"
        return "progress"
    }
    private fun renderModernReview(groups:List<ViewingTitle>) {
        val items=groups.filter { it.queued || it.pending || it.rewatching }
        modernContent { ModernPageHeader(violet,"Review",items.size.toString()+" awaiting action",onMenu={ choose("Review options",listOf("Refresh","Log a watch manually","Retry approved uploads")) { i -> when(i){0->refreshHistory();1->manualLog();2->{HistorySync.schedule(this);refreshHistory()}} } }) }
        val categories=listOf("match" to "Needs match","progress" to "In progress","rewatch" to "Rewatching","sync" to "Ready / sync")
        modernContent { ModernCategories(violet,categories.map { (key,label)->key to (label+" ("+items.count { reviewBucket(it)==key }+")") },reviewCategory) { reviewCategory=it; refreshHistory() } }
        val visible=items.filter { reviewBucket(it)==reviewCategory }
        if(visible.isEmpty()) dashboard.addView(txt("Nothing in this category.",15,muted))
        visible.take(historyLimit).forEach { g ->
            val r=g.representative
            val state=if(AutoMatchSuggestions.eligible(r)) AutoMatchSuggestions.read(this,r) else null
            val candidate=state?.matches?.firstOrNull()?.item
            val original=groupPoster(g)
            val item=if(candidate!=null) original.copy(title=candidate.title.substringBefore(" — "),subtitle=candidate.label().substringAfter(" — ",candidate.label())+" · "+appLabel(r.source),status="Suggested match",type=if(candidate.type=="movie") "movie" else "show",id=if(candidate.type=="episode") candidate.showId else candidate.id) else original.copy(status=if(g.queued) "Awaiting sync" else if(g.rewatching) "Rewatching" else if(r.traktId>0) "Match verified" else "Needs match")
            val actions=if(state!=null) suggestionActions(r,state) else emptyList()
            val primary=if(candidate!=null) actions.firstOrNull() else if(g.queued) UiAction("View sync status"){showSessions(g)} else if(r.traktId>0 && reviewBucket(g)=="sync") UiAction("Confirm watched"){showSessions(g)} else null
            val secondary=actions.firstOrNull { it.label=="No, incorrect" }
            val manual=if(r.traktId<=0) UiAction("Search manually"){identify(r)} else null
            val evidence=when { candidate!=null->state?.matches?.firstOrNull()?.reason.orEmpty()+" · Original: "+r.title; state?.status in setOf("waiting","searching")->"Searching Trakt automatically…"; state?.status=="error"->"Catalogue unavailable; will retry"; state?.status=="rejected"->"Suggestion rejected"; r.traktId<=0->"No reliable Trakt match found"; else->durationText(g.sampledMs)+" sampled · "+original.subtitle }
            modernContent { ModernReviewCard(violet,item,evidence,primary,secondary,manual) {
                val options=titleEntry(g,true).actions.filter { it.label !in setOf("Yes, correct","No, incorrect","Search manually","Open playback app","Open player") || item.play==null }
                val menu=options+if(r.status in setOf("pending","reviewed")) listOf(UiAction("Delete viewing record"){deleteFromReview(r)}) else emptyList()
                modernActionDialog("Viewing record",item.title,menu.map { it.label }) { menu[it].run() }
            } }
        }
        if(visible.size>historyLimit && historyLimit<1000) dashboard.addView(actionButton("Show more"){historyLimit=MemoryPolicy.nextPage(historyLimit,MemoryPolicy.pageSize(lowRam));refreshHistory()})
    }
    private fun deleteFromReview(r:WatchRecord) {
        BridgeDialogBuilder(this).setTitle("Delete this local record?").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->
            if(runCatching { history.trash(r.id) }.getOrDefault(false)) { AutoMatchSuggestions.remove(this,r.id); ViewingNotifications.cancel(this,r.id); refreshHistory() }
            else Toast.makeText(this,"Record could not be deleted",Toast.LENGTH_LONG).show()
        }.show()
    }
    private fun renderModernActivity(records:List<WatchRecord>) {
        val scoped=ViewingAnalytics.filter(records,HistoryFilter(period=analyticsPeriod,source=analyticsSource))
        val groups=ViewingGroups.build(scoped)
        val historical=scoped.filter { it.status=="synced" }
        val activeDays=scoped.filter { it.watched>0 || it.status=="synced" }.map { ViewingAnalytics.day(it.ended) }.distinct().size
        val total=scoped.sumOf { it.watched.coerceAtLeast(0) }
        val daily=scoped.filter { it.watched>0 }.groupBy { ViewingAnalytics.day(it.ended) }.mapValues { (_,rows)->rows.sumOf { it.watched } }
        val today=java.time.LocalDate.now()
        val start=if(analyticsPeriod>0) today.minusDays(analyticsPeriod.toLong()-1) else scoped.minOfOrNull { ViewingAnalytics.day(it.ended) } ?: today
        val monthly=analyticsPeriod==365 || analyticsPeriod==0
        val dates=if(monthly) {
            val first=java.time.YearMonth.from(start)
            val count=(java.time.temporal.ChronoUnit.MONTHS.between(first,java.time.YearMonth.from(today))+1).coerceIn(1,120).toInt()
            (0 until count).map { java.time.YearMonth.from(today).minusMonths((count-1-it).toLong()).atDay(1) }
        } else (0 until analyticsPeriod).map { start.plusDays(it.toLong()) }
        val values=dates.map { date -> if(monthly) daily.entries.filter { java.time.YearMonth.from(it.key)==java.time.YearMonth.from(date) }.sumOf { it.value } else daily[date] ?: 0L }
        modernContent { ModernPageHeader(violet,"Activity",analyticsSource.takeIf { it.isNotBlank() }?.let { appLabel(it) } ?: "All platforms",onFilter={
            val sources=records.map { it.source }.distinct().sortedBy { appLabel(it) }
            choose("Filter by platform",listOf("All platforms")+sources.map { appLabel(it) }){ i -> analyticsSource=if(i==0) "" else sources[i-1];refreshHistory() }
        },onMenu={choose("Activity options",listOf("Subscription costs","Streaming service dashboard","Viewing journal and yearly recap","Playback diagnostics","Refresh")){ i->when(i){0->subscriptions();1->serviceDashboard();2->viewingJournal();3->BridgeDialogBuilder(this).setTitle("Playback diagnostics").setMessage("Paused: "+durationText(scoped.sumOf{it.pausedMs})+"\nBuffering: "+durationText(scoped.sumOf{it.bufferingMs})+"\nSeeks: "+scoped.sumOf{it.seeks}+"\nUncertain: "+durationText(scoped.sumOf{it.uncertainMs})).setPositiveButton("Close",null).show();4->refreshHistory()}}}) }
        modernContent { ModernCategories(violet,listOf("7" to "Week","30" to "Month","365" to "Year","0" to "All time"),analyticsPeriod.toString()){ analyticsPeriod=it.toInt();refreshHistory() } }
        modernContent { ModernWatchChart(violet,durationText(total),dates.map { if(monthly) it.format(java.time.format.DateTimeFormatter.ofPattern("MMM yy")) else it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM")) },values){} }
        val days=(java.time.temporal.ChronoUnit.DAYS.between(start,today)+1).coerceAtLeast(1)
        modernContent { ModernMetricTiles(violet,listOf("Movies synced" to groups.count { it.confirmed && it.representative.matchType=="movie" }.toString(),"Episodes synced" to groups.count { it.confirmed && it.representative.matchType=="episode" }.toString(),"Active days" to activeDays.toString(),"Daily average" to durationText(total/days))) }
        modernContent { ModernMetricTiles(violet,listOf("Movie watches" to historical.count { it.matchType=="movie" }.toString(),"Episode watches" to historical.count { it.matchType=="episode" }.toString(),"Repeat watches in period" to groups.sumOf { (it.completedWatches.size-1).coerceAtLeast(0) }.toString())) }
        section(dashboard,"Watch time by platform")
        ViewingAnalytics.platforms(scoped).filter { it.sampledMs>0 }.forEach { platform ->
            modernContent { ModernActionRow(violet,appLabel(platform.source),durationText(platform.sampledMs)+" · "+(if(total>0) platform.sampledMs*100/total else 0)+"% of sampled time"){analyticsSource=platform.source;refreshHistory()} }
        }
        if(daily.isNotEmpty()) {
            var streak=0;var longest=0;var previous:java.time.LocalDate?=null
            daily.keys.sorted().forEach { day -> streak=if(previous?.plusDays(1)==day) streak+1 else 1;longest=maxOf(longest,streak);previous=day }
            val busiest=daily.entries.groupBy { it.key.dayOfWeek }.maxByOrNull { (_,rows)->rows.sumOf { it.value } }?.key?.name.orEmpty()
            section(dashboard,"Viewing insights")
            modernContent { ModernMetricTiles(violet,listOf("Longest streak" to "$longest days","Most active day" to busiest.lowercase().replaceFirstChar { it.uppercase() })) }
        }
        section(dashboard,"Viewing calendar")
        calendarView(dashboard,if(analyticsSource.isBlank()) records else records.filter { it.source==analyticsSource })
        dashboard.addView(txt("Calendar and watch counts include Trakt history. Sampled watch time excludes historical watches without measured duration.",12,muted))
    }
    private fun streamingAvailability(type:String,id:Long,title:String) {
        val initial=prefs.getString("availability_region",java.util.Locale.getDefault().country).orEmpty()
        val field=EditText(this).apply { hint="Country code, e.g. IN, AE, US"; setSingleLine(true); setText(initial) }
        BridgeDialogBuilder(this).setTitle("Where to watch").setMessage("Choose your country. JustWatch searches by title; Trakt opens the exact catalogue ID. Availability, pricing and subscriptions must be verified on the destination site.").setView(field).setNegativeButton("Cancel",null).setNeutralButton("Trakt") { _,_->openTraktWatchPage(type,id) }.setPositiveButton("Search JustWatch") { _,_->
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
            BridgeDialogBuilder(this).setTitle("Open exact provider title").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Open") { _,_->
                val url=PlaybackShortcuts.providerLink(source,field.text.toString().trim())
                if(url==null) Toast.makeText(this,"Invalid provider ID",Toast.LENGTH_LONG).show()
                else runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }.onFailure { Toast.makeText(this,"No compatible app",Toast.LENGTH_LONG).show() }
            }.show()
        }
    }
    private fun openPlaybackApp(sources:List<String>) {
        val installed=sources.distinct().mapNotNull { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let { pkg to it } }
        if(installed.isEmpty()) { Toast.makeText(this,"No recorded playback app is installed or launchable",Toast.LENGTH_LONG).show(); return }
        fun launch(index:Int) { runCatching { startActivity(installed[index].second) }.onFailure { Toast.makeText(this,"Unable to open player",Toast.LENGTH_LONG).show() } }
        if(installed.size==1) launch(0) else choose("Open recorded playback app",installed.map { appLabel(it.first) }) { i -> launch(i) }
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
            BridgeDialogBuilder(this).setTitle("Upcoming episodes").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun externalIdentity(record:WatchRecord) {
        val field=EditText(this).apply { hint="IMDb tt1234567 or TMDB numeric ID"; setSingleLine(true) }
        choose("External catalogue identifier",listOf("IMDb","TMDB")) { index ->
            val namespace=if(index==0) "imdb" else "tmdb"
            BridgeDialogBuilder(this).setTitle("Enter "+namespace.uppercase()+" ID").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->
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
        BridgeDialogBuilder(this).setTitle(label).setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save privately") { _,_->
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
                            BridgeDialogBuilder(this).setTitle(entry.title).setMessage(if(watched) "Synced completion in local history" else "No synced completion in local history").setNegativeButton("Close",null).setPositiveButton("Details") { _,_->titleDetails("movie",exact.id,exact.title) }.show()
                        }
                    }
                })
            }
            BridgeDialogBuilder(this).setTitle(guide.name).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun syncRecoveryStatus() {
        val rows=history.records(0)
        val counts=listOf("queued","uncertain","blocked").joinToString("\n") { status -> status+": "+rows.count { it.status==status } }
        BridgeDialogBuilder(this).setTitle("Offline sync recovery").setMessage(counts+"\n\nQueued records retry when online. Uncertain uploads are checked against remote history without resending. Blocked records require account verification or manual retry.").setNegativeButton("Close",null).setPositiveButton("Check approved queue") { _,_->HistorySync.schedule(this); showTab(2) }.show()
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
        val battery=BatteryCompatibility.status(this)
        body.addView(txt("Background restricted: "+battery.restricted+" • Battery saver: "+battery.saver+"\n"+BatteryCompatibility.guidance(battery.manufacturer),14,muted))
        body.addView(txt("Last sampling error: "+prefs.getString("sample_error","none"),14,muted))
        body.addView(actionButton("Open guided setup") { setupGuide() })
        body.addView(actionButton("Inspect live sessions") { liveInspector() })
        body.addView(actionButton("Sync recovery status") { syncRecoveryStatus() })
        val groups=ViewingGroups.build(history.records(0)).filter { it.queued || it.pending }
        section(body,"Pending review and sync • "+groups.size)
        groups.take(20).forEach { titleCard(body,it,true) }
        BridgeDialogBuilder(this).setTitle("Tracking health").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun recentlyDeleted() {
        val rows=history.trashed()
        choose("Recently deleted",rows.map { it.matchLabel.ifBlank { it.title.ifBlank { "Unidentified video" } } }) { i ->
            BridgeDialogBuilder(this).setTitle("Restore viewing record?").setMessage("Restore for local review. Nothing will be uploaded.").setNegativeButton("Cancel",null).setPositiveButton("Restore") { _,_->history.restoreTrash(rows[i].id); refreshHistory() }.show()
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
        BridgeDialogBuilder(this).setTitle("Viewing plan").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun viewingPlanner() {
        val candidates=SeriesLibrary.continueWatching(ViewingGroups.build(unifiedRows.ifEmpty { history.records(0) })).filter { it.positionMs>=0 && it.durationMs>0 && it.remainingMs>0 }
        modernDetailWindow { close ->
            ModernViewingPlanner(violet,candidates.map { PlannerEntry(groupPoster(it),it.remainingMs) },{ budget ->
                close(); multiWatchPlan(budget,candidates)
            },{ close(); lookup("Loading watchlist",{ done -> TraktLibrary.page(this,"watchlist",1,done) }) { page:RemotePage ->
                val movie=page.items.filter { it.type=="movie" }.randomOrNull()
                if(movie==null) Toast.makeText(this,"No movies on the first watchlist page",Toast.LENGTH_LONG).show()
                else titleDetails("movie",movie.id,movie.title)
            } },close)
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
                BridgeDialogBuilder(this).setTitle(appLabel(pkg)).setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
                    val amount=price.text.toString().toDoubleOrNull()
                    val date=renewal.text.toString().trim()
                    if(amount==null || !amount.isFinite() || amount !in 0.0..1000000.0 || (date.isNotBlank() && runCatching { java.time.LocalDate.parse(date) }.isFailure)) Toast.makeText(this,"Invalid amount or date",Toast.LENGTH_LONG).show()
                    else { store.edit().putString(pkg,org.json.JSONObject().put("cost",amount).put("renewal",date).toString()).apply(); Toast.makeText(this,"Saved. Reopen dashboard to refresh.",Toast.LENGTH_SHORT).show() }
                }.show()
            })
        }
        body.addView(txt("Total monthly cost: ₹"+String.format(java.util.Locale.US,"%.2f",total),20,ink,true))
        body.addView(txt("Amounts are entered manually. Sampled hours can be incomplete; renewal dates are not billing confirmations.",13,muted))
        BridgeDialogBuilder(this).setTitle("Subscription manager").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
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
            BridgeDialogBuilder(this).setTitle(appLabel(pkg)).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
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
        BridgeDialogBuilder(this).setTitle("Automation shortcuts").setView(body).setPositiveButton("Close",null).show()
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
        modernDetailWindow { close -> ModernAssistant(violet,{ command ->
            close()
            val q=LocalAssistant.parse(command)
            when(q.action) {
                "subscriptions"->subscriptions()
                "recommend"->recommendFromHistory()
                "history"->{ historyFilter=HistoryFilter(period=q.days); historyLimit=100; showTab(1) }
                "unfinished","budget"->{
                    val rows=SeriesLibrary.continueWatching(ViewingGroups.build(unifiedRows.ifEmpty { history.records(0) })).filter { q.action!="budget" || it.remainingMs in 1..(q.minutes*60000L) }
                    modernDetailWindow { done -> ModernPlannerResults(violet,"Your viewing matches",rows.take(30).map { PlannerEntry(groupPoster(it),it.remainingMs) },done) }
                }
                else->searchTitles(command)
            }
        },close) }
    }
    private fun universalSearch() {
        modernDetailWindow { close -> ModernSearchEntry(violet,{ query -> close(); searchTitles(query) },{ close(); localAssistant() },close) }
    }
    private fun searchTitles(query:String) {
        val q=query.trim().take(200)
        if(q.isBlank()) return
        val local=ViewingGroups.build(ViewingAnalytics.filter(unifiedRows.ifEmpty { history.records(0) },HistoryFilter(query=q)))
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
        BridgeDialogBuilder(this).setTitle("Search: "+q).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun manualLog() {
        val field=EditText(this).apply { hint="Movie or episode title"; setSingleLine(true) }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(8)); addView(field); addView(txt("Choose the date and time you finished. No measured watch time will be added.",13,muted)) }
        BridgeDialogBuilder(this).setTitle("Manual watch log").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Choose date") { _,_->
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
        val rows=unifiedRows.ifEmpty { history.records(0) }.filter { it.status!="active" }
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
            BridgeDialogBuilder(this).setTitle("Viewing journal").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
        }
    }
    private fun confirmDeviceRevocation(peer:DevicePairing.Peer?) {
        BridgeDialogBuilder(this).setTitle(if(peer==null) "Revoke all devices?" else "Revoke "+peer.label+"?")
            .setMessage("Remove playback, history and control access. Local viewing history and Trakt approvals remain unchanged. Pair again with a fresh invitation to restore access.")
            .setNegativeButton("Cancel",null).setPositiveButton("Revoke") { _,_->
                runCatching {
                    if(peer==null) DevicePairing.revokeAll(this) else DevicePairing.revoke(this,peer.id)
                    if(prefs.getBoolean("enabled",false)) startTracking()
                    Thread { runCatching { DeviceHistory.clear(applicationContext,peer?.id) } }.start()
                }.fold({ refreshDeviceSettings(); Toast.makeText(this,"Device access revoked",Toast.LENGTH_LONG).show() },{ Toast.makeText(this,"Unable to revoke device",Toast.LENGTH_LONG).show() })
            }.show()
    }
    private fun managePairedDevices() {
        val peers=DevicePairing.peers(this).sortedBy { it.label }
        if(peers.isEmpty()) { refreshDeviceSettings(); return }
        choose("Manage paired devices",peers.map { it.label+" • "+it.id.take(12) }+"Revoke all devices") { i -> confirmDeviceRevocation(peers.getOrNull(i)) }
    }
    private fun showDeviceHistory() {
        var rows by androidx.compose.runtime.mutableStateOf<List<HistoryUiRow>>(emptyList())
        var loading by androidx.compose.runtime.mutableStateOf(true)
        var error by androidx.compose.runtime.mutableStateOf(false)
        val body=ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { BridgeDeviceHistory(violet,rows,loading,error) }
        }
        val dialog=BridgeDialogBuilder(this).setTitle("Cross-device history").setView(body).setPositiveButton("Close",null).create()
        dialog.show()
        if(wide) dialog.window?.setLayout((resources.displayMetrics.widthPixels*0.76).toInt(),-2)
        Thread {
            val result=runCatching { DeviceHistory.visible(applicationContext) }
            val labels=DevicePairing.peers(applicationContext).associate { it.id to it.label }
            runOnUiThread {
                if(!dialog.isShowing || isDestroyed || isFinishing) return@runOnUiThread
                result.fold({ items -> rows=items.map { item ->
                    val e=item.event
                    HistoryUiRow(item.peer+":"+e.id,e.title,e.detail,(labels[item.peer] ?: "Paired device")+" • "+appLabel(e.source),java.text.DateFormat.getDateTimeInstance().format(java.util.Date(e.ended))+" • "+durationText(e.watched)+" watched")
                } },{ error=true })
                loading=false
            }
        }.start()
    }
    private fun configureDeviceRelay() {
        val current=RelaySettings.load(this)
        val host=EditText(this).apply { hint="Relay hostname"; setText(current?.host.orEmpty()); setSingleLine(true) }
        val port=EditText(this).apply { hint="Port"; inputType=android.text.InputType.TYPE_CLASS_NUMBER; setText((current?.port ?: 8765).toString()) }
        val secret=EditText(this).apply { hint="43-character deployment secret"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; setSingleLine(true) }
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; addView(host); addView(port); addView(secret) }
        BridgeDialogBuilder(this).setTitle("Optional self-hosted cloud relay")
            .setMessage("Use only a relay you trust. Playback is encrypted end-to-end between paired devices. The relay can see connection metadata. Mobile data may be used. No hosted service is configured by default.")
            .setView(content).setNegativeButton("Cancel",null).setNeutralButton("Disable") { _,_->
                runCatching { RelaySettings.disable(this); RemotePlayback.clear(); if(prefs.getBoolean("enabled",false)) startTracking() }
            }.setPositiveButton("Save and enable") { _,_->
                val config=RelaySettings.Config(host.text.toString().trim(),port.text.toString().toIntOrNull() ?: 0,secret.text.toString().trim().ifBlank { current?.secret.orEmpty() })
                runCatching { RelaySettings.save(this,config); if(prefs.getBoolean("enabled",false)) startTracking() }
                    .fold({ Toast.makeText(this,"Optional relay configured",Toast.LENGTH_LONG).show() },{ Toast.makeText(this,"Invalid relay settings or secure storage unavailable",Toast.LENGTH_LONG).show() })
            }.show()
    }
    private fun continueOnThisDevice() {
        val choices=RemotePlayback.visible(this).flatMap { entry ->
            (0 until entry.rows.length()).mapNotNull { i -> entry.rows.optJSONObject(i)?.let { entry to it } }
        }
        if(choices.isEmpty()) { Toast.makeText(this,"No fresh paired-device playback",Toast.LENGTH_LONG).show(); return }
        choose("Continue watching",choices.map { it.first.device.label+" • "+it.second.optString("title") }) { index ->
            val (entry,row)=choices[index]
            val source=row.optString("package")
            val packages=PlaybackHandoff.packages(source).let { if(tvMode) it else it.reversed() }
            val position=row.optLong("positionMs",-1)
            val detail=row.optString("title")+"\n"+row.optString("subtitle")+"\n"+if(position>=0) "Resume near "+durationText(position) else "Resume position unavailable"
            val builder=BridgeDialogBuilder(this).setTitle("Continue on this device").setMessage(detail+"\n\nThe player may require you to find the title or seek manually. The source device keeps playing unless you pause it.")
                .setNegativeButton("Cancel",null).setPositiveButton("Open player") { _,_->
                    val fresh=RemotePlayback.visible(this).firstOrNull { it.device.id==entry.device.id }
                    val current=fresh?.let { e -> (0 until e.rows.length()).mapNotNull { e.rows.optJSONObject(it) }.firstOrNull { candidate -> HandoffPolicy.sameSession(row.optString("sessionId"),candidate.optString("sessionId"),row.optString("title"),candidate.optString("title"),row.optString("subtitle"),candidate.optString("subtitle"),source,candidate.optString("package")) } }
                    if(current==null) { Toast.makeText(this,"Playback changed or expired. Select the session again.",Toast.LENGTH_LONG).show() }
                    else {
                        val currentPosition=HandoffPolicy.estimatedPosition(current.optLong("positionMs",-1),current.optLong("durationMs",0),current.optInt("state"),android.os.SystemClock.elapsedRealtime()-requireNotNull(fresh).received)
                        val currentLink=PlaybackHandoff.link(source,current.optString("contentId"),currentPosition)
                        val launch=packages.firstNotNullOfOrNull { pkg ->
                            if(currentLink!=null) {
                                val direct=Intent(Intent.ACTION_VIEW,android.net.Uri.parse(currentLink)).setPackage(pkg)
                                if(direct.resolveActivity(packageManager)!=null) direct else null
                            } else null
                        } ?: packages.firstNotNullOfOrNull { pkg -> (if(tvMode) packageManager.getLeanbackLaunchIntentForPackage(pkg) else null) ?: packageManager.getLaunchIntentForPackage(pkg) }
                        if(launch==null) Toast.makeText(this,"Supported provider app not installed or unavailable",Toast.LENGTH_LONG).show()
                        else runCatching { startActivity(launch) }.onFailure { Toast.makeText(this,"Unable to open player",Toast.LENGTH_LONG).show() }
                    }
                }
            if(DeviceControl.permits(row.optInt("controlMask"),"pause")) builder.setNeutralButton("Pause source") { _,_->
                RemotePlayback.control(this,entry,row.optString("sessionId"),"pause") { ok ->
                    if(!isDestroyed && !isFinishing) { Toast.makeText(this,if(ok) "Pause command sent" else "Pause rejected",Toast.LENGTH_SHORT).show(); continueOnThisDevice() }
                }
            }
            builder.show()
        }
    }
    private fun remotePlaybackControls() {
        val choices=RemotePlayback.visible(this).flatMap { entry ->
            (0 until entry.rows.length()).mapNotNull { i -> entry.rows.optJSONObject(i)?.takeIf { it.optInt("controlMask")>0 && DeviceControl.valid(it.optString("sessionId")) }?.let { entry to it } }
        }
        if(choices.isEmpty()) { Toast.makeText(this,"No controllable remote playback",Toast.LENGTH_LONG).show(); return }
        choose("Remote playback",choices.map { it.first.device.label+" • "+it.second.optString("title") }) { index ->
            val (entry,row)=choices[index]
            val actions=listOf("play","pause").filter { DeviceControl.permits(row.optInt("controlMask"),it) }
            choose("Remote action",actions) { selected ->
                RemotePlayback.control(this,entry,row.optString("sessionId"),actions[selected]) { ok ->
                    if(!isDestroyed && !isFinishing) Toast.makeText(this,if(ok) "Command sent to player" else "Command unavailable or rejected",Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun nowPlaying() {
        var rows by androidx.compose.runtime.mutableStateOf<List<LiveUiSession>>(emptyList())
        var status by androidx.compose.runtime.mutableStateOf("Checking playback…")
        val body=ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { BridgeLiveDashboard(violet,rows,status,{ remotePlaybackControls() },{ continueOnThisDevice() },{ showTab(4) }) }
        }
        val dialog=BridgeDialogBuilder(this).setView(body).create()
        body.setContent { ModernToolPage(violet,"Now playing",{dialog.dismiss()}) { BridgeLiveDashboard(violet,rows,status,{ dialog.dismiss(); remotePlaybackControls() },{ dialog.dismiss(); continueOnThisDevice() },{ dialog.dismiss(); showTab(4); showSettingsCategory("tracking") }) } }
        val handler=android.os.Handler(android.os.Looper.getMainLooper())
        val tick=object:Runnable {
            override fun run() {
                if(!dialog.isShowing || isDestroyed || isFinishing) return
                val age=System.currentTimeMillis()-prefs.getLong("last_sample",0)
                val live=prefs.getBoolean("enabled",false) && PlaybackListener.liveConnected() && age in 0..20000
                val next=mutableListOf<LiveUiSession>()
                fun append(device:String,id:String,data:JSONArray,local:Boolean) {
                    for(i in 0 until data.length()) {
                        val row=data.optJSONObject(i) ?: continue
                        val position=row.optLong("positionMs",-1); val duration=row.optLong("durationMs",0)
                        val percent=DevicePlaybackDisplay.progress(position,duration)
                        val timing=if(percent==null) "Position unavailable" else durationText(position)+" / "+durationText(duration)
                        val evidence=if(local) "Evidence: "+row.optInt("recognitionScore")+"/100"+if(row.optBoolean("recognitionConflict")) " • conflicting evidence" else "" else ""
                        next.add(LiveUiSession(id+":"+i,device,row.optString("title").ifBlank { "Unidentified video" },row.optString("subtitle"),appLabel(row.optString("package")),DevicePlaybackDisplay.state(row.optInt("state")),percent,timing,evidence))
                    }
                }
                if(live) append("This device","local",runCatching { JSONArray(prefs.getString("sessions","[]")) }.getOrDefault(JSONArray()),true)
                val remote=RemotePlayback.visible(this@MainActivity)
                for(entry in remote) append(entry.device.label+if(entry.endpoint==null) " • Relay" else " • Wi-Fi",entry.device.id,entry.rows,false)
                status=(if(live) "Local tracking live" else "No fresh local observation")+" • "+remote.size+" connected device(s)"
                rows=next
                handler.postDelayed(this,2500)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        dialog.show()
        dialog.window?.apply { setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg)); setLayout(if(wide) dp(640).coerceAtMost(resources.displayMetrics.widthPixels) else -1,-1) }
        handler.post(tick)
    }
    private fun liveInspector() {
        val body=TextView(this).apply { setPadding(dp(16),dp(12),dp(16),dp(12)); setTextIsSelectable(true) }
        val dialog=BridgeDialogBuilder(this).setTitle("Live recognition inspector").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
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
        BridgeDialogBuilder(this).setTitle("Unified library").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Browse") { _,_->
            val library=UnifiedLibrary.build(history.records(0),anime,field.text.toString())
            choose("Library categories",listOf("Movies ("+library.movies.size+")","TV / Trakt series ("+library.series.size+")","AniList local anime ("+library.anime.size+")","Unidentified ("+library.unidentified.size+")","Downloaded Trakt account library")) { category ->
                when(category) {
                    0,3 -> { val rows=if(category==0) library.movies else library.unidentified; choose("Viewing titles",rows.take(200).map { it.representative.matchLabel.ifBlank { it.representative.title.ifBlank { "Unidentified" } } }) { showSessions(rows[it]) } }
                    1 -> choose("Series",library.series.take(200).map { it.title }) { showSeries(library.series[it].id) }
                    4 -> openDownloadedTrakt()
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
        BridgeDialogBuilder(this).setTitle(title).setMessage("Local personal progress only. No anime account synchronization.").setView(field).setNegativeButton("Cancel",null).setNeutralButton("Remove") { _,_->store.edit().remove(id.toString()).apply() }.setPositiveButton("Save") { _,_->
            val n=field.text.toString().toIntOrNull()
            if(n!=null && n in 0..(if(episodes>0) episodes else 10000)) store.edit().putString(id.toString(),org.json.JSONObject().put("id",id).put("title",title).put("episodes",episodes).put("progress",n).toString()).apply()
            else Toast.makeText(this,"Invalid episode count",Toast.LENGTH_LONG).show()
        }.show()
    }
    private fun publicAnimeList() {
        val field=EditText(this).apply { hint="AniList username"; setSingleLine(true) }
        BridgeDialogBuilder(this).setTitle("Public AniList library").setMessage("Read-only public list. Private accounts require authentication; no viewing history is uploaded.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Load") { _,_->
            val name=field.text.toString().trim()
            lookup("Loading public anime list",{ done -> AnimeCatalogue.publicList(name,done) }) { rows:List<AnimeListEntry> ->
                choose("AniList • "+name,rows.take(500).map { it.title+" • "+it.progress+"/"+(if(it.episodes>0) it.episodes.toString() else "?")+" • "+it.status }) { i -> startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://anilist.co/anime/"+rows[i].id))) }
            }
        }.show()
    }
    private fun animeSearch() {
        val field=EditText(this).apply { hint="English, Japanese or romaji title"; setSingleLine(true) }
        BridgeDialogBuilder(this).setTitle("Search AniList").setMessage("Your search text is sent to AniList. No account access or history upload.").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->
            val q=field.text.toString().trim()
            if(q.isBlank()) return@setPositiveButton
            lookup("Searching anime",{ done -> AnimeCatalogue.search(q,done) }) { rows:List<AnimeResult> ->
                choose("Anime matches",rows.map { it.english.ifBlank { it.romaji }+" • "+it.romaji }) { i ->
                    val a=rows[i]
                    BridgeDialogBuilder(this).setTitle(a.english.ifBlank { a.romaji }).setMessage(a.romaji+"\n"+a.native+"\nEpisodes: "+if(a.episodes>0) a.episodes.toString()+"\n\n"+a.description else "Unknown\n\n"+a.description).setNegativeButton("Close",null).setNeutralButton("Track locally") { _,_->editAnimeProgress(a.id,a.english.ifBlank { a.romaji },a.episodes) }.setPositiveButton("AniList details") { _,_->startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://anilist.co/anime/"+a.id))) }.show()
                }
            }
        }.show()
    }
    private fun recommendFromHistory() {
        val records=unifiedRows.ifEmpty { history.records(0) }
        val seeds=RecommendationEngine.seeds(records,System.currentTimeMillis())
        if(seeds.isEmpty()) {
            BridgeDialogBuilder(this).setTitle("Recommendations").setMessage("Confirm some viewing history first, or browse trending titles.").setNegativeButton("Close",null).setPositiveButton("Discover") { _,_->discover() }.show()
            return
        }
        val hiddenStore=personalStore("recommendation_hidden")
        BridgeDialogBuilder(this).setTitle("Personalized recommendations").setMessage("Trakt receives up to five catalogue title IDs to find related content. Viewing times and local feedback stay on this device.").setNegativeButton("Cancel",null).setNeutralButton("Reset hidden titles") { _,_->hiddenStore.edit().clear().apply(); recommendFromHistory() }.setPositiveButton("Find titles") { _,_->
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
                BridgeDialogBuilder(this).setTitle("For you").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
            }
        }.show()
    }
    private fun discover() {
        choose("Discover",listOf("Trending movies","Trending shows","Popular movies","Popular shows","Mood-based discovery")) { index ->
            if(index==4) { moodDiscovery(); return@choose }
            val type=if(index%2==0) "movie" else "show"; val mode=if(index<2) "trending" else "popular"
            lookup("Loading "+mode,{ done -> TraktCatalogue.discover(type,mode,done) }) { rows:List<CatalogueItem> ->
                val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
                if(tvMode) {
                    val shelf=rows.distinctBy { it.id }.map { item -> TvLibraryItem(item.id.toString(),item.title,item.label(),type,item.id) { titleDetails(type,item.id,item.title) } }
                    body.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeTvShelf(violet,mode.replaceFirstChar { it.uppercase() },shelf) } })
                } else cards(body,rows) { parent,item ->
                    val card=panel(parent); val image=ImageView(this).apply { scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Poster" }
                    card.addView(image,LinearLayout.LayoutParams(-1,dp(180))); Artwork.load(this,type,item.id,image)
                    card.addView(txt(item.label(),16,ink,true)); card.addView(actionButton("Details") { titleDetails(type,item.id,item.title) })
                }
                BridgeDialogBuilder(this).setTitle(mode.replaceFirstChar { it.uppercase() }).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
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
            val groups=ViewingGroups.build(unifiedRows.ifEmpty { history.records(0) }).filter { g -> val row=g.representative; if(type=="movie") row.matchType=="movie" && row.traktId==id else row.matchType=="episode" && row.showId==id }
            val actions=mutableListOf<UiAction>()
            if(groups.isNotEmpty()) actions.add(UiAction("View matching sessions"){ if(type=="show") showSeries(id) else showSessions(groups.first()) })
            actions.add(UiAction("Related titles") {
                lookup("Loading related titles",{ done -> TraktCatalogue.related(type,id,done) }) { rows:List<CatalogueItem> -> showRelatedTitles(rows) }
            })
            actions.add(UiAction("Watchlist / rating"){ lookup("Checking account",{ done -> TraktLibrary.accountId(this,done) }) { account:String -> remoteActions(RemoteItem(type,id,d.title),account) } })
            actions.add(UiAction("Where to watch"){ streamingAvailability(type,id,d.title.ifBlank { label }) })
            actions.add(UiAction("Cast and directors"){ lookup("Loading credits",{ done -> TraktCatalogue.credits(type,id,done) }) { names:List<String> -> BridgeDialogBuilder(this).setTitle("Cast and crew").setMessage(names.joinToString("\n").ifBlank { "No credits returned" }).setPositiveButton("Close",null).show() } })
            TitleDetailPolicy.trailer(d.trailer)?.let { url -> actions.add(UiAction("Watch trailer"){ runCatching { startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))) }.onFailure { Toast.makeText(this,"No compatible app",Toast.LENGTH_LONG).show() } }) }
            modernDetailWindow { close ->
                ModernCatalogueDetails(violet,d.copy(title=d.title.ifBlank { label }),type,id,prefs.getBoolean("hide_spoilers",true),
                    if(groups.isEmpty()) "" else groups.count { it.confirmed }.toString()+" completed · "+durationText(groups.sumOf { it.sampledMs })+" sampled",
                    actions.map { action -> UiAction(action.label){close();action.run()} },close)
            }
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
        val columns=adaptiveLayout.columns
        if(columns==1) { items.forEach { render(parent,it) }; return }
        items.chunked(columns).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.TOP }
            parent.addView(row)
            pair.forEach { item ->
                val column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
                row.addView(column,LinearLayout.LayoutParams(0,-2,1f).apply { marginEnd=dp(8) })
                render(column,item)
            }
            repeat(columns-pair.size) { row.addView(android.view.View(this),LinearLayout.LayoutParams(0,1,1f)) }
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
        BridgeDialogBuilder(this).setTitle("History filters").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel",null).setPositiveButton("Apply") { _,_->
            historyFilter=HistoryFilter(q.text.toString(),sources[source.selectedItemPosition],periods[period.selectedItemPosition],types[type.selectedItemPosition],statuses[status.selectedItemPosition],sorts[sort.selectedItemPosition]); historyLimit=MemoryPolicy.pageSize(lowRam); refreshHistory()
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
                        background=rounded(if(d.sampledMs>0) android.graphics.Color.argb(255, (android.graphics.Color.red(violet)*(0.25+0.6*d.sampledMs.toDouble()/days.maxOf { it.sampledMs }.coerceAtLeast(1))).toInt().coerceIn(0,255), (android.graphics.Color.green(violet)*(0.25+0.6*d.sampledMs.toDouble()/days.maxOf { it.sampledMs }.coerceAtLeast(1))).toInt().coerceIn(0,255), (android.graphics.Color.blue(violet)*(0.25+0.6*d.sampledMs.toDouble()/days.maxOf { it.sampledMs }.coerceAtLeast(1))).toInt().coerceIn(0,255)) else if(d.sessions>0) 0xff292c40.toInt() else surface,8)
                        contentDescription=d.date.toString()+", "+durationText(d.sampledMs)+" sampled, "+d.sessions+" sessions"
                        setOnClickListener { showCalendarDay(d,records) }
                    }
                }
                row.addView(cell,LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),dp(2),dp(2),dp(2)) })
            }
        }
        card.addView(txt("Dots indicate local records; brighter cells indicate more sampled playback. Sessions spanning midnight are attributed to their end date.",12,muted))
    }
    private fun showCalendarDay(day:ActivityDay,records:List<WatchRecord>) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt(durationText(day.sampledMs)+" sampled • "+day.sessions+" sessions",16,ink,true))
        ViewingGroups.build(records.filter { ViewingAnalytics.day(it.ended)==day.date }).forEach { titleCard(body,it,true) }
        BridgeDialogBuilder(this).setTitle(day.date.toString()).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).show()
    }
    private fun libraryMenu() {
        modernActionDialog("Library options","Manage your library",listOf("Synced Trakt account library","Tags and collections","Log a watch","Refresh","Reset filters","Viewing planner")) { i ->
            when(i) {
                0->openDownloadedTrakt()
                1->organizedLibrary()
                2->manualLog()
                3->refreshHistory()
                4->{ historyFilter=HistoryFilter(); libraryCategory="All"; historyLimit=MemoryPolicy.pageSize(lowRam); refreshHistory() }
                5->viewingPlanner()
            }
        }
    }
    private fun seriesMenu(series:SeriesGroup) {
        modernActionDialog(series.title,"Manage series",listOf("Browse seasons and episodes","Series details",if(isFavorite("show",series.id)) "Remove favorite" else "Add favorite","Personal status: "+libraryStatus("show",series.id).ifBlank { "Not set" },"Notes and tags","Add to collection","Trakt series progress")) { i ->
            when(i) {
                0->showSeries(series.id)
                1->titleDetails("show",series.id,series.title)
                2->{ toggleFavorite("show",series.id,series.title); refreshHistory() }
                3->setLibraryStatus("show",series.id,series.title)
                4->editTitleNotes("show",series.id,series.title)
                5->addCollection("show",series.id,series.title)
                6->remoteSeriesProgress(series.id,series.title)
            }
        }
    }
    private fun playbackAction(g:ViewingTitle): (()->Unit)? {
        val sources=g.sessions.filterNot { UnifiedTraktHistory.remote(it) }.sortedByDescending { it.ended }.map { it.source }.distinct()
        val launchable=sources.filter { runCatching { packageManager.getLaunchIntentForPackage(it)!=null }.getOrDefault(false) }
        return if(launchable.isEmpty()) null else { { openPlaybackApp(launchable) } }
    }
    // Presentation identity only: a suggestion never changes the record or authorizes an upload.
    private fun posterCandidate(g:ViewingTitle):CatalogueItem? =
        g.representative.takeIf { AutoMatchSuggestions.eligible(it) }?.let { r ->
            AutoMatchSuggestions.read(this,r).takeIf { it.status=="ready" }?.matches?.firstOrNull()?.item
        }
    private fun continueIdentity(g:ViewingTitle):String {
        val r=g.representative
        if(r.matchType=="episode" && r.showId>0) return "show:"+r.showId
        if(r.matchType=="movie" && r.traktId>0) return "movie:"+r.traktId
        val candidate=posterCandidate(g)
        return when {
            candidate?.type=="episode" && candidate.showId>0 -> "show:"+candidate.showId
            candidate?.type=="show" -> "show:"+candidate.id
            candidate?.type=="movie" -> "movie:"+candidate.id
            else -> g.key
        }
    }
    private fun continueCards(groups:List<ViewingTitle>,category:String="All",limit:Int=10):List<PosterLibraryItem> {
        val continuing=SeriesLibrary.continueWatching(groups)
        val buckets=continuing.groupBy { continueIdentity(it) }
        return buckets.entries.filter { (key,_) ->
            category=="All" || category=="Movies" && key.startsWith("movie:") || category=="Series" && key.startsWith("show:") || category=="Unidentified" && key.startsWith("session:")
        }.take(limit).map { (key,episodes) ->
            // Prefer most recently played unfinished episode, not historical completion.
            val current=episodes.first()
            val poster=groupPoster(current)
            if(key.startsWith("show:") && episodes.size>1) poster.copy(
                subtitle=poster.subtitle+" · "+episodes.size+" in progress",
                open={ showContinueEpisodes(poster.title,episodes) },
                menu={ showContinueEpisodes(poster.title,episodes) }
            ) else poster
        }
    }
    private fun showContinueEpisodes(title:String,episodes:List<ViewingTitle>) {
        modernDetailWindow { close -> ModernContinueEpisodes(violet,title,episodes.map { groupPoster(it) },{ index -> close(); showSessions(episodes[index]) },close) }
    }
    private fun groupPoster(g:ViewingTitle):PosterLibraryItem {
        val r=g.representative
        val candidate=posterCandidate(g)
        val episode=r.matchType=="episode" && r.showId>0 || candidate?.type=="episode" && candidate.showId>0
        val showId=if(r.showId>0 && r.matchType=="episode") r.showId else candidate?.let { if(it.type=="episode") it.showId else if(it.type=="show") it.id else 0L } ?: 0L
        val name=if(episode || showId>0) (candidate?.title ?: r.matchLabel).substringBefore(" — ").substringBefore(" • S").ifBlank { r.title } else candidate?.title ?: r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } }
        val season=if(r.season>=0) r.season else candidate?.season ?: -1
        val number=if(r.episode>0) r.episode else candidate?.episode ?: -1
        val prefix=if(episode && season>=0 && number>0) "S"+season+" E"+number+" · " else ""
        val detail=prefix+(if(g.rewatching) "Rewatch · "+(if(g.remainingMs>0) durationText(g.remainingMs)+" left" else "In progress") else if(g.confirmed) java.text.DateFormat.getDateInstance().format(java.util.Date(g.sessions.filter { it.status=="synced" }.maxOf { it.ended })) else if(g.remainingMs>0) durationText(g.remainingMs)+" left" else g.platforms.joinToString(" • ") { appLabel(it) })
        val progress=if(g.durationMs>0 && g.positionMs>=0) (g.positionMs.toDouble()/g.durationMs).toFloat().coerceIn(0f,1f) else null
        val open:()->Unit={ if(r.matchType=="episode" && r.showId>0) showSeries(r.showId) else showSessions(g) }
        val play=playbackAction(g)
        return PosterLibraryItem(name,detail,when { g.rewatching->"Rewatching"; g.confirmed->"Watched"; g.queued->"Awaiting sync"; candidate!=null->"Suggested match"; r.traktId<=0->"Needs match"; else->"In progress" },if(showId>0) "show" else "movie",if(showId>0) showId else if(r.traktId>0) r.traktId else candidate?.id ?: 0L,open,{ showSessions(g) },progress,play)
    }
    private fun renderPosterLibrary(records:List<WatchRecord>) {
        val accountId=RemoteAccountCache.active(this)
        val syncStatus=accountId?.let { RemoteAccountCache.status(this,it,TraktData.HISTORY) }
        val groups=ViewingAnalytics.sort(ViewingGroups.build(ViewingAnalytics.filter(records,historyFilter)),historyFilter.sort)
        val series=SeriesLibrary.build(groups)
        val movies=groups.filter { it.representative.matchType=="movie" }
        val other=groups.filter { it.representative.matchType!="movie" && !(it.representative.matchType=="episode" && it.representative.showId>0 && it.representative.season>=0 && it.representative.episode>0) }
        val anime=personalStore("anime_progress").all.values.mapNotNull { value -> runCatching { org.json.JSONObject(value.toString()) }.getOrNull() }.filter { it.optInt("id")>0 && it.optString("title").isNotBlank() && (historyFilter.query.isBlank() || it.optString("title").contains(historyFilter.query,true)) }.sortedBy { it.optString("title") }
        val category=libraryCategory
        val continuing=continueCards(groups,category,10)
        val sections=mutableListOf<PosterGridSection>()
        if(category in setOf("All","Series") && series.isNotEmpty()) sections.add(PosterGridSection("Your series",series.size) { index ->
            val item=series[index]
            val resume=item.resume?.representative
            val resumePoster=item.resume?.let { groupPoster(it) }
            PosterLibraryItem(item.title,resumePoster?.subtitle ?: (item.episodes.size.toString()+" tracked episode(s)"),if(resume!=null) "Continue S"+resume.season+" E"+resume.episode else item.completed.toString()+" watched","show",item.id,{ showSeries(item.id) },{ seriesMenu(item) },resumePoster?.progress,resumePoster?.play)
        })
        if(category in setOf("All","Movies") && movies.isNotEmpty()) sections.add(PosterGridSection("Your movies",movies.size){groupPoster(movies[it])})
        if(category in setOf("All","Anime") && anime.isNotEmpty()) sections.add(PosterGridSection("Your anime",anime.size){index ->
            val item=anime[index]; val id=item.optInt("id"); val title=item.optString("title"); val episodes=item.optInt("episodes")
            val open={editAnimeProgress(id,title,episodes)}
            PosterLibraryItem(title,item.optInt("progress").toString()+"/"+(if(episodes>0) episodes.toString() else "?"),"Anime","anime",id.toLong(),open,open)
        })
        if(category in setOf("All","Unidentified") && other.isNotEmpty()) sections.add(PosterGridSection("Unidentified viewing",other.size){groupPoster(other[it])})
        val columns=if(resources.configuration.fontScale>=1.5f) 1 else maxOf(2,adaptiveLayout.columns)
        libraryContent={
            ModernLazyLibrary(violet,columns,category,sections,continuing,
                if(syncStatus==null) "Sync Trakt history" else if(syncStatus.complete) "Trakt history synchronized" else "Trakt history partially downloaded",
                syncStatus?.let { it.count.toString()+" watch events" } ?: "Bring your existing movies and episodes into Library",
                historyFilter!=HistoryFilter(),{ selected -> libraryCategory=selected;refreshHistory() },{universalSearch()},{editHistoryFilter(records)},{libraryMenu()},{offerTraktSync()},{manualLog()},{animeSearch()})
        }
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
        val series=SeriesLibrary.build(ViewingGroups.build(if(RemoteAccountCache.active(this)!=null) unifiedRows.ifEmpty { history.records(0) } else history.records(0))).firstOrNull { it.id==id } ?: return
        val show=CatalogueItem("show",series.id,series.title)
        modernDetailWindow { close ->
            ModernSeriesDetails(violet,series,
                { close(); remoteSeriesProgress(series.id,series.title) },
                { close(); lookup("Loading catalogue seasons",{ done -> TraktCatalogue.seasons(show,done) }) { seasons:List<CatalogueSeason> ->
                    modernActionDialog(series.title,"Choose a catalogue season",seasons.map { if(it.number==0) "Specials" else "Season "+it.number }) { index -> showCatalogueSeason(series.id,seasons[index].number) }
                } },
                { season -> close();showCatalogueSeason(series.id,season) },
                { episode -> close();showSessions(episode) },close)
        }
    }
    private fun showCatalogueSeason(id:Long,season:Int) {
        val series=SeriesLibrary.build(ViewingGroups.build(if(RemoteAccountCache.active(this)!=null) unifiedRows.ifEmpty { history.records(0) } else history.records(0))).firstOrNull { it.id==id } ?: return
        val show=CatalogueItem("show",series.id,series.title)
        lookup("Loading episodes",{ done -> TraktCatalogue.episodes(show,season,done) }) { catalogue:List<CatalogueItem> ->
            val rows=SeriesLibrary.seasonRows(series,season,catalogue)
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            val completed=rows.count { it.local?.confirmed==true }
            val missing=FranchiseTimeline.missing(id,season,catalogue,unifiedRows.ifEmpty { history.records(0) })
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
            BridgeDialogBuilder(this).setTitle(series.title+" • "+if(season==0) "Specials" else "Season "+season).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Back") { _,_->showSeries(id) }.setPositiveButton("Close") { _,_->refreshHistory() }.show()
        }
    }
    private fun offerTraktSync() {
        BridgeDialogBuilder(this).setTitle("Sync your Trakt account?")
            .setMessage("Download existing movie and episode watches, repeat viewings, watchlist, ratings, saved playback, favorites and collections to this device. Data is encrypted and kept separate from measured local viewing. No uploads or changes to your Trakt account. A large account may take time.")
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Sync once") { _,_->startTraktSync() }
            .setPositiveButton("Sync now and daily") { _,_->
                lookup("Verifying Trakt account",{ done -> TraktLibrary.accountId(this,done) }) { id:String ->
                    RemoteRefreshSchedule.enable(this,id); startTraktSync()
                }
            }.show()
    }
    private var traktDownloadRunning=false
    private fun startTraktSync() {
        if(traktDownloadRunning) { Toast.makeText(this,"Trakt sync already running",Toast.LENGTH_SHORT).show(); return }
        traktDownloadRunning=true
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(16),dp(20),dp(16)) }
        val label=txt("Connecting to Trakt…",15,ink)
        val bar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate=true; max=1000; progressTintList=android.content.res.ColorStateList.valueOf(violet); indeterminateTintList=android.content.res.ColorStateList.valueOf(violet) }
        body.addView(label); body.addView(bar,LinearLayout.LayoutParams(-1,dp(10)))
        body.addView(txt("You can close this progress window and continue using the app.",13,muted))
        val dialog=BridgeDialogBuilder(this).setTitle("Synchronizing Trakt").setView(body).setPositiveButton("Hide",null).create()
        dialog.show()
        TraktLibrary.syncAccount(this,progress={ stage,done,total ->
            if(!isDestroyed && !isFinishing) {
                label.text=stage+if(total>0) " • "+done+" / "+total+" pages" else if(done>0) " • page "+done else "…"
                bar.isIndeterminate=total<=0
                if(total>0) bar.progress=(done.toLong()*1000/total).toInt().coerceIn(0,1000)
            }
        }) { result ->
            traktDownloadRunning=false
            if(!isDestroyed && !isFinishing) {
                val wasVisible=dialog.isShowing
                dialog.dismiss()
                bridgeModel.cancel()
                if(tab==4 && settingsCategory=="account") refreshSettingsState("account")
                refreshHistory()
                result.fold({ summary ->
                    val message=summary.results.joinToString("\n") { row -> row.kind.title+": "+row.count+(if(row.error.isNotBlank()) " • "+row.error else if(row.unchanged) " • unchanged" else "") }
                    if(wasVisible) BridgeDialogBuilder(this).setTitle(if(summary.complete) "Trakt sync complete" else "Trakt sync incomplete")
                        .setMessage(message.ifBlank { "No categories completed. Check connection and retry." })
                        .setNegativeButton("Close",null).setNeutralButton("Retry"){_,_->startTraktSync()}.setPositiveButton("Open library"){_,_->showTab(1)}.show()
                    else Toast.makeText(this,if(summary.complete) "Trakt sync complete" else "Trakt sync incomplete; retry in Account & Sync",Toast.LENGTH_LONG).show()
                }, {
                    if(wasVisible) BridgeDialogBuilder(this).setTitle("Trakt sync failed").setMessage("Check your connection and try again. Existing history is retained.").setNegativeButton("Close",null).setPositiveButton("Retry"){_,_->startTraktSync()}.show()
                    else Toast.makeText(this,"Trakt sync failed; retry in Account & Sync",Toast.LENGTH_LONG).show()
                })
            }
        }
    }
    private fun openDownloadedTrakt() {
        val accountId=RemoteAccountCache.active(this)
        if(accountId==null) {
            BridgeDialogBuilder(this).setTitle("Your Trakt library").setMessage("Connect your Trakt account and sync to download your existing data.").setNegativeButton("Close",null).setNeutralButton("Online library") { _,_->openTraktLibrary() }.setPositiveButton("Sync now") { _,_->offerTraktSync() }.show()
            return
        }
        val kinds=TraktData.entries
        val labels=kinds.map { kind ->
            val s=RemoteAccountCache.status(this,accountId,kind)
            kind.title+" • "+(s?.let { it.count.toString()+if(it.complete) "" else " (partial)" } ?: "Not downloaded")
        }
        choose("Downloaded Trakt library",labels+listOf("Sync now","Online Trakt library","Daily sync settings")) { i ->
            when(i) {
                kinds.size->offerTraktSync()
                kinds.size+1->openTraktLibrary()
                kinds.size+2->configureAccountRefresh()
                else->browseDownloadedTrakt(accountId,kinds[i],1,"")
            }
        }
    }
    private fun browseDownloadedTrakt(accountId:String,kind:TraktData,page:Int,query:String) {
        if(RemoteAccountCache.active(this)!=accountId) { openDownloadedTrakt(); return }
        val app=applicationContext
        lookup("Loading downloaded "+kind.title,{ done ->
            Thread {
                val result=runCatching { RemoteAccountCache.rows(app,accountId,kind).filter { query.isBlank() || it.title.contains(query,ignoreCase=true) } }
                runOnUiThread { done(result) }
            }.start()
        }) { rows:List<RemoteItem> ->
            if(RemoteAccountCache.active(this)!=accountId) return@lookup
            val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
            val size=50; val pages=maxOf(1,(rows.size+size-1)/size); val current=page.coerceIn(1,pages)
            val status=RemoteAccountCache.status(this,accountId,kind)
            body.addView(txt(rows.size.toString()+" entries • Page "+current+" of "+pages,16,ink,true))
            body.addView(txt("Downloaded from Trakt • "+(status?.let { if(it.complete) "Complete snapshot" else "Partial snapshot" } ?: "Not synced")+" • Does not add measured watch time",13,muted))
            var dialog:android.app.AlertDialog?=null
            body.addView(actionButton("Search downloaded titles") {
                val field=EditText(this).apply { hint="Title"; setSingleLine(true); setText(query) }
                BridgeDialogBuilder(this).setTitle("Search "+kind.title).setView(field).setNegativeButton("Cancel",null).setPositiveButton("Search") { _,_->dialog?.dismiss(); browseDownloadedTrakt(accountId,kind,1,field.text.toString().trim()) }.show()
            })
            for(item in rows.drop((current-1)*size).take(size)) {
                val card=panel(body)
                card.addView(txt(item.title,17,ink,true))
                if(item.watchedAt.isNotBlank()) card.addView(txt("Watched on Trakt: "+item.watchedAt,12,muted))
                if(item.rating>0) card.addView(txt("Your rating: "+item.rating+"/10",13,violet))
                if(item.progress in 0.0..100.0) card.addView(txt("Saved progress: "+item.progress.toInt()+"%",13,violet))
                if(item.type in setOf("movie","show")) card.addView(actionButton("Title details (online)") { titleDetails(item.type,item.id,item.title) })
                if(item.showId>0) card.addView(actionButton("Series progress (online)") { remoteSeriesProgress(item.showId,item.title.substringBefore(" — ")) })
                card.addView(actionButton("Watchlist / rating actions (online)") { remoteActions(item,accountId) })
            }
            if(rows.isEmpty()) body.addView(txt("No matching downloaded entries.",14,muted))
            if(current>1) body.addView(actionButton("Previous page") { dialog?.dismiss(); browseDownloadedTrakt(accountId,kind,current-1,query) })
            if(current<pages) body.addView(actionButton("Next page") { dialog?.dismiss(); browseDownloadedTrakt(accountId,kind,current+1,query) })
            dialog=BridgeDialogBuilder(this).setTitle(kind.title).setView(ScrollView(this).apply { addView(body) }).setNegativeButton("Back") { _,_->openDownloadedTrakt() }.setPositiveButton("Close",null).create()
            dialog.show()
        }
    }
    private fun configureAccountRefresh() {
        val enabled=prefs.getString("auto_refresh_account","").orEmpty().isNotBlank()
        BridgeDialogBuilder(this).setTitle("Daily Trakt library sync").setMessage("Refresh downloaded history, watchlist, ratings, playback, favorites and collections approximately daily while connected. No upload or sampled watch time. Currently: "+if(enabled) "enabled" else "disabled").setNegativeButton("Close",null).setNeutralButton("Disable") { _,_->RemoteRefreshSchedule.disable(this); Toast.makeText(this,"Automatic refresh disabled",Toast.LENGTH_SHORT).show() }.setPositiveButton("Enable for connected account") { _,_->
            lookup("Verifying account",{ done -> TraktLibrary.accountId(this,done) }) { id:String ->
                RemoteRefreshSchedule.enable(this,id); startTraktSync(); Toast.makeText(this,"Daily library sync enabled",Toast.LENGTH_LONG).show()
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
        dialog=BridgeDialogBuilder(this).setTitle("Trakt account library").setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close",null).create()
        dialog.show()
    }
    private fun remoteSeriesProgress(showId:Long,title:String) {
        lookup("Loading account progress",{ done -> TraktLibrary.progress(this,showId,done) }) { result:RemoteProgress ->
            BridgeDialogBuilder(this).setTitle(title).setMessage(result.completed.toString()+" of "+result.aired+" aired episodes completed on Trakt"+if(result.next.isBlank()) "" else "\nNext episode: "+result.next).setPositiveButton("OK",null).show()
        }
    }
    private fun remoteActions(item:RemoteItem,account:String) {
        lookup("Checking watchlist and rating",{ done -> TraktLibrary.titleState(this,account,item,done) }) { state:RemoteTitleState ->
            val view=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow) }
            val dialog=BridgeDialogBuilder(this).setView(view).create()
            fun update(action:String,rating:Int=0,done:(Boolean)->Unit) {
                TraktLibrary.mutate(this,account,item,action,rating) { result ->
                    if(!isDestroyed && !isFinishing) {
                        val confirmed=result.getOrNull()=="Trakt change confirmed"
                        val message=if(!confirmed) "Change not confirmed. Please retry." else when(action) {
                            "watchlist-add"->"Added to watchlist"
                            "watchlist-remove"->"Removed from watchlist"
                            "rate"->"Rating saved: "+rating+"/10"
                            else->"Rating removed"
                        }
                        Toast.makeText(this,message,Toast.LENGTH_SHORT).show()
                        done(confirmed)
                    }
                }
            }
            view.setContent { ModernTraktActions(violet,item.title,state,{ added,done -> update(if(added) "watchlist-add" else "watchlist-remove",done=done) },{ rating,done -> update(if(rating==0) "unrate" else "rate",rating,done) },{dialog.dismiss()}) }
            dialog.show()
            dialog.window?.apply {
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                setGravity(android.view.Gravity.BOTTOM)
                setLayout(if(wide) dp(560).coerceAtMost(resources.displayMetrics.widthPixels) else -1,-2)
            }
        }
    }
    private fun durationText(ms:Long):String {
        if(ms<0) return "Unknown"
        val min=ms/60000
        return if(min>=60) (min/60).toString()+"h "+(min%60)+"m" else if(min==0L && ms>0) (ms/1000).toString()+"s" else min.toString()+" min"
    }
    private val suggestionListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_ ->
        if(!isDestroyed && !isFinishing && tab in 0..2 && ::contentPage.isInitialized && (!prefs.getBoolean("privacy_lock",false) || unlocked)) {
            restoreScrollY=contentPage.scrollY
            refreshHistory()
        }
    }
    private fun requestReviewSuggestions(records:List<WatchRecord>) {
        if(tab !in 0..2 || (prefs.getBoolean("privacy_lock",false) && !unlocked)) return
        RewatchAutomation.request(this)
        AutoMatchSuggestions.enqueue(this,records)
    }
    private fun acceptSuggestion(r:WatchRecord,item:CatalogueItem) {
        val current=history.record(r.id)
        if(current==null || !AutoMatchSuggestions.eligible(current) || current.title!=r.title || current.subtitle!=r.subtitle) {
            Toast.makeText(this,"Viewing record changed; refresh and try again",Toast.LENGTH_LONG).show()
            refreshHistory(); return
        }
        if(item.type=="show") { selectSuggestedEpisode(current,item); return }
        history.match(current.id,item,false)
        val saved=history.record(current.id)
        if(saved==null || saved.traktId!=item.id || saved.matchType!=item.type) {
            Toast.makeText(this,"Match could not be saved; refresh and try again",Toast.LENGTH_LONG).show()
            refreshHistory(); return
        }
        AutoMatchSuggestions.confirmed(this,current,item)
        AutoMatchSuggestions.remove(this,current.id)
        ViewingNotifications.cancel(this,current.id)
        refreshHistory()
        Toast.makeText(this,"Match saved locally",Toast.LENGTH_SHORT).show()
    }
    private fun suggestionActions(r:WatchRecord,state:AutoMatchSuggestions.State):List<UiAction> {
        val best=state.matches.firstOrNull()?.item
        val actions=mutableListOf<UiAction>()
        if(best!=null) {
            actions.add(UiAction(if(best.type=="show") "Yes — choose episode" else "Yes, correct") { acceptSuggestion(r,best) })
            if(state.matches.size>1) actions.add(UiAction("Other suggested matches") {
                choose("Alternative Trakt matches",state.matches.drop(1).map { it.item.label()+" • "+it.reason }) { i ->
                    val item=state.matches[i+1].item
                    BridgeDialogBuilder(this).setTitle("Use this match?").setMessage(item.label()).setNegativeButton("Cancel",null).setPositiveButton("Yes") { _,_->acceptSuggestion(r,item) }.show()
                }
            })
            actions.add(UiAction("No, incorrect") { AutoMatchSuggestions.reject(this,r); refreshHistory() })
        }
        actions.add(UiAction("Search manually") { identify(r) })
        return actions
    }
    private fun titleEntry(g:ViewingTitle,expanded:Boolean):LazyTitleEntry {
        val r=g.representative
        val suggestion=if(tab==2 && AutoMatchSuggestions.eligible(r)) AutoMatchSuggestions.read(this,r) else null
        val candidate=suggestion?.matches?.firstOrNull()?.item
        val assessment=g.positionSession?.let { completionAssessment(it) }
        val completion=when { g.sessions.all { it.status=="restored_audit" } -> "Restored history • prior upload not repeated"; g.rewatching -> "Rewatching • original watch preserved"; g.confirmed -> "Finished • synced to Trakt"; g.queued -> "Completion approved • sync pending"; g.active -> "Currently watching"; assessment?.suggested==true -> "Completion suggested • confirm manually"; g.positionMs>=0 -> "In progress"; else -> "Completion unknown" }
        val progress=if(g.durationMs>0 && g.positionMs>=0) (g.positionMs.toDouble()/g.durationMs*100).toInt().coerceIn(0,100) else null
        val position=if(progress!=null) durationText(g.positionMs)+" / "+durationText(g.durationMs)+" • "+durationText(g.remainingMs)+" left • "+g.positionSource else (if(g.catalogueRuntimeMs>0) "Catalogue runtime: "+durationText(g.catalogueRuntimeMs)+" • " else "")+"Remaining time unavailable"
        val diagnostics=buildString {
            if(g.bufferingMs>0 || g.pausedMs>0 || g.seeks>0 || g.uncertainMs>0) append("Paused: "+durationText(g.pausedMs)+" • Buffering: "+durationText(g.bufferingMs)+" • Seeks: "+g.seeks+" • Uncertain: "+durationText(g.uncertainMs))
            if(suggestion!=null) append("\n"+when(suggestion.status) { "ready"->"Suggested: "+suggestion.matches.first().reason+" • Original: "+r.title; "searching","waiting"->"Searching Trakt automatically…"; "none"->"No reliable Trakt match found."; "error"->"Catalogue unavailable; will retry."; "rejected"->"Suggestion rejected."; else->"" })
            else if(r.traktId<=0) append(" Identify exact title to combine sessions.")
        }
        val row=TitleUi(candidate?.label() ?: r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },if(candidate!=null) "Suggested Trakt match • "+appLabel(r.source) else g.platforms.joinToString(" • ") { appLabel(it) },g.sessions.size.toString()+" viewing session(s) • "+java.text.DateFormat.getDateInstance().format(java.util.Date(g.latest.ended)),completion,"Total sampled watch time: "+if(g.sampledMs>0) durationText(g.sampledMs) else "Unknown",progress,position,diagnostics,if(candidate?.type in setOf("episode","show") || r.matchType=="episode") "show" else "movie",candidate?.let { if(it.type=="episode") it.showId else it.id } ?: if(r.matchType=="episode") r.showId else r.traktId)
        val actions=mutableListOf<UiAction>()
        if(suggestion!=null) actions.addAll(suggestionActions(r,suggestion))
        if(r.traktId>0) actions.add(UiAction("Title details") { titleDetails(if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,r.matchLabel) })
        if(!g.confirmed) actions.add(UiAction("Open playback app") { openPlaybackApp(g.platforms) })
        if(r.traktId>0) actions.add(UiAction("Review possible duplicate titles") { duplicateTitles(r) })
        if(r.traktId>0) actions.add(UiAction("More title actions") {
            modernActionDialog("Manage title",r.matchLabel,listOf("Personal status: "+libraryStatus(r.matchType,r.traktId).ifBlank { "Not set" },if(isFavorite(r.matchType,r.traktId)) "Remove favorite" else "Add favorite","Notes and tags","Add to collection")) { action ->
                when(action) { 0->setLibraryStatus(r.matchType,r.traktId,r.matchLabel); 1->{ toggleFavorite(r.matchType,r.traktId,r.matchLabel); refreshHistory() }; 2->editTitleNotes(r.matchType,r.traktId,r.matchLabel); 3->addCollection(r.matchType,r.traktId,r.matchLabel) }
            }
        })
        actions.add(UiAction(if((expanded || g.pending || g.queued) && !g.confirmed) "Review / view sessions" else "View sessions") { showSessions(g) })
        return LazyTitleEntry(r.id,row,actions)
    }

    private fun titleCard(parent:LinearLayout,g:ViewingTitle,expanded:Boolean) {
        val item=titleEntry(g,expanded)
        parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeTitleCard(violet,item.row,item.actions) } })
    }
    private fun lazyTitles(parent:LinearLayout,groups:List<ViewingTitle>,expanded:Boolean) {
        if(wide || groups.size<8) { cards(parent,groups) { target,g -> titleCard(target,g,expanded) }; return }
        val items=groups.map { titleEntry(it,expanded) }
        parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeLazyTitles(violet,items) } })
    }
    private fun duplicateTitles(target:WatchRecord) {
        val rows=LibraryDuplicates.candidates(history.records(0),target)
        choose("Possible duplicate titles",rows.map { it.title+" • "+appLabel(it.source)+" • "+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.started)) }) { i ->
            BridgeDialogBuilder(this).setTitle("Link to same catalogue title?").setMessage(rows[i].title+" → "+target.matchLabel+"\nVerify the exact title and episode. Original sessions and sampled time remain unchanged. Nothing is approved or uploaded.").setNegativeButton("Cancel",null).setPositiveButton("Link title") { _,_->
                if(history.linkDuplicateTitle(target.id,rows[i].id)) { ViewingNotifications.cancel(this,rows[i].id); refreshHistory() } else Toast.makeText(this,"Record changed; review again",Toast.LENGTH_LONG).show()
            }.show()
        }
    }
    private fun showSessions(g:ViewingTitle) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(20)) }
        val r=g.representative
        val title=if(r.matchType=="episode") r.matchLabel.substringBefore(" — ") else r.matchLabel.ifBlank { r.title.ifBlank { "Viewing details" } }
        body.addView(txt(g.sessions.size.toString()+" viewing session(s) · "+g.sessions.count { it.status=="synced" }+" synced",13,muted))
        val dialog=BridgeDialogBuilder(this).setTitle(title).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Done") { _,_->refreshHistory() }.create()
        g.sessions.forEach { session ->
            if(!UnifiedTraktHistory.remote(session) && session.id in g.possibleRewatchIds) body.addView(txt("Possible rewatch — review before approving",14,muted))
            recordCard(body,session,true,g.completedWatches.any { it.ended<session.started } || g.queued,onDeleted={ dialog.dismiss() },onNavigate={ dialog.dismiss() })
        }
        dialog.show()
    }
    private fun recordCard(parent:LinearLayout,r:WatchRecord,expanded:Boolean,completionAlreadyRecorded:Boolean=false,onDeleted:()->Unit={},onNavigate:()->Unit={}) {
        if(UnifiedTraktHistory.remote(r)) {
            val row=TitleUi(r.matchLabel,"Trakt account history",java.text.DateFormat.getDateTimeInstance().format(java.util.Date(r.ended)),"Watched on Trakt","Historical watch duration unavailable",null,"","Read-only downloaded watch event",if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId)
            parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeTitleCard(violet,row,emptyList()) } }); return
        }
        val status=if(completionAlreadyRecorded && r.status in setOf("pending","reviewed")) "Rewatching · already watched on Trakt" else when(r.status) { "synced"->"Synced to Trakt"; "active"->"Watching"; "pending"->"Needs review"; "reviewed"->if(completionAssessment(r).suggested) "Ready to confirm" else "Matched · in progress"; "restored_audit"->"Restored audit • no upload"; else->r.status.replaceFirstChar { it.uppercase() } }
        val progress=if(r.duration>0 && r.position>=0) (r.position.toDouble()/r.duration*100).toInt().coerceIn(0,100) else null
        val evidence=buildString {
            if(r.status in setOf("pending","reviewed")) { val a=completionAssessment(r); append("Completion evidence: "+(a.percent?.let { it.toString()+"% • " } ?: "")+a.reason) }
            if(r.seeks>0 || r.bufferingMs>0 || r.pausedMs>0 || r.uncertainMs>0) append("\nPaused: "+durationText(r.pausedMs)+" • Buffering: "+durationText(r.bufferingMs)+" • Seeks: "+r.seeks+" (rewinds "+r.rewinds+") • Uncertain: "+durationText(r.uncertainMs))
            if(expanded && r.traktId>0) append("\nTrakt ID "+r.traktId)
            if(expanded && r.status in setOf("queued","uncertain","blocked","synced")) append("\nSync: "+r.status+" — "+history.syncMessage(r.id))
        }
        val row=TitleUi(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },r.subtitle.takeUnless { it.equals(appLabel(r.source),true) }.orEmpty(),appLabel(r.source)+" • "+java.text.DateFormat.getDateInstance().format(java.util.Date(r.started)),status,if(r.watched<=0) "Watch time unknown" else durationText(r.watched)+" sampled",progress,if(progress!=null) durationText(r.position)+" / "+durationText(r.duration) else "",evidence,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId)
        val actions=mutableListOf<UiAction>()
        if(!expanded) actions.add(UiAction("View details") { BridgeDialogBuilder(this).setTitle("Viewing details").setMessage(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } }+"\n"+r.subtitle).setNegativeButton("Close",null).setPositiveButton("Open history") { _,_->showTab(1) }.show() })
        else {
            if(r.traktId>0) actions.add(UiAction("Trakt watchlist / rating") { lookup("Checking account",{ done -> TraktLibrary.accountId(this,done) }) { account:String -> remoteActions(RemoteItem(r.matchType,r.traktId,r.matchLabel,r.showId,r.season,r.episode),account) } })
            if(r.id.startsWith("import:") && r.status=="pending") actions.add(UiAction("Reconcile with local history") { reconcileImported(r) })
            if(r.status in setOf("pending","reviewed")) actions.add(UiAction(if(r.traktId>0) "Change Trakt match" else "Identify on Trakt") { identify(r) })
            if(r.traktId>0 && r.status in setOf("pending","reviewed")) actions.add(UiAction("Remove Trakt match") { history.clearMatch(r.id); onNavigate(); refreshHistory() })
            if(r.status=="reviewed" && r.traktId>0 && !completionAlreadyRecorded) actions.add(UiAction("Mark watched on Trakt") { approveUpload(r,false) })
            if(r.status=="blocked") actions.add(UiAction("Retry blocked upload") { history.retryBlocked(r.id); HistorySync.schedule(this); refreshHistory() })
            if(r.status in setOf("queued","blocked")) actions.add(UiAction("Cancel queued upload") { history.cancelQueued(r.id); refreshHistory() })
            if(r.status=="uncertain") actions.add(UiAction("Reconcile with Trakt (no resend)") { HistorySync.schedule(this); refreshHistory() })
            if(r.status=="pending") actions.add(UiAction("Review / edit title") {
                val field=EditText(this).apply { setText(r.title); hint="Movie or episode title" }
                BridgeDialogBuilder(this).setTitle("Confirm locally (not synced)").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->
                    val title=field.text.toString().trim(); if(title.isNotEmpty()) { history.review(r.id,title); ViewingNotifications.cancel(this,r.id); refreshHistory() }
                }.show()
            })
            if(r.status in setOf("pending","reviewed")) actions.add(UiAction("Delete viewing record") {
                BridgeDialogBuilder(this).setTitle("Delete this local record?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_->
                    val deleted=runCatching { history.trash(r.id) }.getOrDefault(false)
                    if(deleted) {
                        AutoMatchSuggestions.remove(this,r.id)
                        ViewingNotifications.cancel(this,r.id)
                        onDeleted()
                        refreshHistory()
                        Toast.makeText(this,"Viewing record deleted",Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this,"Record could not be deleted; refresh and try again",Toast.LENGTH_LONG).show()
                        refreshHistory()
                    }
                }.show()
            })
        }
        if(expanded) {
            val primary=actions.firstOrNull { it.label in setOf("Mark watched on Trakt","Confirm separate rewatch","Identify on Trakt","Retry blocked upload") }
            val menu=actions.filter { it!==primary }
            val item=PosterLibraryItem(r.matchLabel.ifBlank { r.title },r.subtitle.takeUnless { it.equals(appLabel(r.source),true) }.orEmpty(),"",if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,{},{},progress?.div(100f))
            parent.addView(ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent { ModernSessionDetails(violet,item,status,appLabel(r.source)+" · "+java.text.DateFormat.getDateInstance().format(java.util.Date(r.started)),if(r.watched<=0) "Watch time unknown" else durationText(r.watched)+" sampled",if(progress!=null) durationText(r.position)+" / "+durationText(r.duration) else "",evidence,primary?.let { action -> UiAction(action.label) { onNavigate(); action.run() } },menu.map { action -> UiAction(action.label) { onNavigate(); action.run() } }) }
            })
        } else parent.addView(ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow); setContent { BridgeTitleCard(violet,row,actions) } })
    }
    private fun appLabel(pkg: String): String = mapOf(UnifiedTraktHistory.SOURCE to "Trakt history","server.plex" to "Plex server","server.jellyfin" to "Jellyfin server","server.emby" to "Emby server","server.kodi" to "Kodi server")[pkg] ?: apps.entries.firstOrNull { it.value == pkg }?.key ?: runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg,0)).toString()
    }.getOrDefault(pkg)
    private fun chooseInstalledApps() {
        val packages=PlaybackDetectors.supported.toList()
        val selected=PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty()).toMutableSet()
        val checked=packages.map { it in selected }.toBooleanArray()
        BridgeDialogBuilder(this).setTitle("Supported playback apps")
            .setMultiChoiceItems(packages.map { appLabel(it)+" — regular videos only" }.toTypedArray(),checked) { _,index,enabled ->
                if(enabled) selected.add(packages[index]) else selected.remove(packages[index])
            }.setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ ->
                prefs.edit().putStringSet("allowed",selected).apply(); PlaybackListener.wake(); recreate()
            }.show()
    }
    private fun configureCompletionThreshold() {
        val options=listOf(75,80,85,90,95,100)
        val current=CompletionPolicy.threshold(prefs.getInt("completion_threshold",CompletionPolicy.DEFAULT_THRESHOLD))
        BridgeDialogBuilder(this).setTitle("Completion review threshold").setMessage("Current: "+current+"%. A suggestion never uploads a completed watch. You must still approve it.").setNeutralButton("Change") { _,_->
            choose("Suggest completion at",options.map { it.toString()+"%" }) { i -> prefs.edit().putInt("completion_threshold",options[i]).apply(); refreshHistory() }
        }.setPositiveButton("Close",null).show()
    }
    private fun completionAssessment(r:WatchRecord)=CompletionPolicy.assess(r,prefs.getInt("completion_threshold",CompletionPolicy.DEFAULT_THRESHOLD))
    private fun approveUpload(r: WatchRecord,rewatch:Boolean=false) {
        BridgeDialogBuilder(this).setTitle(if(rewatch) "Confirm a separate completed rewatch?" else "Mark as fully watched?")
            .setMessage(r.matchLabel + "\nThis adds a completed watch to Trakt at the recorded end time. Only confirm if you finished the movie or episode. Partial viewing stays local. For app-activity records the end time is only the app exit time.")
            .setNegativeButton("Cancel",null).setPositiveButton("Verify account") { _,_ ->
                Thread {
                    val result=runCatching { HistorySync.accountForApproval(applicationContext) }
                    runOnUiThread {
                        if(!isFinishing && !isDestroyed) result.fold({ accountId ->
                            BridgeDialogBuilder(this).setTitle("Approve upload to connected Trakt account?")
                                .setMessage(r.matchLabel + "\nAccount ID: " + accountId + "\nApproved records will retry when online.")
                                .setNegativeButton("Cancel",null).setPositiveButton("Approve and sync") { _,_ ->
                                    if(history.enqueue(r.id,accountId,rewatch,r.traktId,r.matchType,r.ended)) HistorySync.schedule(this) else Toast.makeText(this,"This title already has a completion or pending upload for this account.",Toast.LENGTH_LONG).show()
                                    refreshHistory()
                                }.show()
                        },{ BridgeDialogBuilder(this).setTitle("Account verification required").setMessage("Connect to Trakt and verify the account online before approving uploads.").setPositiveButton("OK",null).show() })
                    }
                }.start()
            }.show()
    }
    private fun identify(r: WatchRecord) {
        val initial=RecognitionEngine.query(r.title)
        val learned=history.learned(r)?.takeIf { r.traktId==0L }
        val view=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow) }
        val dialog=BridgeDialogBuilder(this).setView(view).create()
        fun search(q:String,type:String) {
            dialog.dismiss()
            lookup("Searching Trakt", { done -> TraktCatalogue.search(q,type,done) }) { results: List<CatalogueItem> ->
                val ranked=RecognitionEngine.rank(if(q==initial) r.title else q,r.subtitle,r.duration,results)
                choose("Select title",ranked.map { it.item.label()+" · "+it.score+"/100\n"+it.reason }) { index ->
                    val item=ranked[index].item
                    if(item.type=="movie") confirmMatch(r,item) else selectSuggestedEpisode(r,item)
                }
            }
        }
        view.setContent { ModernIdentifyDialog(violet,initial,{q,type->search(q,type)},{dialog.dismiss();externalIdentity(r)},learned?.let { item -> UiAction("Use learned match: "+item.label()){dialog.dismiss();confirmMatch(r,item)} },{dialog.dismiss()}) }
        dialog.show()
    }
    private fun <T> lookup(label: String, request: ((Result<T>) -> Unit) -> Unit, success: (T) -> Unit) {
        val progress = BridgeDialogBuilder(this).setMessage(label + "…").setNegativeButton("Cancel",null).create()
        progress.show()
        try { request { result ->
            if (!isFinishing && !isDestroyed && progress.isShowing) {
                progress.dismiss()
                try { result.getOrThrow().let(success) } catch(e:Exception) { DebugTrace.failure("catalogue-ui",e); BridgeDialogBuilder(this).setTitle("Catalogue unavailable").setMessage("Unable to complete this request. Try again later.").setPositiveButton("OK",null).show() }
            }
        } } catch(e:Exception) { progress.dismiss(); DebugTrace.failure("catalogue-request",e); if(!isFinishing && !isDestroyed) Toast.makeText(this,"Unable to start request. Try again.",Toast.LENGTH_LONG).show() }
    }
    private fun modernDetailWindow(content:@androidx.compose.runtime.Composable (()->Unit)->Unit) {
        if(isFinishing || isDestroyed) return
        val dialog=android.app.Dialog(this)
        val view=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow) }
        val decor=requireNotNull(dialog.window).decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        dialog.setContentView(view)
        view.setContent { content { dialog.dismiss() } }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
            setLayout(if(wide) dp(640).coerceAtMost(resources.displayMetrics.widthPixels) else -1,-1)
        }
    }
    private fun showRelatedTitles(rows:List<CatalogueItem>) {
        modernDetailWindow { close -> ModernRelatedTitles(violet,rows,{ item -> close(); titleDetails(item.type,item.id,item.title) },close) }
    }
    private fun modernActionDialog(title:String,detail:String,labels:List<String>,selected:(Int)->Unit) {
        val view=ComposeView(this).apply { setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow) }
        val dialog=BridgeDialogBuilder(this).setView(view).create()
        view.setContent { ModernActionDialog(violet,title,detail,labels,{ index -> dialog.dismiss(); selected(index) },{dialog.dismiss()}) }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setGravity(android.view.Gravity.BOTTOM)
            setLayout(if(wide) dp(560).coerceAtMost(resources.displayMetrics.widthPixels) else -1,-2)
        }
    }
    private fun choose(title: String, labels: List<String>, selected: (Int) -> Unit) {
        if (labels.isEmpty()) { BridgeDialogBuilder(this).setTitle(title).setMessage("No results. Try another title or spelling.").setPositiveButton("OK",null).show(); return }
        BridgeDialogBuilder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index -> BridgeHaptics.tap(window.decorView); selected(index) }.setNegativeButton("Cancel",null).show()
    }
    private fun selectSuggestedEpisode(r:WatchRecord,show:CatalogueItem) {
        val evidence=r.title+" "+r.subtitle
        val ep=RecognitionEngine.episodeOf(evidence) ?: AnimeEpisodeMapping.specialNumber(evidence)
        if(ep==null) {
            val absolute=AnimeEpisodeMapping.absoluteNumber(evidence)
            if(absolute==null) selectSeason(r,show) else BridgeDialogBuilder(this).setTitle("Episode numbering").setMessage("Detected episode "+absolute+". Absolute anime numbering may differ from catalogue season numbering. Choose the exact season and episode manually.").setPositiveButton("Choose episode") { _,_->selectSeason(r,show) }.setNegativeButton("Cancel",null).show()
            return
        }
        lookup("Checking detected episode", { done -> TraktCatalogue.episodes(show,ep.season,done) }) { episodes:List<CatalogueItem> ->
            val exact=RecognitionEngine.exactEpisode(show.id,ep,episodes)
            if(exact==null) selectSeason(r,show) else BridgeDialogBuilder(this).setTitle("Detected episode").setMessage(exact.label()+"\nSeason and episode agree with the detected text. Confirm the exact episode.").setNegativeButton("Cancel",null).setNeutralButton("Choose another") { _,_->selectSeason(r,show) }.setPositiveButton("Review match") { _,_->confirmMatch(r,exact) }.show()
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
        BridgeDialogBuilder(this).setTitle("Save local match?").setMessage(item.label() + "\nTrakt ID: " + item.id + "\nThis does not mark it watched on Trakt.").setNegativeButton("Cancel",null).setPositiveButton("Save match") { _,_ -> history.match(r.id,item); AutoMatchSuggestions.confirmed(this,r,item); AutoMatchSuggestions.remove(this,r.id); ViewingNotifications.cancel(this,r.id); refreshHistory() }.show()
    }
    private fun refresh() {
        val raw=prefs.getString("sessions","[]") ?: "[]"
        val manager=getSystemService(android.app.NotificationManager::class.java)
        val access=if(android.os.Build.VERSION.SDK_INT>=27) manager.isNotificationListenerAccessGranted(android.content.ComponentName(this,PlaybackListener::class.java)) else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(":").any { android.content.ComponentName.unflattenFromString(it)==android.content.ComponentName(this,PlaybackListener::class.java) }
        val last=prefs.getLong("last_sample",0)
        val age=if(last>0) ((System.currentTimeMillis()-last)/1000).coerceAtLeast(0) else -1
        val info=listOf("Sampling enabled: "+prefs.getBoolean("enabled",false),"Notification listener access: "+access,"Listener connected (last reported): "+prefs.getBoolean("listener_connected",false),"Last sample: "+(if(age<0) "never" else age.toString()+" sec ago"),"Selected apps: "+(prefs.getStringSet("allowed",emptySet())?.size ?: 0),"Review notifications permitted: "+ViewingNotifications.enabled(this),"Selected media sessions: "+prefs.getInt("session_count",0),"Tracked sessions: "+prefs.getInt("tracked_session_count",0),"Last error: "+prefs.getString("sample_error","none")).joinToString("\n")
        val current=runCatching { JSONArray(raw).toString(2) }.getOrDefault("[]")
        val previous=runCatching { JSONArray(prefs.getString("last_nonempty_sessions","[]") ?: "[]").toString(2) }.getOrDefault("[]")
        output.text=info+"\n\nCurrent selected media sessions:\n"+current+"\n\nLast nonempty selected media sessions:\n"+previous
    }
}
