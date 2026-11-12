package com.traktbridge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context

// Only inspect selected video packages, and only explicit player-title identifiers.
// Do not collect arbitrary screen text, credentials, screenshots or input events.
class TitleAccessibility: AccessibilityService() {
    private val prefs by lazy { getSharedPreferences("tracker",MODE_PRIVATE) }
    private var lastScan=0L
    override fun onServiceConnected() {
        DebugTrace.init(this)
        prefs.edit().putBoolean("title_connected",true).apply()
        DebugTrace.event("title","accessibility connected")
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if(!prefs.getBoolean("enabled",false) || !prefs.getBoolean("title_optin",false)) return
        val pkg=event?.packageName?.toString() ?: return
        if(pkg !in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())) return
        val now=System.currentTimeMillis()
        if(now-lastScan<1000) return
        lastScan=now
        val root=rootInActiveWindow ?: return
        if(root.packageName?.toString()!=pkg) return
        val candidates=mutableListOf<Pair<String,String>>()
        val inspected=linkedSetOf<String>()
        var player=false
        var count=0
        fun walk(node: AccessibilityNodeInfo,depth: Int,inPlayer:Boolean=false) {
            if(depth>20 || ++count>350 || node.packageName?.toString()!=pkg || node.isPassword) return
            val id=PlayerRules.id(node.viewIdResourceName.orEmpty())
            val within=inPlayer || PlayerRules.isPlayer(id)
            if(PlayerRules.isPlayer(id)) player=true
            if(PlayerRules.inspectId(id) && id.matches(Regex("[a-z0-9_]{1,80}"))) inspected.add(id)
            if(PlayerRules.titleId(pkg,id,within)) {
                val value=(node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                if(PlayerRules.validTitle(value) && node.isVisibleToUser) candidates.add(id to value)
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1,within) }
        }
        walk(root,0)
        DebugTrace.event("title","selected package="+pkg+" playerHint="+player+" candidateCount="+candidates.size)
        if(prefs.getBoolean("player_inspector",false)) DebugTrace.event("inspector","package="+pkg+" nodes="+count+" player="+player+" ids="+inspected.take(40).joinToString(","))
        if(!player || candidates.isEmpty()) return
        val title=candidates.firstOrNull { it.first.contains("show_title") }?.second ?: candidates.firstOrNull { !PlayerRules.detailId(it.first) }?.second ?: candidates.firstOrNull { it.first=="episode_title" || it.first=="player_episode_title" }?.second ?: return
        val detail=candidates.firstOrNull { it.second!=title && PlayerRules.detailId(it.first) }?.second.orEmpty()
        prefs.edit().putString("title_hint:"+pkg,title).putString("title_detail:"+pkg,detail).putLong("title_time:"+pkg,now).apply()
    }
    override fun onInterrupt() { DebugTrace.event("title","interrupted") }
    override fun onDestroy() { prefs.edit().putBoolean("title_connected",false).apply(); super.onDestroy() }
    companion object {
        fun hint(context: Context,pkg: String,start: Long,end: Long): Pair<String,String>? {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(!p.getBoolean("title_optin",false)) return null
            val t=p.getLong("title_time:"+pkg,0)
            if(t !in start..end) return null
            val title=p.getString("title_hint:"+pkg,"").orEmpty()
            return if(title.isBlank()) null else title to p.getString("title_detail:"+pkg,"").orEmpty()
        }
    }
}
