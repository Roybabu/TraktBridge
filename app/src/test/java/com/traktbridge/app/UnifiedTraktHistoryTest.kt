package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class UnifiedTraktHistoryTest {
    private val first=RemoteItem("episode",42,"Wayne — Episode • S1 E6",10,1,6,"2020-01-01T10:00:00Z",101)
    private val second=first.copy(watchedAt="2021-01-01T10:00:00Z",eventId=102)
    private val time=java.time.Instant.parse(first.watchedAt).toEpochMilli()
    private val local=WatchRecord("local","com.google.android.youtube","Wayne","",time-1000,time+500,1000,2000,1000,"synced","episode",42,first.title,10,1,6)
    @Test fun preservesHistoricalDatesAndRewatches() {
        val rows=UnifiedTraktHistory.merge(emptyList(),listOf(first,first,second),emptySet())
        assertEquals(2,rows.size)
        assertTrue(rows.all { UnifiedTraktHistory.remote(it) && it.watched==0L && it.position==-1L && it.status=="synced" })
        assertEquals(time,rows.last().ended)
        val group=ViewingGroups.build(rows).single()
        assertTrue(group.confirmed)
        assertFalse(group.pending)
        assertEquals(0L,group.sampledMs)
    }
    @Test fun reconcilesOnlySameAccountAndExactTime() {
        assertEquals(2,UnifiedTraktHistory.merge(listOf(local),listOf(first,second),setOf(local.id)).size)
        assertEquals(3,UnifiedTraktHistory.merge(listOf(local),listOf(first,second),emptySet()).size)
        assertEquals(3,UnifiedTraktHistory.merge(listOf(local.copy(status="reviewed")),listOf(first,second),setOf(local.id)).size)
    }
    @Test fun rejectsMalformedHistory() {
        assertTrue(UnifiedTraktHistory.merge(emptyList(),listOf(first.copy(watchedAt="invalid"),second.copy(eventId=0)),emptySet()).isEmpty())
    }
}
