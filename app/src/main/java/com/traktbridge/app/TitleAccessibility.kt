package com.traktbridge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.os.Build
import android.graphics.Rect
import android.graphics.Bitmap

class TitleAccessibility: AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private var lastScan=0L
    override fun onServiceConnected() {
        DebugTrace.init(this)
        prefs.edit().putBoolean("title_connected",true).apply()
        DebugTrace.event("title","accessibility connected")
    }
    private fun maybeOcr(pkg:String,root:AccessibilityNodeInfo,now:Long) {
        if(Build.VERSION.SDK_INT<34 || prefs.getString("ocr_target","")!=pkg || now !in prefs.getLong("ocr_after",Long.MAX_VALUE)..prefs.getLong("ocr_until",0)) return
        // Consume the one-shot request before taking the image. Never retry a secure window.
        prefs.edit().remove("ocr_target").remove("ocr_after").remove("ocr_until").putString("ocr_status","Scanning selected player").apply()
        val generation=prefs.getLong("ocr_generation",0)
        fun validRequest()=prefs.getBoolean("enabled",false) && prefs.getBoolean("title_optin",false) && prefs.getLong("ocr_generation",0)==generation && pkg in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())
        takeScreenshotOfWindow(root.windowId,mainExecutor,object:TakeScreenshotCallback {
            override fun onFailure(errorCode:Int) {
                if(!validRequest()) return
                val status=if(errorCode==ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) "Screen capture blocked by the player" else "Screen capture unavailable ("+errorCode+")"
                prefs.edit().putString("ocr_status",status).apply()
                DebugTrace.event("ocr","capture unavailable code="+errorCode)
            }
            override fun onSuccess(result:ScreenshotResult) {
                val buffer=result.hardwareBuffer
                if(!validRequest()) { buffer.close(); return }
                val bitmap=try {
                    val hardware=Bitmap.wrapHardwareBuffer(buffer,result.colorSpace)
                    try { hardware?.copy(Bitmap.Config.ARGB_8888,false) } finally { hardware?.recycle() }
                } finally { buffer.close() }
                if(bitmap==null) { prefs.edit().putString("ocr_status","Image unavailable").apply(); return }
                PlayerOcr.scan(bitmap) { outcome ->
                    if(!validRequest()) return@scan
                    outcome.fold({ choices ->
                        val array=org.json.JSONArray()
                        choices.forEach { c -> array.put(org.json.JSONObject().put("title",c.title).put("detail",c.detail).put("position",c.position).put("duration",c.duration)) }
                        prefs.edit().putString("ocr_choices",array.toString()).putString("ocr_source",pkg).putLong("ocr_captured",now).putString("ocr_status",if(choices.isEmpty()) "No title candidates found" else "Review "+choices.size+" OCR candidates").apply()
                        DebugTrace.event("ocr","candidateCount="+choices.size)
                    },{ prefs.edit().putString("ocr_status","OCR unavailable").apply(); DebugTrace.event("ocr","recognition failed") })
                }
            }
        })
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(!prefs.getBoolean("enabled",false) || !prefs.getBoolean("title_optin",false)) return
        val pkg=event?.packageName?.toString() ?: return
        if(pkg !in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())) return
        val now=System.currentTimeMillis()
        if(now-lastScan<(if(pkg==YouTubeTitleProbe.PACKAGE) 250L else 1000L)) return
        lastScan=now
        val root=rootInActiveWindow ?: return
        if(root.packageName?.toString()!=pkg) return
        maybeOcr(pkg,root,now)
        val window=Rect().also { root.getBoundsInScreen(it) }
        if(window.width()<=0 || window.height()<=0) return
        val labels=mutableListOf<ScreenLabel>()
        val ids=linkedSetOf<String>()
        val visibleIds=linkedSetOf<String>()
        val classes=linkedSetOf<String>()
        var player=false
        var count=0
        var textNodes=0
        fun walk(node: AccessibilityNodeInfo,depth:Int) {
            if(depth>20 || ++count>350 || node.packageName?.toString()!=pkg || node.isPassword || node.isEditable) return
            val id=PlayerRules.id(node.viewIdResourceName.orEmpty())
            val cls=node.className?.toString()?.substringAfterLast('.').orEmpty()
            if(id.matches(Regex("[a-z0-9_]{1,80}"))) ids.add(id)
            if(cls.matches(Regex("[A-Za-z0-9_]{1,80}"))) classes.add(cls)
            if(PlayerRules.isPlayer(id)) player=true
            if(node.isVisibleToUser) {
                if(id.isNotBlank()) visibleIds.add(id)
                val value=(node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                val seek=cls=="SeekBar" || id.contains("seekbar") || id.contains("seek_bar") || id=="exo_progress"
                if(value.isNotBlank() || seek) {
                    if(value.isNotBlank()) textNodes++
                    val heading=Build.VERSION.SDK_INT>=28 && node.isHeading
                    val button=cls=="Button" || node.isClickable
                    val rect=Rect().also { node.getBoundsInScreen(it) }
                    if(value.length<=160 && rect.width()>0 && rect.height()>0) labels.add(ScreenLabel(id,value,heading,button,
                        (rect.exactCenterX()-window.left)/window.width().toDouble(),(rect.exactCenterY()-window.top)/window.height().toDouble(),
                        rect.width()/window.width().toDouble(),rect.height()/window.height().toDouble(),seek))
                }
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        player=ContentRecognition.playerContext(labels,player)
        if(pkg==YouTubeTitleProbe.PACKAGE) YouTubeProbeState.observeScreen(now,labels,player)
        val segment=SegmentDetection.screen(labels,player)
        if(pkg==YouTubeTitleProbe.PACKAGE && YouTubePlayback.observe(YouTubePlayback.mode(visibleIds,segment),now)) PlaybackListener.wake()
        prefs.edit().putString("segment:"+pkg,segment.name).putLong("segment_time:"+pkg,now).apply()
        val hint=ContentRecognition.detect(pkg,labels,player)
        val timing=ContentRecognition.timing(labels,player)
        if(timing!=null && hint!=null && hint.kind!="details") prefs.edit().putLong("screen_position:"+pkg,timing.position).putLong("screen_duration:"+pkg,timing.duration).putLong("screen_time:"+pkg,now).putString("screen_title:"+pkg,hint.title).apply()
        DebugTrace.event("title","package="+pkg+" player="+player+" textNodes="+textNodes+" candidate="+(hint?.kind ?: "none"))
        if(prefs.getBoolean("player_inspector",false)) {
            // Structural metadata only: no text, content descriptions, credentials or URLs in reports.
            DebugTrace.event("inspector","package="+pkg+" nodes="+count+" textNodes="+textNodes+" ids="+ids.take(60).joinToString(","))
            DebugTrace.event("inspector","package="+pkg+" classes="+classes.take(25).joinToString(",")+" headings="+labels.count { it.heading }+" buttons="+labels.count { it.button }+" top="+labels.count { it.y in 0.0..0.30 }+" bottom="+labels.count { it.y in 0.70..1.0 }+" seek="+labels.count { it.seek }+" timerPair="+(timing!=null))
        }
        if(hint==null) return
        prefs.edit().putString("title_hint:"+pkg,hint.title).putString("title_detail:"+pkg,hint.detail).putLong("title_time:"+pkg,now).apply()
    }
    override fun onInterrupt() { DebugTrace.event("title","interrupted") }
    override fun onDestroy() { YouTubePlayback.clear(); prefs.edit().putBoolean("title_connected",false).apply(); super.onDestroy() }
    companion object {
        fun segment(context:Context,pkg:String,now:Long):PlaybackSegment {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(!p.getBoolean("enabled",false) || !p.getBoolean("title_optin",false) || pkg !in (p.getStringSet("allowed",emptySet()) ?: emptySet()) || now-p.getLong("segment_time:"+pkg,0) !in 0..6000) return PlaybackSegment.UNKNOWN
            return runCatching { PlaybackSegment.valueOf(p.getString("segment:"+pkg,"UNKNOWN").orEmpty()) }.getOrDefault(PlaybackSegment.UNKNOWN)
        }
        fun timing(context:Context,pkg:String,title:String,now:Long):ContentRecognition.PlayerTime? {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(!p.getBoolean("title_optin",false) || title.isBlank() || p.getString("screen_title:"+pkg,"")!=title || now-p.getLong("screen_time:"+pkg,0) !in 0..8000) return null
            val pos=p.getLong("screen_position:"+pkg,-1); val dur=p.getLong("screen_duration:"+pkg,0)
            return if(PlaybackPosition.valid(pos,dur)) ContentRecognition.PlayerTime(pos,dur) else null
        }
        fun hint(context:Context,pkg:String,start:Long,end:Long):Pair<String,String>? {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(!p.getBoolean("title_optin",false) || !ContentRecognition.allowAutomaticScreenTitle(pkg) || pkg !in (p.getStringSet("allowed",emptySet()) ?: emptySet())) return null
            val t=p.getLong("title_time:"+pkg,0)
            if(t !in start..end) return null
            val title=p.getString("title_hint:"+pkg,"").orEmpty()
            return if(title.isBlank()) null else title to p.getString("title_detail:"+pkg,"").orEmpty()
        }
    }
}
