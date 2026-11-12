package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ConcurrentPlaybackTest {
 @Test fun distinctSessionsRequireDistinctIdentityAndTiming() {
  val a=WatchSample("a","app","Movie A","",600000,10000,true)
  val b=a.copy(key="b",title="Movie B")
  assertEquals(2,ConcurrentPlayback.select(listOf(a,b)).size)
  assertTrue(ConcurrentPlayback.select(listOf(a,a.copy(key="b"))).isEmpty())
  assertEquals(1,ConcurrentPlayback.select(listOf(a,b.copy(position=-1,positionSource="unknown"))).size)
 }
}
