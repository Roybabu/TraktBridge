package com.traktbridge.app

import java.time.Instant
import java.time.ZoneId
import java.security.MessageDigest

object ExternalHistoryImport {
    // These are a documented interchange format, not a promise that each service
    // currently exports this exact schema. Imported records are always review-only.
    val sources=listOf("letterboxd","trakt","simkl","imdb","myanimelist","anilist")
    fun parseCsv(text:String,source:String,zone:ZoneId=ZoneId.systemDefault()):ImportPreview {
        require(source in sources)
        if(source=="letterboxd") return HistoryImport.parse(text,source,zone)
        val rows=HistoryImport.csv(text.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "Empty CSV" }
        val headers=rows.first().map { it.trim().lowercase(java.util.Locale.ROOT) }
        val ti=listOf("title","name","anime_title").firstNotNullOfOrNull { name -> headers.indexOf(name).takeIf { it>=0 } } ?: error("CSV must include Title or Name")
        val di=listOf("watched_at","watched at","watched date","date","date watched","finish date").firstNotNullOfOrNull { name -> headers.indexOf(name).takeIf { it>=0 } } ?: error("CSV must include a viewing date; ratings and watchlists are not viewing history")
        val si=headers.indexOf("season")
        val ei=headers.indexOf("episode")
        fun escaped(v:String)="\""+v.replace("\"","\"\"")+"\""
        val converted=buildString {
            append("Title,Date,Start Time\n")
            for(row in rows.drop(1)) {
                val title=row.getOrNull(ti).orEmpty()
                val date=row.getOrNull(di).orEmpty()
                val season=row.getOrNull(si)?.toIntOrNull()
                val episode=row.getOrNull(ei)?.toIntOrNull()
                val suffix=if(season!=null && season>=0 && episode!=null && episode>0) " S"+season+"E"+episode else if(episode!=null && episode>0) " Episode "+episode else ""
                append(escaped(if(title.isBlank()) "" else title+suffix)); append(','); append(escaped(date)); append(','); append(escaped(date)); append('\n')
            }
        }
        return HistoryImport.parse(converted,source,zone)
    }
    fun fromTrakt(rows:List<RemoteItem>,account:String,zone:ZoneId=ZoneId.systemDefault()):List<ImportedViewing> {
        require(account.isNotBlank())
        return rows.take(HistoryImport.MAX_ROWS).mapNotNull { row ->
            if(row.type !in setOf("movie","episode") || row.id<=0 || row.eventId<=0 || row.title.isBlank()) return@mapNotNull null
            val ts=runCatching { Instant.parse(row.watchedAt).toEpochMilli() }.getOrNull() ?: return@mapNotNull null
            val day=Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
            if(day.year !in 1900..2100) return@mapNotNull null
            val id=MessageDigest.getInstance("SHA-256").digest(("trakt:"+account+":"+row.eventId).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            val title=row.title.take(250)+if(row.type=="episode" && row.season>=0 && row.episode>0) " S"+row.season+"E"+row.episode else ""
            ImportedViewing("trakt",title,"Imported remote watch event; local playback unverified",day,ts,"import:"+id)
        }.distinctBy { it.id }
    }
}
