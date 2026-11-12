package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class UnifiedLibraryTest {
    @Test fun catalogueNamespacesRemainSeparate() {
        val movie=WatchRecord("a","netflix","Example","",1,2,1,60000,1,"reviewed","movie",42)
        val anime=LocalAnimeEntry(42,"Example",3,12)
        val library=UnifiedLibrary.build(listOf(movie),listOf(anime),"example")
        assertEquals(1,library.movies.size)
        assertEquals(1,library.anime.size)
    }
    @Test fun invalidAnimeProgressIsExcluded() {
        val rows=listOf(LocalAnimeEntry(1,"Good",3,12),LocalAnimeEntry(2,"Bad",13,12),LocalAnimeEntry(0,"Invalid",0,0))
        assertEquals(listOf(1),UnifiedLibrary.build(emptyList(),rows).anime.map { it.id })
    }
}
