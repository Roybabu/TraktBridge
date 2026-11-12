package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SegmentDetectionTest {
 @Test fun onlyExplicitPlayerCues() {
  val cue=ScreenLabel("skip_intro","Skip intro",button=true)
  assertEquals(PlaybackSegment.INTRO,SegmentDetection.screen(listOf(cue),true))
  assertEquals(PlaybackSegment.UNKNOWN,SegmentDetection.screen(listOf(cue),false))
  assertEquals(PlaybackSegment.UNKNOWN,SegmentDetection.screen(listOf(ScreenLabel("title","The Advertisement")),true))
  assertEquals(PlaybackSegment.ADVERTISEMENT,SegmentDetection.metadata("Advertisement"))
 }
 @Test fun segmentsDoNotInflateViewing() {
  val s=WatchSample("key","app","Movie","",600000,10000,true)
  for(segment in listOf(PlaybackSegment.INTRO,PlaybackSegment.RECAP,PlaybackSegment.CREDITS,PlaybackSegment.ADVERTISEMENT)) {
   val interval=PlaybackAccuracy.interval(s,s.copy(position=15000,segment=segment),5000)
   assertEquals(0L,interval.watched); assertEquals(5000L,interval.uncertain)
  }
 }
}
