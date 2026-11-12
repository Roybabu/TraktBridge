package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DetectionProfilesTest {
 @Test fun profilesAndFallback() {
  assertEquals("Netflix",DetectionProfiles.forPackage("com.netflix.mediaclient").label)
  assertFalse(DetectionProfiles.forPackage("unknown.app").screenTiming)
  assertFalse(DetectionProfiles.validMetadata("Advertisement"))
  assertFalse(DetectionProfiles.validMetadata("YouTube"))
  assertTrue(DetectionProfiles.validMetadata("The Matrix"))
 }
}
