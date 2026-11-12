package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class ReliabilitySoakTest {
 @Test fun boundedPlaybackAndCachePolicyOver72SimulatedHours() {
  val interval=5000L
  val total=72*60*60*1000L
  var accumulated=0L
  var tick=0L
  while(tick<total) {
   val delay=BatteryCompatibility.idleDelay(true,false,false)
   assertEquals(interval,delay)
   assertTrue(MemoryPolicy.artworkKiB(128,true)<=2048)
   assertTrue(!PlaybackSharing.fresh(PlaybackSharing.TTL_MS+1))
   tick+=delay
   accumulated+=delay
  }
  assertEquals(total,accumulated)
 }
 @Test fun securityBoundaries() {
  assertFalse(DeviceControl.permits(3,"seek"))
  assertFalse(DeviceControl.valid("invalid"))
  assertFalse(CrossDeviceUploadPolicy.nearby(1,2,0))
  assertFalse(DatabaseProtection.canPrepare(0,1,0,0,true))
 }
}
