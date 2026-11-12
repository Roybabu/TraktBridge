package com.traktbridge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Context
import android.os.Build

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
        val labels=mutableListOf<ScreenLabel>()
        val ids=linkedSetOf<String>()
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
                val value=(node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                if(value.isNotBlank()) {
                    textNodes++
                    val heading=Build.VERSION.SDK_INT>=28 && node.isHeading
                    val button=cls=="Button" || node.isClickable
                    if(value.length<=160 && (heading || button || id.isNotBlank() || PlayerRules.titleId(pkg,id,player))) labels.add(ScreenLabel(id,value,heading,button))
                }
            }
            for(i in 0 until node.childCount) node.getChild(i)?.let { walk(it,depth+1) }
        }
        walk(root,0)
        val hint=ContentRecognition.detect(pkg,labels,player)
        DebugTrace.event("title","package="+pkg+" player="+player+" textNodes="+textNodes+" candidate="+(hint?.kind ?: "none"))
        if(prefs.getBoolean("player_inspector",false)) {
            // Structural metadata only: no text, content descriptions, credentials or URLs in reports.
            DebugTrace.event("inspector","package="+pkg+" nodes="+count+" textNodes="+textNodes+" ids="+ids.take(60).joinToString(","))
            DebugTrace.event("inspector","package="+pkg+" classes="+classes.take(25).joinToString(",")+" headings="+labels.count { it.heading }+" buttons="+labels.count { it.button })
        }
        if(hint==null) return
        prefs.edit().putString("title_hint:"+pkg,hint.title).putString("title_detail:"+pkg,hint.detail).putLong("title_time:"+pkg,now).apply()
    }
    override fun onInterrupt() { DebugTrace.event("title","interrupted") }
    override fun onDestroy() { prefs.edit().putBoolean("title_connected",false).apply(); super.onDestroy() }
    companion object {
        fun hint(context:Context,pkg:String,start:Long,end:Long):Pair<String,String>? {
            val p=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
            if(!p.getBoolean("title_optin",false) || pkg !in (p.getStringSet("allowed",emptySet()) ?: emptySet())) return null
            val t=p.getLong("title_time:"+pkg,0)
            if(t !in start..end) return null
            val title=p.getString("title_hint:"+pkg,"").orEmpty()
            return if(title.isBlank()) null else title to p.getString("title_detail:"+pkg,"").orEmpty()
        }
    }
}
