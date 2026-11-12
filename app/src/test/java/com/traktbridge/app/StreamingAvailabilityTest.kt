package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class StreamingAvailabilityTest {
 @Test fun regionalSearchEncodesTitleAndRejectsInvalidRegion() {
  assertEquals("IN",StreamingAvailability.region(" in "))
  assertNull(StreamingAvailability.region("https://evil.example"))
  assertEquals("https://www.justwatch.com/in/search?q=A+%26+B",StreamingAvailability.search("A & B","IN"))
  assertNull(StreamingAvailability.trakt("episode",1))
 }
 @Test fun rebindIsThrottledAndDetectsStaleHeartbeat() {
  assertTrue(TrackingHealthPolicy.shouldRebind(100000,1000,60000,0))
  assertFalse(TrackingHealthPolicy.shouldRebind(100000,1000,59000,0))
  assertFalse(TrackingHealthPolicy.shouldRebind(100000,95000,60000,0))
 }
}
