package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class BatteryCompatibilityTest {
 @Test fun activePlaybackCadenceUnchanged() {
  assertEquals(5000L,BatteryCompatibility.idleDelay(true,true,true))
  assertEquals(15000L,BatteryCompatibility.idleDelay(false,false,true))
  assertEquals(10000L,BatteryCompatibility.idleDelay(false,false,false))
  assertTrue(BatteryCompatibility.guidance("Samsung").contains("Sleeping"))
 }
}
