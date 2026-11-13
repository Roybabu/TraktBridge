package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class YouTubePlaybackTest {
    @Test fun regularAndShortsAreDistinct() {
        assertEquals(YouTubePlayback.Mode.REGULAR,YouTubePlayback.mode(listOf("watch_player","fullscreen_button"),PlaybackSegment.UNKNOWN))
        assertEquals(YouTubePlayback.Mode.SHORTS,YouTubePlayback.mode(listOf("watch_player","reel_feedback_play"),PlaybackSegment.UNKNOWN))
        assertEquals(YouTubePlayback.Mode.ADVERTISEMENT,YouTubePlayback.mode(listOf("watch_player","skip_ad_button"),PlaybackSegment.UNKNOWN))
        assertEquals(YouTubePlayback.Mode.UNKNOWN,YouTubePlayback.mode(listOf("recommendation_title"),PlaybackSegment.UNKNOWN))
    }
    @Test fun titlePreferenceAndFallback() {
        val title="Jensen Huang Quits As Nvidia CEO | Price Of Sleeping With A Chinese Spy - Or Worse? | Akash Banerjee"
        assertEquals(title,YouTubePlayback.recognition(title,listOf("Previous video"),"")?.title)
        assertEquals("Wayne | Ep 1: \"Get Some Then\"",YouTubePlayback.recognition("",listOf("Wayne | Ep 1: \"Get Some Then\""),"")?.title)
        assertNull(YouTubePlayback.recognition("",listOf("One video","Different video"),""))
    }
    @Test fun excludesShortsEvenWithPriorTitle() {
        val regular=YouTubePlayback.Mode.REGULAR
        val shorts=YouTubePlayback.Mode.SHORTS
        val unknown=YouTubePlayback.Mode.UNKNOWN
        assertTrue(YouTubePlayback.permitted(regular,unknown))
        assertFalse(YouTubePlayback.permitted(regular,shorts))
        assertFalse(YouTubePlayback.allowContinuation(shorts,regular,"Same title","Same title"))
        assertFalse(YouTubePlayback.allowContinuation(unknown,unknown,"Old title","New title"))
        assertTrue(YouTubePlayback.allowContinuation(unknown,unknown,"Same title","Same title"))
        assertFalse(YouTubePlayback.permitted(unknown,unknown))
        assertEquals(shorts,YouTubePlayback.uriMode("https://www.youtube.com/shorts/abcdefghijk"))
        assertEquals(regular,YouTubePlayback.uriMode("https://www.youtube.com/watch?v=abcdefghijk"))
    }
    @Test fun exclusionsStayLatchedUntilRegularPlayer() {
        YouTubePlayback.clear()
        try {
            YouTubePlayback.observe(YouTubePlayback.Mode.SHORTS,1000)
            assertEquals(YouTubePlayback.Mode.SHORTS,YouTubePlayback.screenMode(50000,true))
            YouTubePlayback.observe(YouTubePlayback.Mode.REGULAR,51000)
            assertEquals(YouTubePlayback.Mode.REGULAR,YouTubePlayback.screenMode(52000,true))
            assertEquals(YouTubePlayback.Mode.UNKNOWN,YouTubePlayback.screenMode(90000,true))
        } finally { YouTubePlayback.clear() }
    }
}
