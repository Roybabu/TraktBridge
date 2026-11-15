package com.traktbridge.app

import android.content.Context
import android.net.Uri

/** Public catalogue IDs only. Never requests a playback URL or sends its tokens. */
object HotstarPublicCatalogue {
    data class Entry(val id:String,val kind:String,val title:String)
    private var index:Map<String,Entry>?=null
    @Synchronized private fun load(c:Context):Map<String,Entry> {
        index?.let { return it }
        val loaded=c.assets.open("hotstar_public_ids.tsv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val p=line.split('\t',limit=3)
                if(p.size==3) Entry(p[0],p[1],p[2]) else null
            }.associateBy { it.id }
        }
        index=loaded
        return loaded
    }
    fun candidateIds(segments:List<String>):Set<String> =
        segments.flatMap { segment -> Regex("(?<![A-Za-z0-9])[0-9]{6,12}(?![A-Za-z0-9])").findAll(segment).map { it.value }.toList() }.toSet()
    fun lookup(c:Context,uri:String,episode:Boolean):List<Entry> {
        val u=Uri.parse(uri)
        if(u.scheme!="https" || u.host.isNullOrBlank()) return emptyList()
        val ids=candidateIds(u.pathSegments)
        if(ids.isEmpty()) return emptyList()
        val catalogue=load(c)
        return ids.mapNotNull { catalogue[it] }.filter { if(episode) it.kind=="tv" else it.kind=="movie" }
    }
}
