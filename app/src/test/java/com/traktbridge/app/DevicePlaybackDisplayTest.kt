package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DevicePlaybackDisplayTest {
    @Test fun labelsAndProgress() {
        assertEquals("Playing",DevicePlaybackDisplay.state(3))
        assertEquals("Paused",DevicePlaybackDisplay.state(2))
        assertEquals(50,DevicePlaybackDisplay.progress(5,10))
        assertNull(DevicePlaybackDisplay.progress(-1,10))
        assertNull(DevicePlaybackDisplay.progress(11,10))
    }
}
