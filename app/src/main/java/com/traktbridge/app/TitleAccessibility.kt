package com.traktbridge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

/** YouTube uses control IDs only; Hotstar player labels require separate diagnostic consent. */
class TitleAccessibility: AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private var lastScan=0L
    override fun onServiceConnected() {
        DebugTrace.init(this)
        prefs.edit().putBoolean("title_connected",true).apply()
        DebugTrace.event("title","YouTube player identification connected")
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        val pkg=event?.packageName?.toString() ?: return
        val hotstar=pkg==HotstarPlayback.PACKAGE && HotstarProbe.enabled(this)
        if(!hotstar && (!prefs.getBoolean("enabled",false) || !prefs.getBoolean("title_optin",false) || pkg!=YouTubePlayback.PACKAGE || pkg !in PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty()))) return
        val now=System.currentTimeMillis()
        if(now-lastScan in 0..249) return
        val root=rootInActiveWindow ?: return
        if(root.packageName?.toString()!=pkg) return
        lastScan=now
        val ids=linkedSetOf<String>()
        val labels=linkedSetOf<String>()
        var count=0
        fun walk(node:AccessibilityNodeInfo,depth:Int) {
            if(depth>20 || ++count>350 || node.packageName?.toString()!=pkg || node.isPassword || node.isEditable) return
            if(node.isVisibleToUser) {
                val id=PlayerRules.id(node.viewIdResourceName.orEmpty())
                if(id.matches(Regex("[a-z0-9_]{1,80}"))) ids.add(id)
                if(hotstar && id.matches(Regex(".*(player|playback|video|episode|season|content)_(title|subtitle|name|description).*"))) {
                    node.text?.toString()?.take(160)?.let { labels.add(it) }
                    node.contentDescription?.toString()?.take(160)?.let { labels.add(it) }
                }
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        if(hotstar) { HotstarProbe.screen(this,ids,labels,event.eventType); return }
        val mode=YouTubePlayback.mode(ids,PlaybackSegment.UNKNOWN)
        val segment=if(mode==YouTubePlayback.Mode.ADVERTISEMENT) PlaybackSegment.ADVERTISEMENT else PlaybackSegment.UNKNOWN
        prefs.edit().putString("segment:"+pkg,segment.name).putLong("segment_time:"+pkg,now).apply()
        if(YouTubePlayback.observe(mode,now)) PlaybackListener.wake()
    }
    override fun onInterrupt() { YouTubePlayback.clear(); DebugTrace.event("title","interrupted") }
    override fun onDestroy() { YouTubePlayback.clear(); prefs.edit().putBoolean("title_connected",false).apply(); super.onDestroy() }
    companion object {
        fun segment(context:Context,pkg:String,now:Long):PlaybackSegment {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(pkg!=YouTubePlayback.PACKAGE || !p.getBoolean("enabled",false) || !p.getBoolean("title_optin",false) || pkg !in PlaybackDetectors.selected(p.getStringSet("allowed",emptySet()).orEmpty()) || now-p.getLong("segment_time:"+pkg,0) !in 0..6000) return PlaybackSegment.UNKNOWN
            return runCatching { PlaybackSegment.valueOf(p.getString("segment:"+pkg,"UNKNOWN").orEmpty()) }.getOrDefault(PlaybackSegment.UNKNOWN)
        }
    }
}
