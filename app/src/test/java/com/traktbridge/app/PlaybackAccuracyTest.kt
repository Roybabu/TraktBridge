package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlaybackAccuracyTest {
 private fun sample(pos:Long,phase:PlaybackPhase=PlaybackPhase.PLAYING,title:String="Movie",detail:String="")=WatchSample("key","app",title,detail,600000,pos,phase==PlaybackPhase.PLAYING,phase)
 @Test fun normalPlayback() { assertEquals(5000L,PlaybackAccuracy.interval(sample(10000),sample(15000),5000).watched) }
 @Test fun seekNotWatchTime() { val x=PlaybackAccuracy.interval(sample(10000),sample(300000),5000); assertEquals(1,x.seeks); assertEquals(0L,x.watched) }
 @Test fun rewindNotWatchTime() { assertEquals(1,PlaybackAccuracy.interval(sample(100000),sample(10000),5000).replay) }
 @Test fun bufferingAndPause() { assertEquals(5000L,PlaybackAccuracy.interval(sample(10000),sample(10000,PlaybackPhase.BUFFERING),5000).buffering); assertEquals(5000L,PlaybackAccuracy.interval(sample(10000,PlaybackPhase.PAUSED),sample(10000,PlaybackPhase.PAUSED),5000).paused) }
 @Test fun stagnantAndGaps() { assertEquals(0L,PlaybackAccuracy.interval(sample(10000),sample(10000),5000).watched); assertEquals(0L,PlaybackAccuracy.interval(sample(10000),sample(30000),20000).watched) }
 @Test fun unknownTitleCanBeEnriched() { assertTrue(PlaybackAccuracy.compatibleIdentity(sample(0,title=""),sample(5000))); assertFalse(PlaybackAccuracy.compatibleIdentity(sample(0,detail="S01 E01"),sample(5000,detail="S01 E02"))) }
 @Test fun completionSuggestion() { assertTrue(PlaybackAccuracy.nearEnd(550000,600000)); assertFalse(PlaybackAccuracy.nearEnd(-1,600000)) }
}
