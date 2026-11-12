package com.traktbridge.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

data class AppActivity(val source: String,val start: Long,val end: Long)
object UsageFallback {
    fun allowed(context: Context): Boolean {
        val ops=context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),context.packageName)==AppOpsManager.MODE_ALLOWED
    }
    fun recent(context: Context,selected: Set<String>,since: Long): List<AppActivity> {
        if(!allowed(context) || selected.isEmpty()) return emptyList()
        val now=System.currentTimeMillis()
        val start=maxOf(since,now-24*60*60*1000L)
        if(start>=now) return emptyList()
        val events=context.getSystemService(UsageStatsManager::class.java).queryEvents(start,now) ?: return emptyList()
        val opened=mutableMapOf<String,Long>()
        val result=mutableListOf<AppActivity>()
        val event=UsageEvents.Event()
        while(events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg=event.packageName ?: continue
            if(pkg !in selected) continue
            when(event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> opened.putIfAbsent(pkg,event.timeStamp)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from=opened.remove(pkg) ?: continue
                    if(event.timeStamp-from>=30000) result.add(AppActivity(pkg,from,event.timeStamp))
                }
            }
        }
        return result.sortedByDescending { it.end }.take(50)
    }
}
