package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceControlTest {
 @Test fun allowlist() { assertTrue(DeviceControl.permits(1,"play")); assertFalse(DeviceControl.permits(1,"pause")); assertFalse(DeviceControl.permits(3,"seek")); assertFalse(DeviceControl.valid("bad")) }
}
