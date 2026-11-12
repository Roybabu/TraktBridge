package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceIdentityTest {
    @Test fun fingerprintIsStableAndDistinct() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",DeviceIdentity.fingerprint(byteArrayOf()))
        assertNotEquals(DeviceIdentity.fingerprint(byteArrayOf(1)),DeviceIdentity.fingerprint(byteArrayOf(2)))
    }
}
