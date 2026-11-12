package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DevicePairingTest {
    @Test fun pinFormatAndInvalidTokens() {
        assertTrue(DevicePairing.verificationCode("sample").matches(Regex("[0-9]{8}")))
        assertEquals(DevicePairing.verificationCode("sample"),DevicePairing.verificationCode("sample"))
        assertTrue(runCatching { DevicePairing.verify("invalid","00000000") }.isFailure)
    }
}
