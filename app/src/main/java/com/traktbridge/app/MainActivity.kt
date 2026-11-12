package com.traktbridge.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.widget.*
import android.view.ViewGroup
import org.json.JSONArray

class MainActivity : Activity() {
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
    private val ink=0xfff1f0f6.toInt()
    private val muted=0xffa9a8ba.toInt()
    private val violet=0xffb6a0ff.toInt()
    private val surface=0xff1b1e2a.toInt()
    private val bg=0xff0d0f16.toInt()
    private fun showAuth(message: String = auth.status()) { account.text = message }
    private fun handleCallback(i: Intent) {
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.host != "callback") return
        i.data = null
        showAuth("Connecting to Trakt…")
        auth.complete(uri) { showAuth(it) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleCallback(intent); handleReview(intent); handleShared(intent) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==57 && resultCode==RESULT_OK) data?.data?.let { inspectVideo(it) }
        if(requestCode==58 && resultCode==RESULT_OK) data?.data?.let { inspectImport(it) }
    }
    private var importSource="com.netflix.mediaclient"
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
                HistoryImport.parse(text,source)
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
                prefs.edit().remove("ocr_choices").remove("ocr_source").remove("ocr_captured").putString("ocr_target",pkg).putLong("ocr_after",now+8000).putLong("ocr_until",now+45000).putString("ocr_status","Armed").apply()
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
        if(i.action!=Intent.ACTION_SEND) return
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
            val recent=history.records(0).filter { it.status=="pending" && it.traktId==0L && now-it.ended in 0..1800000L && !it.id.startsWith("shared:") && (candidate.source.isBlank() || candidate.source=="local.file" && it.source in setOf("org.videolan.vlc","com.mxtech.videoplayer.ad","com.mxtech.videoplayer.pro") || it.source==candidate.source) }.take(10)
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
        if(i.action==ViewingNotifications.USAGE_REVIEW) { i.action=null; reviewUsage(); return }
        if (i.action != ViewingNotifications.REVIEW) return
        val uri = i.data ?: return
        if (uri.scheme != "traktbridge" || uri.host != "review") return
        val id = uri.lastPathSegment ?: return
        i.action = null; i.data = null
        ViewingNotifications.cancel(this,id)
        val r = history.record(id) ?: return
        if (r.status == "pending" || r.status == "reviewed") identify(r)
    }
    private val apps = PlayerRules.apps
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugTrace.init(this)
        DebugTrace.event("activity","main opened")
        tab=savedInstanceState?.getInt("tab") ?: 0
        shell=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg) }
        shell.setOnApplyWindowInsetsListener { v,insets -> v.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets }
        setContentView(shell)
        val header=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(16),dp(24),dp(12)); addView(txt("TRAKTBRIDGE",12,violet)); addView(txt("Your viewing companion",24,ink,true)) }
        shell.addView(header)
        val frame=android.widget.FrameLayout(this)
        shell.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(24)) }
        settingsPage=ScrollView(this).apply { addView(root) }; frame.addView(settingsPage)
        dashboard=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(24)) }
        contentPage=ScrollView(this).apply { addView(dashboard) }; frame.addView(contentPage)
        nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(dp(4),dp(6),dp(4),dp(6)); setBackgroundColor(surface) }; shell.addView(nav)
        listOf("Home","History","Review","Stats","Settings").forEachIndexed { i,name -> nav.addView(TextView(this).apply { text=name; textSize=12f; gravity=android.view.Gravity.CENTER; minHeight=dp(48); setOnClickListener { showTab(i) } },LinearLayout.LayoutParams(0,-2,1f)) }
        fun label(s: String) { root.addView(txt(s,15,muted)) }
        fun button(s: String, action: () -> Unit) { root.addView(actionButton(s,action)) }
        label("SETTINGS • Account and tracking")
        button("Import streaming viewing history (CSV)") { chooseImportProvider() }
        button("Identify local video file") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/*"),57) }
        button("Identify shared movie link") { val field=EditText(this).apply { hint="Paste shared title or link" }; android.app.AlertDialog.Builder(this).setTitle("Identify shared content").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Continue") { _,_-> presentShared(SharedContent.fromText(field.text.toString())) }.show() }
        button("Scan player title (one-shot OCR)") { armOcr() }
        button("Review OCR result") { reviewOcr() }
        button("Clear OCR result") { prefs.edit().remove("ocr_target").remove("ocr_after").remove("ocr_until").remove("ocr_choices").remove("ocr_source").remove("ocr_captured").remove("ocr_status").apply() }
        button("Connect media servers") { serverSettings() }
        root.addView(Switch(this).apply { text="Automatically reuse confirmed local title mappings"; isChecked=prefs.getBoolean("learned_auto",false); setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("learned_auto",checked).apply() } })
        button("Clear learned title mappings") { android.app.AlertDialog.Builder(this).setTitle("Clear learned mappings?").setMessage("Existing viewing history and Trakt matches stay unchanged.").setNegativeButton("Cancel",null).setPositiveButton("Clear") { _,_->history.clearLearning() }.show() }
        button("Open diagnostic recorder") { startActivity(Intent(this,DebugActivity::class.java)) }
        auth = TraktAuth(applicationContext)
        account = TextView(this).apply { textSize = 16f }
        root.addView(account)
        showAuth()
        button("Connect Trakt") { try { startActivity(Intent(Intent.ACTION_VIEW, auth.begin())); showAuth("Complete sign-in in your browser") } catch (_: Exception) { showAuth("Unable to open sign-in") } }
        button("Check Trakt connection") { showAuth("Checking…"); auth.check { showAuth(it) } }
        button("Disconnect Trakt locally") { auth.disconnect { showAuth(it) } }
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
        label("Optional show and episode title suggestions: reads only recognized player title controls in selected apps. No screenshots or automatic uploads.")
        button("Enable title detection (Accessibility)") {
            android.app.AlertDialog.Builder(this).setTitle("Optional title detection").setMessage("Android Accessibility can expose screen content. TraktBridge checks selected video apps for player titles and video detail headings near play actions. Suggestions stay local and are never uploaded automatically. Enable only if you agree.").setNegativeButton("Cancel",null).setPositiveButton("Agree and open settings") { _,_ -> prefs.edit().putBoolean("title_optin",true).apply(); startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
        }
        button("Disable title detection") {
            val edit=prefs.edit().putBoolean("title_optin",false).remove("ocr_target").remove("ocr_after").remove("ocr_until").remove("ocr_choices").remove("ocr_source").remove("ocr_captured").remove("ocr_status")
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
        handleCallback(intent)
        handleReview(intent)
        handleShared(intent)
    }
    private fun startTracking() {
        runCatching {
            startForegroundService(Intent(this,TrackingService::class.java))
            android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(this,PlaybackListener::class.java))
        }.onFailure { DebugTrace.failure("foreground",it); Toast.makeText(this,"Unable to start tracking",Toast.LENGTH_LONG).show() }
    }
    override fun onResume() { super.onResume(); if (::output.isInitialized) refresh(); if (::historyList.isInitialized) refreshHistory() }
    override fun onDestroy() { if (::history.isInitialized) history.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("tab",tab); super.onSaveInstanceState(outState) }
    private fun dp(n:Int)= (n*resources.displayMetrics.density+0.5f).toInt()
    private fun rounded(color:Int,radius:Int=16)=android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat() }
    private fun txt(value:String,size:Int=15,color:Int=ink,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size.toFloat(); setTextColor(color); if(bold) setTypeface(null,android.graphics.Typeface.BOLD)
    }
    private fun actionButton(label:String,action:()->Unit)=Button(this).apply {
        text=label; isAllCaps=false; setTextColor(ink); background=rounded(0xff302a48.toInt(),12); minHeight=dp(46)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(6) }; setOnClickListener { action() }
    }
    private fun panel(parent:LinearLayout):LinearLayout=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; background=rounded(surface); setPadding(dp(16),dp(16),dp(16),dp(16))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun section(parent:LinearLayout,title:String) { parent.addView(txt(title,20,ink,true),LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(12) }) }
    private fun showTab(index:Int) {
        tab=index; settingsPage.visibility=if(index==4) android.view.View.VISIBLE else android.view.View.GONE
        contentPage.visibility=if(index==4) android.view.View.GONE else android.view.View.VISIBLE
        for(i in 0 until nav.childCount) (nav.getChildAt(i) as TextView).apply { setTextColor(if(i==index) violet else muted); background=rounded(if(i==index) 0xff302a48.toInt() else surface,12) }
        refreshHistory(); contentPage.scrollTo(0,0)
    }
    private fun refreshHistory() {
        if(!::dashboard.isInitialized || tab==4) return
        dashboard.removeAllViews()
        val records=history.records(0)
        val groups=ViewingGroups.build(records)
        val pending=groups.count { it.queued || (it.pending && !it.confirmed) }
        when(tab) {
            0 -> {
                val status=panel(dashboard)
                val age=System.currentTimeMillis()-prefs.getLong("last_sample",0)
                val live=prefs.getBoolean("enabled",false) && age in 0..20000
                status.addView(txt(if(live) "●  TRACKING ACTIVE" else "○  TRACKING NOT ACTIVE",12,if(live) 0xff6ee7b7.toInt() else 0xfffbbf24.toInt(),true))
                status.addView(txt(if(live) "Ready to track selected apps" else "Start playback tracking",22,ink,true))
                status.addView(txt("Only confirmed completed watches sync to Trakt.",14,muted))
                status.addView(actionButton(if(live) "Tracking settings" else "Start tracking") { if(live) showTab(4) else { prefs.edit().putBoolean("enabled",true).apply(); startTracking(); showTab(4) } })
                section(dashboard,"Now watching")
                val active=groups.filter { it.active }
                if(active.isEmpty()) panel(dashboard).addView(txt("No verified active playback. Play a video in a selected app.",15,muted)) else active.take(2).forEach { titleCard(dashboard,it,false) }
                section(dashboard,"Review queue")
                val q=panel(dashboard); q.addView(txt("$pending awaiting action",22,ink,true)); q.addView(actionButton("Open review queue") { showTab(2) }); q.addView(actionButton("Review app activity") { reviewUsage() })
                section(dashboard,"Recently watched")
                val recent=groups.filter { !it.active }.take(5)
                if(recent.isEmpty()) panel(dashboard).addView(txt("Your viewing history will appear here.",15,muted)) else recent.forEach { titleCard(dashboard,it,false) }
            }
            1,2 -> {
                section(dashboard,if(tab==1) "Viewing history" else "Needs your review")
                dashboard.addView(actionButton("Refresh") { refreshHistory() })
                if(tab==2) { dashboard.addView(actionButton("Review recent app activity") { reviewUsage() }); dashboard.addView(actionButton("Retry approved sync queue") { HistorySync.schedule(this); refreshHistory() }) }
                val items=if(tab==1) groups else groups.filter { it.queued || (it.pending && !it.confirmed) }
                if(items.isEmpty()) panel(dashboard).addView(txt(if(tab==1) "No viewing records yet." else "You're all caught up.",16,muted))
                items.forEach { titleCard(dashboard,it,true) }
            }
            3 -> {
                section(dashboard,"Viewing activity")
                val stats=panel(dashboard)
                val seconds=records.sumOf { it.watched }/1000
                stats.addView(txt(String.format(java.util.Locale.US,"%.1f hours",seconds/3600.0),32,violet,true))
                stats.addView(txt("Sampled watch time • all local sessions",14,muted))
                stats.addView(txt(groups.count { it.confirmed && it.representative.matchType=="movie" }.toString()+" movies synced  •  "+groups.count { it.confirmed && it.representative.matchType=="episode" }+" episodes synced",16))
                stats.addView(txt("$pending awaiting action",15,muted))
                section(dashboard,"Watch time by platform")
                val totals=records.groupBy { it.source }.mapValues { it.value.sumOf { r -> r.watched } }.toList().sortedByDescending { it.second }
                val max=totals.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                val chart=panel(dashboard)
                if(totals.isEmpty()) chart.addView(txt("No sampled activity yet.",15,muted))
                totals.forEach { (pkg,time) ->
                    chart.addView(txt(appLabel(pkg)+"  •  "+(time/60000)+" min",14))
                    chart.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(time.toDouble()/max*100).toInt(); progressTintList=android.content.res.ColorStateList.valueOf(violet) },LinearLayout.LayoutParams(-1,dp(12)).apply { bottomMargin=dp(14) })
                }
                chart.addView(txt("App-activity estimates are excluded from watch time.",13,muted))
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
        val poster=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0xff343448.toInt()); contentDescription="Title poster" }
        row.addView(poster,LinearLayout.LayoutParams(dp(94),dp(141)).apply { rightMargin=dp(14) })
        if(r.traktId>0) Artwork.load(this,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,poster)
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },18,ink,true))
        details.addView(txt(g.platforms.joinToString(" • ") { appLabel(it) },13,muted))
        details.addView(txt(g.sessions.size.toString()+" viewing session(s) • "+java.text.DateFormat.getDateInstance().format(java.util.Date(g.latest.ended)),13,muted))
        val completion=when { g.confirmed -> "Finished • synced to Trakt"; g.queued -> "Completion approved • sync pending"; g.active -> "Currently watching"; g.nearEnd -> "Near end • confirm completion"; g.positionMs>=0 -> "In progress"; else -> "Completion unknown" }
        details.addView(txt(completion,14,if(g.confirmed) 0xff6ee7b7.toInt() else 0xfffbbf24.toInt(),true))
        details.addView(txt("Total sampled watch time: "+if(g.sampledMs>0) durationText(g.sampledMs) else "Unknown",13,muted))
        if(g.durationMs>0 && g.positionMs>=0) {
            details.addView(txt(durationText(g.positionMs)+" / "+durationText(g.durationMs)+" • "+durationText(g.remainingMs)+" left",13,ink))
            details.addView(txt("Last observed position • "+when(g.positionSource) { "screen"->"visible player timer estimate"; "server"->"server reported"; "media"->"media session reported"; else->"source unverified" },12,muted))
            details.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(g.positionMs.toDouble()/g.durationMs*100).toInt().coerceIn(0,100); progressTintList=android.content.res.ColorStateList.valueOf(violet) })
        } else { if(g.catalogueRuntimeMs>0) details.addView(txt("Catalogue runtime: "+durationText(g.catalogueRuntimeMs),13,muted)); details.addView(txt("Remaining time unavailable • player position unknown",13,muted)) }
        if(g.bufferingMs>0 || g.pausedMs>0 || g.seeks>0 || g.uncertainMs>0) details.addView(txt("Paused: "+durationText(g.pausedMs)+" • Buffering: "+durationText(g.bufferingMs)+" • Seeks: "+g.seeks+" • Uncertain: "+durationText(g.uncertainMs),12,muted))
        if(r.traktId<=0) details.addView(txt("Identify exact title to combine sessions.",12,muted))
        if(expanded || g.pending || g.queued) card.addView(actionButton(if(g.confirmed) "View sessions" else "Review / view sessions") { showSessions(g) })
        else card.addView(actionButton("View sessions") { showSessions(g) })
    }
    private fun showSessions(g:ViewingTitle) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(12),dp(12),dp(12)) }
        body.addView(txt("Original viewing sessions are retained. One confirmed title appears once in history.",14,muted))
        g.sessions.forEach { recordCard(body,it,true,g.confirmed || g.queued) }
        android.app.AlertDialog.Builder(this).setTitle(g.representative.matchLabel.ifBlank { g.representative.title.ifBlank { "Viewing sessions" } }).setView(ScrollView(this).apply { addView(body) }).setPositiveButton("Close") { _,_->refreshHistory() }.show()
    }
    private fun recordCard(parent:LinearLayout,r:WatchRecord,expanded:Boolean,completionAlreadyRecorded:Boolean=false) {
        val card=panel(parent)
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.TOP }
        card.addView(row)
        val poster=ImageView(this).apply { scaleType=ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.ic_media_play); setBackgroundColor(0xff343448.toInt()); contentDescription="Poster artwork" }
        row.addView(poster,LinearLayout.LayoutParams(dp(94),dp(141)).apply { rightMargin=dp(14) })
        if(r.traktId>0) Artwork.load(this,if(r.matchType=="episode") "show" else "movie",if(r.matchType=="episode") r.showId else r.traktId,poster)
        val details=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        row.addView(details,LinearLayout.LayoutParams(0,-2,1f))
        details.addView(txt(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } },18,ink,true))
        if(r.subtitle.isNotBlank()) details.addView(txt(r.subtitle,13,muted))
        details.addView(txt(appLabel(r.source)+" • "+java.text.DateFormat.getDateInstance().format(java.util.Date(r.started)),13,muted))
        val status=when(r.status) { "synced"->"Synced to Trakt"; "active"->"Watching"; "pending"->"Needs review"; "reviewed"->"Ready to confirm"; else->r.status.replaceFirstChar { it.uppercase() } }
        details.addView(txt(status,13,if(r.status=="synced") 0xff6ee7b7.toInt() else 0xfffbbf24.toInt(),true))
        details.addView(txt(if(r.watched<=0) "Watch time unknown" else durationText(r.watched)+" sampled",13,muted))
        if(r.seeks>0 || r.bufferingMs>0 || r.pausedMs>0 || r.uncertainMs>0) details.addView(txt("Paused: "+durationText(r.pausedMs)+" • Buffering: "+durationText(r.bufferingMs)+" • Seeks: "+r.seeks+" (rewinds "+r.rewinds+") • Uncertain: "+durationText(r.uncertainMs),12,muted))
        if(r.duration>0 && r.position>=0) details.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { progress=(r.position.toDouble()/r.duration*100).toInt().coerceIn(0,100); progressTintList=android.content.res.ColorStateList.valueOf(violet) })
        if(!expanded) { card.addView(actionButton("View details") { android.app.AlertDialog.Builder(this).setTitle("Viewing details").setMessage(r.matchLabel.ifBlank { r.title.ifBlank { "Unidentified video" } }+"\n"+r.subtitle).setNegativeButton("Close",null).setPositiveButton("Open history") { _,_->showTab(1) }.show() }); return }
            if (r.traktId > 0) card.addView(TextView(this).apply { text = "Matched: " + r.matchLabel + " • Trakt ID " + r.traktId })
            if(r.id.startsWith("import:") && r.status=="pending") card.addView(actionButton("Reconcile with local history") { reconcileImported(r) })
            if (r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = if (r.traktId > 0) "Change Trakt match" else "Identify on Trakt"; setOnClickListener { identify(r) } })
            if (r.traktId > 0 && r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = "Remove Trakt match"; setOnClickListener { history.clearMatch(r.id); refreshHistory() } })
            if (r.status == "reviewed" && r.traktId > 0 && !completionAlreadyRecorded) card.addView(Button(this).apply { text = "Mark watched on Trakt"; setOnClickListener { approveUpload(r) } })
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
            if (r.status in setOf("pending","reviewed")) card.addView(Button(this).apply { text = "Delete viewing record"; setOnClickListener { android.app.AlertDialog.Builder(this@MainActivity).setTitle("Delete this local record?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_ -> history.discard(r.id); ViewingNotifications.cancel(this@MainActivity,r.id); refreshHistory() }.show() } })

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
    private fun approveUpload(r: WatchRecord) {
        android.app.AlertDialog.Builder(this).setTitle("Mark as fully watched?")
            .setMessage(r.matchLabel + "\nThis adds a completed watch to Trakt at the recorded end time. Only confirm if you finished the movie or episode. Partial viewing stays local. For app-activity records the end time is only the app exit time.")
            .setNegativeButton("Cancel",null).setPositiveButton("Verify account") { _,_ ->
                Thread {
                    val result=runCatching { HistorySync.accountForApproval(applicationContext) }
                    runOnUiThread {
                        if(!isFinishing && !isDestroyed) result.fold({ accountId ->
                            android.app.AlertDialog.Builder(this).setTitle("Approve upload to connected Trakt account?")
                                .setMessage(r.matchLabel + "\nAccount ID: " + accountId + "\nApproved records will retry when online.")
                                .setNegativeButton("Cancel",null).setPositiveButton("Approve and sync") { _,_ ->
                                    if(history.enqueue(r.id,accountId)) HistorySync.schedule(this) else Toast.makeText(this,"This title already has a completion or pending upload for this account.",Toast.LENGTH_LONG).show()
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
        val learned=history.learned(r)
        if(learned!=null && r.traktId==0L) box.addView(actionButton("Use learned match: "+learned.label()) { confirmMatch(r,learned) })
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Identify viewing record").setView(box).setNegativeButton("Cancel",null).setNeutralButton("TV / Anime",null).setPositiveButton("Movie",null).create()
        dialog.setOnShowListener {
            fun launch(type: String) {
                val q = field.text.toString().trim()
                if (q.isBlank()) { field.error = "Enter a title"; return }
                dialog.dismiss()
                lookup("Searching Trakt", { done -> TraktCatalogue.search(q,type,done) }) { results: List<CatalogueItem> ->
                    val ranked=RecognitionEngine.rank(q,r.subtitle,r.duration,results)
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
        request { result ->
            if (!isFinishing && !isDestroyed && progress.isShowing) {
                progress.dismiss()
                result.fold(success, { android.app.AlertDialog.Builder(this).setTitle("Catalogue unavailable").setMessage(it.message?.take(200) ?: "Try again later.").setPositiveButton("OK",null).show() })
            }
        }
    }
    private fun choose(title: String, labels: List<String>, selected: (Int) -> Unit) {
        if (labels.isEmpty()) { android.app.AlertDialog.Builder(this).setTitle(title).setMessage("No results. Try another title or spelling.").setPositiveButton("OK",null).show(); return }
        android.app.AlertDialog.Builder(this).setTitle(title).setItems(labels.toTypedArray()) { _, index -> selected(index) }.setNegativeButton("Cancel",null).show()
    }
    private fun selectSuggestedEpisode(r:WatchRecord,show:CatalogueItem) {
        val ep=RecognitionEngine.episodeOf(r.title+" "+r.subtitle)
        if(ep==null) { selectSeason(r,show); return }
        lookup("Checking detected episode", { done -> TraktCatalogue.episodes(show,ep.season,done) }) { episodes:List<CatalogueItem> ->
            val exact=episodes.singleOrNull { it.episode==ep.episode }
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
        val info=listOf("Sampling enabled: "+prefs.getBoolean("enabled",false),"Notification listener access: "+access,"Listener connected (last reported): "+prefs.getBoolean("listener_connected",false),"Last sample: "+(if(age<0) "never" else age.toString()+" sec ago"),"Selected apps: "+(prefs.getStringSet("allowed",emptySet())?.size ?: 0),"Usage fallback: "+(prefs.contains("usage_since") && UsageFallback.allowed(this)),"Review notifications permitted: "+ViewingNotifications.enabled(this),"All active media sessions: "+prefs.getInt("session_count",0),"Last error: "+prefs.getString("sample_error","none")).joinToString("\n")
        val current=runCatching { JSONArray(raw).toString(2) }.getOrDefault("[]")
        val previous=runCatching { JSONArray(prefs.getString("last_nonempty_sessions","[]") ?: "[]").toString(2) }.getOrDefault("[]")
        output.text=info+"\n\nCurrent selected media sessions:\n"+current+"\n\nLast nonempty selected media sessions:\n"+previous
    }
}
