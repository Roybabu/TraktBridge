package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlaybackSharingTest {
    @Test fun boundsAndFreshness() {
        assertTrue(PlaybackSharing.fresh(20000))
        assertFalse(PlaybackSharing.fresh(20001))
        assertFalse(PlaybackSharing.fresh(-1))
        assertTrue(PlaybackSharing.frameSize(32768))
        assertFalse(PlaybackSharing.frameSize(32769))
    }
}
