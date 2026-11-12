package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceDiscoveryTest {
    @Test fun filtersUnrelatedNames() {
        assertTrue(DeviceDiscovery.validName("TB-123"))
        assertFalse(DeviceDiscovery.validName("other"))
        assertFalse(DeviceDiscovery.validName("TB-"+"a".repeat(64)))
    }
}
