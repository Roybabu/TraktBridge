package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class TrackingRecoveryTest {
    @Test fun onlyPreviouslyEnabledAndUnlocked() {
        assertTrue(TrackingRecovery.shouldStart(true,true,false,true))
        assertFalse(TrackingRecovery.shouldStart(false,true,false,true))
        assertFalse(TrackingRecovery.shouldStart(true,false,false,true))
        assertFalse(TrackingRecovery.shouldStart(true,true,true,true))
        assertFalse(TrackingRecovery.shouldStart(true,true,false,false))
    }
}
