package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test

class ReleaseReadinessTest {
 @Test fun semanticVersionsAreNumeric() {
  assertTrue(UpdateCheck.compare("0.25.0","0.9.9")>0)
  assertEquals(0,UpdateCheck.compare("v0.25.0","0.25.0"))
  assertTrue(UpdateCheck.compare("0.24.0","0.25.0")<0)
 }
 @Test fun invalidVersionsRejected() {
  assertThrows(IllegalArgumentException::class.java) { UpdateCheck.compare("latest","0.25.0") }
 }
 @Test fun completionDoesNotFollowFromUnknownPosition() {
  assertFalse(PlaybackAccuracy.nearEnd(-1,600000))
  assertFalse(PlaybackAccuracy.nearEnd(600000,0))
 }
 @Test fun providerIdsCannotCrossProviders() {
  assertNotEquals(RecognitionEngine.providerKey("netflix","123456"),RecognitionEngine.providerKey("prime","123456"))
 }
}
