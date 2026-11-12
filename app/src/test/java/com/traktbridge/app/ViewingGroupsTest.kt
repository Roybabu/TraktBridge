package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class ViewingGroupsTest {
    private fun row(id:String,source:String,position:Long,watched:Long,ended:Long,trakt:Long=42,type:String="movie",status:String="reviewed") =
        WatchRecord(id,source,"Title","",1,ended,watched,600000,position,status,type,trakt)
    @Test fun resumesAcrossAppsUseLatestPositionNotSum() {
        val group=ViewingGroups.build(listOf(row("a","netflix",120000,120000,10),row("b","prime",300000,180000,20))).single()
        assertEquals(2,group.platforms.size)
        assertEquals(300000L,group.sampledMs)
        assertEquals(300000L,group.positionMs)
        assertEquals(300000L,group.remainingMs)
        assertFalse(group.confirmed)
    }
    @Test fun episodesRemainDistinct() {
        assertEquals(2,ViewingGroups.build(listOf(row("a","netflix",1,1,1,42,"episode"),row("b","netflix",1,1,2,43,"episode"))).size)
    }
    @Test fun unknownTitlesAreNotFalselyMerged() {
        assertEquals(2,ViewingGroups.build(listOf(row("a","netflix",1,1,1,0),row("b","netflix",1,1,2,0))).size)
    }
    @Test fun syncedCompletionSurvivesLaterReplay() {
        val group=ViewingGroups.build(listOf(row("a","netflix",600000,600000,1,status="synced"),row("b","prime",60000,60000,2))).single()
        assertTrue(group.confirmed)
        assertEquals(540000L,group.remainingMs)
    }
}
