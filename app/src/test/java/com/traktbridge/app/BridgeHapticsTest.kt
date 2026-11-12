package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class BridgeHapticsTest {
 @Test fun respectsPreferenceDeviceAndViewSettings() {
  assertTrue(BridgeHaptics.allowed(true,false,true))
  assertFalse(BridgeHaptics.allowed(false,false,true))
  assertFalse(BridgeHaptics.allowed(true,true,true))
  assertFalse(BridgeHaptics.allowed(true,false,false))
 }
}
