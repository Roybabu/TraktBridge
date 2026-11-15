package com.traktbridge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

/** YouTube uses control IDs only; Hotstar player labels require separate diagnostic consent. */
class TitleAccessibility: AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private var lastScan=0L
    private val handler=android.os.Handler(android.os.Looper.getMainLooper())
    private val poll=object:Runnable {
        override fun run() {
            if(!HotstarProbe.enabled(this@TitleAccessibility)) return
            try {
                scan(HotstarPlayback.PACKAGE,true,-1)
            } catch(e:Exception) {
                HotstarProbe.screen(this@TitleAccessibility,emptySet(),status="poll-error:"+e.javaClass.simpleName)
            } finally {
                if(HotstarProbe.enabled(this@TitleAccessibility)) handler.postDelayed(this,3000L)
            }
        }
    }
    private fun refreshPoll() {
        handler.removeCallbacks(poll)
        if(HotstarProbe.enabled(this)) handler.post(poll)
    }
    private val preferenceListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key=="hotstar_probe_until" || key=="privacy_lock") refreshPoll()
    }
    override fun onServiceConnected() {
        DebugTrace.init(this)
        prefs.edit().putBoolean("title_connected",true).apply()
        DebugTrace.event("title","YouTube player identification connected")
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshPoll()
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        val pkg=event?.packageName?.toString() ?: return
        val hotstar=pkg==HotstarPlayback.PACKAGE && HotstarProbe.enabled(this)
        if(!hotstar && (!prefs.getBoolean("enabled",false) || !prefs.getBoolean("title_optin",false) || pkg!=YouTubePlayback.PACKAGE || pkg !in PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty()))) return
        scan(pkg,hotstar,event.eventType)
    }
    private fun scan(pkg:String,hotstar:Boolean,eventType:Int) {
        if(hotstar && !HotstarProbe.enabled(this)) return
        val now=System.currentTimeMillis()
        if(eventType!=-1 && now-lastScan in 0..249) return
        val root=rootInActiveWindow ?: run { if(hotstar) HotstarProbe.screen(this,emptySet(),emptySet(),eventType,emptySet(),"no-root"); return }
        if(root.packageName?.toString()!=pkg) {
            if(hotstar) HotstarProbe.screen(this,emptySet(),eventType=eventType,status="non-hotstar-window")
            return
        }
        lastScan=now
        val ids=linkedSetOf<String>()
        val labels=linkedSetOf<String>()
        val visible=linkedSetOf<String>()
        var count=0
        fun walk(node:AccessibilityNodeInfo,depth:Int) {
            if(depth>20 || ++count>350 || node.packageName?.toString()!=pkg || node.isPassword || node.isEditable) return
            if(node.isVisibleToUser) {
                val id=PlayerRules.id(node.viewIdResourceName.orEmpty())
                if(id.matches(Regex("[a-z0-9_]{1,80}"))) ids.add(id)
                if(hotstar) {
                    val role=if(node.isHeading) "heading" else if(node.isSelected) "selected" else if(node.isClickable) "clickable" else "text"
                    listOf(node.text,node.contentDescription).forEach { value -> value?.toString()?.take(160)?.let { if(visible.size<80) visible.add(role+"|"+id+"|"+it) } }
                }
                if(hotstar && (node.isHeading || id.matches(Regex(".*(player|playback|video|episode|season|content|series|show|detail|header)_(title|subtitle|name|description).*")))) {
                    node.text?.toString()?.take(160)?.let { labels.add(it) }
                    node.contentDescription?.toString()?.take(160)?.let { labels.add(it) }
                }
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        if(hotstar) { HotstarProbe.screen(this,ids,labels,eventType,visible,"nodes="+count+" source="+(if(eventType==-1) "poll" else "callback")); return }
        val mode=YouTubePlayback.mode(ids,PlaybackSegment.UNKNOWN)
        val segment=if(mode==YouTubePlayback.Mode.ADVERTISEMENT) PlaybackSegment.ADVERTISEMENT else PlaybackSegment.UNKNOWN
        prefs.edit().putString("segment:"+pkg,segment.name).putLong("segment_time:"+pkg,now).apply()
        if(YouTubePlayback.observe(mode,now)) PlaybackListener.wake()
    }
    override fun onInterrupt() { YouTubePlayback.clear(); DebugTrace.event("title","interrupted") }
    override fun onDestroy() { handler.removeCallbacks(poll); prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener); YouTubePlayback.clear(); prefs.edit().putBoolean("title_connected",false).apply(); super.onDestroy() }
    companion object {
        fun segment(context:Context,pkg:String,now:Long):PlaybackSegment {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(pkg!=YouTubePlayback.PACKAGE || !p.getBoolean("enabled",false) || !p.getBoolean("title_optin",false) || pkg !in PlaybackDetectors.selected(p.getStringSet("allowed",emptySet()).orEmpty()) || now-p.getLong("segment_time:"+pkg,0) !in 0..6000) return PlaybackSegment.UNKNOWN
            return runCatching { PlaybackSegment.valueOf(p.getString("segment:"+pkg,"UNKNOWN").orEmpty()) }.getOrDefault(PlaybackSegment.UNKNOWN)
        }
    }
}
