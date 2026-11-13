package com.traktbridge.app

import org.junit.Assert.*
import org.junit.Test

class AutoMatchPolicyTest {
    @Test fun parsesObservedYouTubeTitles() {
        val wayne=AutoMatchPolicy.query("Wayne | Ep 1: \"Get Some Then\"")!!
        assertEquals("Wayne",wayne.title)
        assertEquals(1,wayne.episode)
        assertEquals("Get Some Then",wayne.episodeTitle)
        val dhindora=AutoMatchPolicy.query("Dhindora | EP 02: Baasi Roti | BB Ki Vines")!!
        assertEquals("Dhindora",dhindora.title)
        assertEquals(2,dhindora.episode)
        assertEquals("Baasi Roti",dhindora.episodeTitle)
        assertNull(dhindora.season)
    }
    @Test fun resolvesEpisodeWithoutGuessingSeason() {
        val q=AutoMatchPolicy.query("Wayne | Ep 1: \"Get Some Then\"")!!
        val first=CatalogueItem("episode",11,"Wayne — Get Some Then",2019,1,1,1)
        val other=CatalogueItem("episode",12,"Wayne — Different title",2019,1,2,1)
        assertEquals(first,AutoMatchPolicy.episodeMatches(q,listOf(first,other)))
        assertNull(AutoMatchPolicy.episodeMatches(q,listOf(other)))
        assertNull(AutoMatchPolicy.episodeMatches(q,listOf(first,first.copy(id=13,season=3))))
    }
    @Test fun rejectsUnrelatedTitlesAndShortClips() {
        val q=AutoMatchPolicy.query("Wayne | Ep 1: Get Some Then")!!
        val show=CatalogueItem("show",1,"Wayne",2019)
        val unrelated=CatalogueItem("show",2,"Wayne's World",1992)
        val movie=CatalogueItem("movie",3,"Wayne",2019)
        assertEquals(listOf(show),AutoMatchPolicy.rank(q,"Wayne",1200000,listOf(unrelated,movie,show)).map { it.item })
        val clip=AutoMatchPolicy.query("Example Movie")!!
        assertTrue(AutoMatchPolicy.rank(clip,"Example Movie",60000,listOf(CatalogueItem("movie",4,"Example Movie",2020,runtimeMinutes=120))).isEmpty())
    }
}
