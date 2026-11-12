package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceDetectionTest {
    @Test fun screenPolicy() {
        val base=DetectionProfile("Test")
        assertEquals(DeviceDetection.Kind.TABLET,DeviceDetection.kind(false,600))
        assertEquals(DeviceDetection.Kind.TV,DeviceDetection.kind(true,320))
        assertEquals(base,DeviceDetection.profile(base,DeviceDetection.Kind.PHONE))
        val tv=DeviceDetection.profile(base,DeviceDetection.Kind.TV)
        assertFalse(tv.screenTiming)
        assertEquals(60,tv.notificationWeight)
        assertEquals(15000L,DeviceDetection.notificationAge(tv,DeviceDetection.Kind.TV))
    }
}
