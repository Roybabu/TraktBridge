package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
class ViewingAnalyticsTest {
 private val zone=ZoneId.of("UTC")
 private val today=LocalDate.of(2026,11,12)
 private fun row(id:String,day:Int,source:String="netflix",status:String="reviewed",time:Long=60000L):WatchRecord {
  val ms=today.withDayOfMonth(day).atStartOfDay(zone).toInstant().toEpochMilli()
  return WatchRecord(id,source,"Movie "+id,"",ms,ms,time,600000,10000,status,"movie",day.toLong(),"Movie "+id)
 }
 @Test fun filterByDateAndSource() { assertEquals(listOf("a"),ViewingAnalytics.filter(listOf(row("a",12),row("b",1),row("c",12,"prime")),HistoryFilter(source="netflix",period=7),today,zone).map { it.id }) }
 @Test fun statusAndSearch() { assertEquals("a",ViewingAnalytics.filter(listOf(row("a",12,status="synced"),row("b",12)),HistoryFilter(query="movie",status="completed"),today,zone).single().id) }
 @Test fun calendarUsesEndDate() { val days=ViewingAnalytics.calendar(listOf(row("a",12),row("b",12,time=0)),YearMonth.of(2026,11),zone); assertEquals(30,days.size); assertEquals(60000L,days[11].sampledMs); assertEquals(2,days[11].sessions) }
 @Test fun annualRecapCountsOnlyMeasuredDaysAndStreaks() {
  val recap=ViewingAnalytics.recap(listOf(row("a",1),row("b",2),row("c",4,time=0),row("d",5,status="active")),2026,zone)
  assertEquals(120000L,recap.sampledMs); assertEquals(2,recap.activeDays); assertEquals(2,recap.longestStreak); assertEquals(11,recap.busiestMonth); assertEquals("netflix",recap.topSource)
 }
}
