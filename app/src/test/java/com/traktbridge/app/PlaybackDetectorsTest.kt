package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class PlaybackDetectorsTest {
    @Test fun onlyYouTubeIsSupported() {
        assertEquals(setOf(YouTubePlayback.PACKAGE),PlaybackDetectors.supported)
        assertEquals(setOf(YouTubePlayback.PACKAGE),PlaybackDetectors.selected(setOf(YouTubePlayback.PACKAGE,"com.netflix.mediaclient","com.amazon.avod.thirdpartyclient","server.plex","com.google.android.youtube.tv")))
        assertTrue(PlaybackDetectors.selected(setOf("com.netflix.mediaclient")).isEmpty())
        assertTrue(PlaybackDetectors.selected(emptySet()).isEmpty())
    }
}
