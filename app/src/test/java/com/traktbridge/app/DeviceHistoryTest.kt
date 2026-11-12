package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceHistoryTest {
    private val now=1700000000000L
    private val a=DeviceHistory.Event("12345678-1234-1234-1234-123456789abc","com.netflix.mediaclient","Example","S1 E1",now-60000,now,50000,120000,50000)
    @Test fun boundsAndOverlap() {
        assertTrue(DeviceHistory.valid(a,now))
        assertFalse(DeviceHistory.valid(a.copy(watched=1),now))
        assertFalse(DeviceHistory.valid(a.copy(position=130000),now))
        assertTrue(DeviceHistory.duplicate(a,a.copy(id="other",source="com.netflix.ninja")))
        assertFalse(DeviceHistory.duplicate(a,a.copy(id="other",detail="S1 E2")))
        assertFalse(DeviceHistory.duplicate(a,a.copy(id="other",started=now+1000,ended=now+61000)))
    }
}
