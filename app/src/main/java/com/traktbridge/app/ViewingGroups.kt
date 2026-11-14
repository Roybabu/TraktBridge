package com.traktbridge.app

import java.util.Locale

// Keep original observations and upload audit records. Consolidation is a read model,
// not destructive merging. Only confirmed IDs can establish cross-provider identity.
data class ViewingTitle(val key:String,val sessions:List<WatchRecord>) {
    val latest=sessions.maxBy { it.ended }
    val representative=sessions.filter { it.traktId>0 }.maxByOrNull { it.ended } ?: latest
    val platforms=sessions.map { it.source }.distinct()
    val sampledMs=sessions.sumOf { it.watched.coerceAtLeast(0) }
    val pausedMs=sessions.sumOf { it.pausedMs }
    val bufferingMs=sessions.sumOf { it.bufferingMs }
    val uncertainMs=sessions.sumOf { it.uncertainMs }
    val seeks=sessions.sumOf { it.seeks }
    val rewinds=sessions.sumOf { it.rewinds }
    val possibleRewatchIds=sessions.sortedBy { it.started }.zipWithNext().filter { (a,b) -> SessionContinuity.possibleRewatch(a,b) }.map { it.second.id }.toSet()
    val confirmed=sessions.any { it.status=="synced" }
    val completedWatches=sessions.filter { it.status=="synced" }.sortedBy { it.ended }
    val lastCompleted=completedWatches.lastOrNull()?.ended ?: 0L
    val rewatchSessions=sessions.filter { !UnifiedTraktHistory.remote(it) && it.status in setOf("active","pending","reviewed") && it.started>lastCompleted && lastCompleted>0 && it.watched>0 }
    val rewatching=rewatchSessions.isNotEmpty()
    val active=sessions.any { it.status=="active" }
    val queued=sessions.any { it.status in setOf("queued","uncertain","blocked") }
    val pending=sessions.any { it.status in setOf("pending","reviewed") }
    // Do not add playback positions together: resuming/rewinding/replaying is not progress.
    val positionSession=(if(rewatching) rewatchSessions else sessions).filter { PlaybackPosition.valid(it.position,it.duration) }.maxByOrNull { it.ended }
    val catalogueRuntimeMs=sessions.maxOf { it.catalogueRuntime }
    val durationMs=positionSession?.duration ?: catalogueRuntimeMs
    val positionMs=positionSession?.position?.coerceIn(0,durationMs) ?: -1L
    val positionSource=positionSession?.positionSource ?: "unknown"
    val nearEnd=PlaybackAccuracy.nearEnd(positionMs,durationMs)
    val remainingMs=if(durationMs>0 && positionMs>=0) (durationMs-positionMs).coerceAtLeast(0) else -1L
}
object ViewingGroups {
    fun normalized(value:String)=value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    fun key(r:WatchRecord):String {
        if(r.traktId>0 && r.matchType in setOf("movie","episode")) return r.matchType+":"+r.traktId
        // Never join unknown titles or potentially different episodes on a bare show name.
        // Unmatched records stay provisional until exact identification.
        return "session:"+r.id
    }
    fun build(records:List<WatchRecord>):List<ViewingTitle> =
        records.distinctBy { it.id }.groupBy(::key).map { (key,rows)->ViewingTitle(key,rows.sortedByDescending { it.ended }) }.sortedByDescending { it.latest.ended }
}
