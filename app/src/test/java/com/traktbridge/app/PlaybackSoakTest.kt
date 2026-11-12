package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlaybackSoakTest {
 @Test fun simulatedTwentyFourHoursDoesNotInflateWatchTime() {
  val duration=86400000L
  val sample=WatchSample("key","app","Movie","",duration,0,true)
  var watched=0L
  var uncertain=0L
  for(i in 1..17280) {
   val old=sample.copy(position=(i-1)*5000L)
   val next=sample.copy(position=i*5000L)
   val interval=PlaybackAccuracy.interval(old,next,5000L)
   watched+=interval.watched
   uncertain+=interval.uncertain
  }
  assertEquals(duration,watched)
  assertEquals(0L,uncertain)
 }
 @Test fun missingSamplesAndPausesDoNotCountAsViewing() {
  val a=WatchSample("key","app","Movie","",600000L,10000L,true)
  repeat(1000) {
   assertEquals(0L,PlaybackAccuracy.interval(a,a.copy(position=20000L),30000L).watched)
   assertEquals(0L,PlaybackAccuracy.interval(a,a.copy(playing=false,phase=PlaybackPhase.PAUSED),5000L).watched)
  }
 }
 @Test fun navigationAndIdentityIntegration() {
  assertEquals("review",PlaybackShortcuts.shortcut("traktbridge://shortcut/review"))
  assertEquals("record-1",ComponentPolicy.reviewId("traktbridge://review/record-1"))
  assertNull(StreamingAvailability.trakt("episode",42))
 }
}
