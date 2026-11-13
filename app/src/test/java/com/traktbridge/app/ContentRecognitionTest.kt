package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ContentRecognitionTest {
 @Test fun youtubeDoesNotGuessTitlesFromPlayerOrBrowseScreens() {
  val labels=listOf(ScreenLabel("video_title","Recommended video",heading=true,x=0.5,y=0.1,w=0.5,h=0.05), ScreenLabel("","Pause",button=true), ScreenLabel("","",seek=true))
  assertNull(ContentRecognition.detect("com.google.android.youtube",labels,true))
  assertNull(ContentRecognition.detect("com.google.android.youtube",listOf(ScreenLabel("","Other video",heading=true),ScreenLabel("","Play",button=true)),false))
  assertFalse(ContentRecognition.allowAutomaticScreenTitle("com.google.android.youtube"))
  assertTrue(ContentRecognition.allowAutomaticScreenTitle("other.player"))
 }
 @Test fun detailHeadingNeedsPlayAction() {
  val title=ScreenLabel("","The Last of Us",heading=true)
  assertNull(ContentRecognition.detect("example",listOf(title),false))
  assertEquals("The Last of Us",ContentRecognition.detect("example",listOf(title,ScreenLabel("","Play",button=true)),false)?.title)
 }
 @Test fun ambiguousBrowseScreenRejected() {
  assertNull(ContentRecognition.detect("example",listOf(ScreenLabel("","Movie A",true),ScreenLabel("","Movie B",true),ScreenLabel("","Play",button=true)),false))
 }
 @Test fun episodeHintRetained() {
  val h=ContentRecognition.detect("example",listOf(ScreenLabel("","The Last of Us",true),ScreenLabel("","S01 E02"),ScreenLabel("","Play",button=true)),false)
  assertEquals("S01 E02",h?.detail)
 }
 @Test fun rejectsPositionWithoutDuration() {
  assertEquals(-1L,PlaybackPosition.sanitize(1706,0))
  assertEquals(120000L,PlaybackPosition.sanitize(120000,600000))
  assertEquals(-1L,PlaybackPosition.sanitize(700000,600000))
 }
}
