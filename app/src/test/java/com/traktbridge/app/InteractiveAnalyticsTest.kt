package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDate
class InteractiveAnalyticsTest {
    private val zone=ZoneId.of("UTC")
    private fun row(id:String,month:Int,watched:Long,source:String="netflix",status:String="reviewed"):WatchRecord {
        val end=LocalDate.of(2026,month,1).atStartOfDay(zone).toInstant().toEpochMilli()
        return WatchRecord(id,source,"Example","",end-1000,end,watched,60000,1000,status)
    }
    @Test fun monthsIncludeEmptyAndExcludeActiveOrDuplicateRows() {
        val a=row("a",1,1000)
        val rows=ViewingAnalytics.monthly(listOf(a,a,row("b",2,2000,status="active"),row("c",12,3000)),2026,zone)
        assertEquals(12,rows.size); assertEquals(1000L,rows.first().sampledMs); assertEquals(0L,rows[1].sampledMs); assertEquals(3000L,rows.last().sampledMs)
    }
    @Test fun platformTotalsAreDeduplicated() {
        val a=row("a",1,1000)
        val rows=ViewingAnalytics.platforms(listOf(a,a,row("b",2,2000,"prime")))
        assertEquals(listOf("prime","netflix"),rows.map { it.source }); assertEquals(1000L,rows.last().sampledMs)
    }
}
