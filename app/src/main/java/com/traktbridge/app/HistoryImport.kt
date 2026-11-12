package com.traktbridge.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.security.MessageDigest
import java.util.Locale

data class ImportedViewing(val source:String,val title:String,val detail:String,val day:LocalDate,val timestamp:Long?,val id:String)
data class ImportPreview(val rows:List<ImportedViewing>,val skipped:Int)
object HistoryImport {
    const val MAX_CHARS=4_000_000
    const val MAX_ROWS=20000
    // RFC 4180-style quoted fields, including escaped quotes and embedded newlines.
    fun csv(input:String):List<List<String>> {
        require(input.length<=MAX_CHARS) { "File too large (4 million characters maximum)" }
        val rows=mutableListOf<List<String>>()
        val row=mutableListOf<String>()
        val field=StringBuilder()
        var quoted=false
        var i=0
        fun endField() { row.add(field.toString()); field.setLength(0); require(row.size<=100) { "Too many columns" } }
        fun endRow() { endField(); if(row.any { it.isNotBlank() }) rows.add(row.toList()); row.clear(); require(rows.size<=MAX_ROWS+1) { "Too many rows" } }
        while(i<input.length) {
            val c=input[i++]
            when {
                c=='"' && quoted && i<input.length && input[i]=='"' -> { field.append('"'); i++ }
                c=='"' && quoted -> quoted=false
                c=='"' && field.isEmpty() -> quoted=true
                c==',' && !quoted -> endField()
                (c=='\n' || c=='\r') && !quoted -> { if(c=='\r' && i<input.length && input[i]=='\n') i++; endRow() }
                else -> field.append(c)
            }
        }
        require(!quoted) { "Unclosed CSV quote" }
        if(field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
    private fun normalized(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    private fun day(s:String):LocalDate? {
        val formats=listOf(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("M/d/uuuu",Locale.US),DateTimeFormatter.ofPattern("M/d/uu",Locale.US),DateTimeFormatter.ofPattern("M/d/uuuu",Locale.US))
        return formats.firstNotNullOfOrNull { runCatching { LocalDate.parse(s.trim(),it) }.getOrNull() }
    }
    private fun timestamp(s:String,zone:ZoneId):Long? = runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(s.trim().replace(' ','T')).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    fun parse(input:String,source:String,zone:ZoneId=ZoneId.systemDefault()):ImportPreview {
        val rows=csv(input.removePrefix("\uFEFF"))
        require(rows.isNotEmpty()) { "Empty CSV" }
        val header=rows.first().map(::normalized)
        val titleIndex=if(source=="letterboxd") header.indexOf("name") else header.indexOf("title")
        val dateIndex=if(source=="letterboxd") header.indexOf("watched date") else header.indexOf("date")
        val timeIndex=header.indexOf("start time")
        require(titleIndex>=0 && (dateIndex>=0 || timeIndex>=0)) { if(source=="letterboxd") "Letterboxd diary must contain Name and Watched Date" else "CSV must contain Title and Date, or Title and Start Time" }
        var skipped=0
        val occurrences=mutableMapOf<String,Int>()
        val result=mutableListOf<ImportedViewing>()
        for(row in rows.drop(1)) {
            val title=row.getOrNull(titleIndex).orEmpty().trim().take(300)
            val rawTime=row.getOrNull(timeIndex).orEmpty()
            val ts=timestamp(rawTime,zone)
            val date=ts?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: day(row.getOrNull(dateIndex).orEmpty())
            if(title.isBlank() || date==null || date.year !in 1900..2100) { skipped++; continue }
            val key=source+"\u001f"+normalized(title)+"\u001f"+(ts?.toString() ?: date.toString())
            val n=(occurrences[key] ?: 0)+1; occurrences[key]=n
            val id="import:"+MessageDigest.getInstance("SHA-256").digest((key+"#"+n).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            result.add(ImportedViewing(source,title,"Imported provider activity; completion and playback time unverified",date,ts,id))
        }
        return ImportPreview(result,skipped)
    }
    // Match only exact normalized titles, same provider and calendar day. Ambiguity stays for review.
    fun candidates(row:ImportedViewing,records:List<WatchRecord>,zone:ZoneId=ZoneId.systemDefault()):List<WatchRecord> =
        records.filter { !it.id.startsWith("import:") && !it.id.startsWith("shared:") && it.source==row.source &&
            it.status!="active" && normalized(it.title)==normalized(row.title) &&
            Instant.ofEpochMilli(it.started).atZone(zone).toLocalDate()==row.day }
}
