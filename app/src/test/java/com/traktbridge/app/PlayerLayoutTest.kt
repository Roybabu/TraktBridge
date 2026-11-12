package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlayerLayoutTest {
 private fun seek()=ScreenLabel("seekbar","",x=.5,y=.86,w=.8,h=.02,seek=true)
 private fun pause()=ScreenLabel("","Pause",button=true,x=.5,y=.5,w=.1,h=.1)
 private fun title(y:Double,x:Double=.5)=ScreenLabel("","The Last of Us",heading=true,x=x,y=y,w=.3,h=.04)
 @Test fun topBottomAndSide() {
  for(t in listOf(title(.1),title(.78),title(.45,.12))) {
   val labels=listOf(t,seek(),pause())
   assertTrue(ContentRecognition.playerContext(labels,false))
   assertEquals("The Last of Us",ContentRecognition.detect("example",labels,true)?.title)
  }
 }
 @Test fun centerSubtitlesIgnored() {
  val labels=listOf(ScreenLabel("","Some dialogue",x=.5,y=.5,w=.4,h=.04),seek(),pause())
  assertNull(ContentRecognition.detect("example",labels,true))
 }
 @Test fun timerPairRequiresNegativeRemaining() {
  val a=ScreenLabel("","15:24",x=.1,y=.9,w=.1,h=.02)
  val b=ScreenLabel("","−39:36",x=.9,y=.9,w=.1,h=.02)
  val result=ContentRecognition.timing(listOf(seek(),a,b),true)
  assertEquals(924000L,result?.position)
  assertEquals(3300000L,result?.duration)
  assertNull(ContentRecognition.timing(listOf(seek(),a,b.copy(text="39:36")),true))
 }
 @Test fun ambiguousTitlesNotChosen() {
  assertNull(ContentRecognition.detect("example",listOf(title(.1),title(.1).copy(text="Another title"),seek(),pause()),true))
 }
}
