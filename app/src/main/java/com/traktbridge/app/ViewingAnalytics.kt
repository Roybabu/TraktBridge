package com.traktbridge.app

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class HistoryFilter(val query:String="",val source:String="",val period:Int=0,val type:String="",val status:String="",val sort:String="recent")
data class ActivityDay(val date:LocalDate,val sampledMs:Long,val sessions:Int)
data class AnnualRecap(val sampledMs:Long,val activeDays:Int,val longestStreak:Int,val busiestMonth:Int?,val topSource:String?,val confirmedMovies:Int,val confirmedEpisodes:Int)
object ViewingAnalytics {
    fun recap(records:List<WatchRecord>,year:Int,zone:ZoneId=ZoneId.systemDefault()):AnnualRecap {
        val rows=records.filter { it.status!="active" && day(it.ended,zone).year==year }
        val sampled=rows.filter { it.watched>0 }
        val days=sampled.map { day(it.ended,zone) }.distinct().sorted()
        var streak=0; var longest=0; var previous:LocalDate?=null
        for(d in days) { streak=if(previous?.plusDays(1)==d) streak+1 else 1; longest=maxOf(longest,streak); previous=d }
        val months=sampled.groupBy { day(it.ended,zone).monthValue }
        val sources=sampled.groupBy { it.source }
        val groups=ViewingGroups.build(rows)
        return AnnualRecap(sampled.sumOf { it.watched },days.size,longest,months.entries.sortedBy { it.key }.maxByOrNull { it.value.sumOf { r -> r.watched } }?.key,sources.entries.sortedBy { it.key }.maxByOrNull { it.value.sumOf { r -> r.watched } }?.key,groups.count { it.confirmed && it.representative.matchType=="movie" },groups.count { it.confirmed && it.representative.matchType=="episode" })
    }
    fun day(ms:Long,zone:ZoneId=ZoneId.systemDefault()):LocalDate=Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun filter(records:List<WatchRecord>,f:HistoryFilter,today:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()):List<WatchRecord> {
        val q=f.query.trim().lowercase(Locale.ROOT)
        val start=if(f.period>0) today.minusDays(f.period.toLong()-1) else null
        return records.filter { r ->
            (q.isBlank() || listOf(r.title,r.subtitle,r.matchLabel).any { it.lowercase(Locale.ROOT).contains(q) }) &&
            (f.source.isBlank() || r.source==f.source) &&
            (start==null || day(r.ended,zone) in start..today) &&
            (f.type.isBlank() || (if(f.type=="unknown") r.traktId<=0 else r.matchType==f.type)) &&
            (f.status.isBlank() || when(f.status) { "completed"->r.status=="synced"; "unfinished"->r.status in setOf("pending","reviewed","active"); "queued"->r.status in setOf("queued","uncertain","blocked"); else->false })
        }
    }
    fun sort(groups:List<ViewingTitle>,sort:String):List<ViewingTitle> = when(sort) {
        "title"->groups.sortedBy { it.representative.matchLabel.ifBlank { it.representative.title }.lowercase(Locale.ROOT) }
        "watchtime"->groups.sortedByDescending { it.sampledMs }
        else->groups.sortedByDescending { it.latest.ended }
    }
    // Existing rows store aggregate sampled duration, not timestamped intervals.
    // Attribute the aggregate to the session end date; never fabricate per-day splits.
    fun calendar(records:List<WatchRecord>,month:YearMonth,zone:ZoneId=ZoneId.systemDefault()):List<ActivityDay> {
        val byDay=records.groupBy { day(it.ended,zone) }
        return (1..month.lengthOfMonth()).map { n ->
            val d=month.atDay(n); val rows=byDay[d].orEmpty()
            ActivityDay(d,rows.sumOf { it.watched.coerceAtLeast(0) },rows.size)
        }
    }
}
