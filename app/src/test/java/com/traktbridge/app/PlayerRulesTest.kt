package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlayerRulesTest {
 @Test fun genericTitleRequiresPlayerContext() {
  assertFalse(PlayerRules.titleId("com.example.video","title",false))
  assertTrue(PlayerRules.titleId("com.example.video","title",true))
 }
 @Test fun explicitTitleRecognized() { assertTrue(PlayerRules.titleId("com.example.video","player_title",false)) }
 @Test fun rejectsControlsAndSensitivePatterns() {
  assertFalse(PlayerRules.validTitle("Play")); assertFalse(PlayerRules.validTitle("name@example.com")); assertFalse(PlayerRules.validTitle("12:34")); assertTrue(PlayerRules.validTitle("The Last of Us"))
 }
 @Test fun localFileTitleSupported() { assertTrue(PlayerRules.titleId("org.videolan.vlc","filename",true)) }
}
