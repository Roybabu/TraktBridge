package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class OcrRulesTest {
 @Test fun edgeCandidatesOnly() {
  val labels=listOf(ScreenLabel("","Movie Title",x=.5,y=.1,w=.4,h=.05),ScreenLabel("","Pause",x=.5,y=.8,w=.1,h=.03),ScreenLabel("","Dialogue",x=.5,y=.5,w=.4,h=.04))
  assertEquals(listOf("Movie Title"),OcrRules.candidates(labels).map { it.title })
 }
 @Test fun timerIsOnlySuggestion() {
  val labels=listOf(ScreenLabel("","Movie Title",x=.5,y=.1,w=.4,h=.05),ScreenLabel("","15:24",x=.1,y=.9,w=.1,h=.02),ScreenLabel("","−39:36",x=.9,y=.9,w=.1,h=.02))
  assertEquals(924000L,OcrRules.candidates(labels).single().position)
 }
}
