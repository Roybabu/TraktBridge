package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class YouTubeTitleProbeTest {
    @Test fun rejectsBrowseAndRecommendations() {
        val labels=listOf(ScreenLabel("video_title","A real video"),ScreenLabel("recommendation_title","Another video"))
        assertNull(YouTubeTitleProbe.screen(labels,false))
        assertEquals("A real video",YouTubeTitleProbe.screen(labels,true))
        assertNull(YouTubeTitleProbe.screen(labels+ScreenLabel("video_title","Different video"),true))
        assertNull(YouTubeTitleProbe.screen(listOf(ScreenLabel("video_title","Subscribe")),true))
    }
    @Test fun comparesSourcesWithoutGuessing() {
        assertEquals("A real video",YouTubeTitleProbe.compare(listOf("A real video"),emptyList(),"A real video").title)
        assertNull(YouTubeTitleProbe.compare(listOf("A real video"),emptyList(),"Different video").title)
        assertNull(YouTubeTitleProbe.compare(emptyList(),emptyList(),null).title)
        assertEquals("Accessibility",YouTubeTitleProbe.compare(emptyList(),emptyList(),"A real video").source)
    }
    @Test fun testWindowAndStaleTitles() {
        YouTubeProbeState.clear()
        try {
            YouTubeProbeState.start(1000)
            YouTubeProbeState.observeScreen(2000,listOf(ScreenLabel("video_title","A real video")),true)
            assertTrue(YouTubeProbeState.report(3000).contains("Candidate: A real video"))
            assertTrue(YouTubeProbeState.report(40000).contains("Candidate: Not identified"))
            assertFalse(YouTubeProbeState.active(122000))
        } finally { YouTubeProbeState.clear() }
    }
}
