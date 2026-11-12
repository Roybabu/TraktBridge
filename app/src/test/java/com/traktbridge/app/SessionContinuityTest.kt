package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class SessionContinuityTest {
    private val row=WatchRecord("a","netflix","Example","S02 E03",1000,31000,30000,600000,30000,"pending",positionSource="media")
    private val sample=WatchSample("key","netflix","Example","S02 E03",600000,35000,true)
    @Test fun resumesOnlyCompatibleInterruptedEpisode() {
        assertTrue(SessionContinuity.canResume(row,sample,36000))
        assertFalse(SessionContinuity.canResume(row,sample.copy(subtitle="S02 E04"),36000))
        assertFalse(SessionContinuity.canResume(row,sample.copy(position=0),36000))
        assertFalse(SessionContinuity.canResume(row,sample.copy(source="prime"),36000))
        assertFalse(SessionContinuity.canResume(row,sample,200000))
        assertFalse(SessionContinuity.canResume(row.copy(status="synced"),sample,36000))
        assertFalse(SessionContinuity.canResume(row.copy(subtitle=""),sample.copy(subtitle=""),36000))
        assertFalse(SessionContinuity.canResume(row,sample,30000))
    }
    @Test fun rewatchRequiresNonoverlappingConfirmedIdentityAndRestart() {
        val previous=row.copy(traktId=42,matchType="episode",status="synced",position=590000)
        val next=row.copy(id="b",traktId=42,matchType="episode",started=40000,ended=60000)
        assertTrue(SessionContinuity.possibleRewatch(previous,next))
        assertFalse(SessionContinuity.possibleRewatch(previous,next.copy(started=20000)))
        assertFalse(SessionContinuity.possibleRewatch(previous,next.copy(traktId=43)))
        assertFalse(SessionContinuity.possibleRewatch(previous,next.copy(position=400000)))
    }
    @Test fun duplicatedInputDoesNotInflateTotals() {
        assertEquals(30000L,ViewingGroups.build(listOf(row,row)).single().sampledMs)
    }
}
