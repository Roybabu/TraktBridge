package com.traktbridge.app

import java.time.Instant

/** Remote history is a read model, never inserted into the local playback database. */
object UnifiedTraktHistory {
    const val SOURCE="trakt.account"
    fun remote(record:WatchRecord)=record.source==SOURCE && record.id.startsWith("trakt-event:")
    private data class EventKey(val type:String,val id:Long,val second:Long)
    private fun key(r:WatchRecord)=EventKey(r.matchType,r.traktId,r.ended/1000)
    fun merge(local:List<WatchRecord>,remote:List<RemoteItem>,approvedLocalIds:Set<String>):List<WatchRecord> {
        // A locally approved upload can represent one remote event, not every
        // repeat viewing of the same title. Match exact UTC seconds only.
        val localCounts=local.filter { it.status=="synced" && it.id in approvedLocalIds && it.traktId>0 }
            .groupingBy(::key).eachCount().toMutableMap()
        val projected=remote.distinctBy { it.eventId }.mapNotNull { item ->
            if(item.eventId<=0 || item.id<=0 || item.type !in setOf("movie","episode")) return@mapNotNull null
            val end=runCatching { Instant.parse(item.watchedAt).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val k=EventKey(item.type,item.id,end/1000)
            val remaining=localCounts[k] ?: 0
            if(remaining>0) { localCounts[k]=remaining-1; return@mapNotNull null }
            WatchRecord(id="trakt-event:"+item.eventId,source=SOURCE,title=item.title,subtitle="Trakt account history",
                started=end,ended=end,watched=0,duration=0,position=-1,status="synced",matchType=item.type,
                traktId=item.id,matchLabel=item.title,showId=item.showId,season=item.season,episode=item.episode)
        }
        return (local+projected).distinctBy { it.id }.sortedByDescending { it.ended }
    }
}
