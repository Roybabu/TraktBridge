package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class TraktDownloadRulesTest {
    @Test fun repeatEventsPreserved() {
        val a=RemoteItem("movie",123,"Movie",watchedAt="2026-01-01T12:00:00Z",eventId=10)
        assertNotEquals(TraktDownloadRules.key(TraktData.HISTORY,a),TraktDownloadRules.key(TraktData.HISTORY,a.copy(eventId=11)))
        assertEquals(TraktDownloadRules.key(TraktData.WATCHLIST,a),TraktDownloadRules.key(TraktData.WATCHLIST,a.copy(eventId=11)))
        assertNull(TraktDownloadRules.key(TraktData.HISTORY,a.copy(eventId=0)))
        assertNull(TraktDownloadRules.key(TraktData.HISTORY,a.copy(watchedAt="invalid")))
    }
    @Test fun paginationNeverAssumesRequestedLimit() {
        assertFalse(TraktDownloadRules.lastPage(1,null,10))
        assertFalse(TraktDownloadRules.lastPage(1,3,10))
        assertTrue(TraktDownloadRules.lastPage(3,3,10))
        assertTrue(TraktDownloadRules.lastPage(2,null,0))
    }
    @Test fun distinctTypesAndValidRatings() {
        val m=RemoteItem("movie",1,"Movie",rating=8,progress=55.0)
        assertNotEquals(TraktDownloadRules.key(TraktData.WATCHLIST,m),TraktDownloadRules.key(TraktData.WATCHLIST,m.copy(type="show")))
        assertNull(TraktDownloadRules.key(TraktData.RATINGS,m.copy(rating=11)))
        assertNull(TraktDownloadRules.key(TraktData.PLAYBACK,m.copy(progress=-1.0)))
        assertEquals("season:1",TraktDownloadRules.key(TraktData.WATCHLIST,m.copy(type="season")))
    }
    @Test fun refreshSkipRequiresCompleteAndMatchingMarker() {
        assertTrue(TraktDownloadRules.canSkip("a","a",true))
        assertFalse(TraktDownloadRules.canSkip("","",true))
        assertFalse(TraktDownloadRules.canSkip("a","b",true))
        assertFalse(TraktDownloadRules.canSkip("a","a",false))
    }
}
