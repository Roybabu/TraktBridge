package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DiscoveryEngineTest {
    @Test fun moodRuntimeRatingAndIdentityFilters() {
        fun row(id:Long,genre:String,runtime:Int,rating:Double)=DiscoveryItem(CatalogueItem("movie",id,"Title "+id,runtimeMinutes=runtime),setOf(genre),rating)
        val good=row(1,"comedy",80,8.0)
        val rows=listOf(good,good,row(2,"horror",80,9.0),row(3,"comedy",0,9.0),row(4,"comedy",90,6.0),row(5,"comedy",80,9.0))
        assertEquals(listOf(1L),DiscoveryEngine.filter(rows,DiscoveryEngine.moods.first(),90,7.0,setOf("movie:5")).map { it.item.id })
    }
    @Test fun artworkDecodingUsesBoundedDimensions() {
        assertEquals(1,ArtworkSizing.sample(600,900))
        assertEquals(4,ArtworkSizing.sample(2400,3000))
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsOversizedArtwork() { ArtworkSizing.sample(4096,4096) }
}
