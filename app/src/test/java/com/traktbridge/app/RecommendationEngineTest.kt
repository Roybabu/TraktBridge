package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class RecommendationEngineTest {
    private fun row(id:String,trakt:Long,watched:Long,status:String="synced")=WatchRecord(id,"netflix","Title "+id,"",1,1000,watched,600000,10000,status,"movie",trakt)
    @Test fun seedsFavorMeasuredViewingAndExcludeUnconfirmed() {
        val seeds=RecommendationEngine.seeds(listOf(row("a",1,60000),row("b",2,600000),row("c",3,900000,"pending")),2000)
        assertEquals(listOf(2L,1L),seeds.map { it.id })
    }
    @Test fun mergeRelatedSourcesAndExcludeSeenAndHidden() {
        val a=RecommendationSeed("movie",1,"A",3.0); val b=RecommendationSeed("movie",2,"B",2.0)
        fun item(id:Long)=CatalogueItem("movie",id,"Title "+id)
        val rows=RecommendationEngine.rank(listOf(RelatedBatch(a,listOf(item(3),item(4),item(5))),RelatedBatch(b,listOf(item(3),item(6)))),setOf("movie:4"),setOf("movie:5"))
        assertEquals(listOf(3L,6L),rows.map { it.item.id }); assertEquals(listOf("A","B"),rows.first().reasons)
    }
    @Test fun noHistoryHasNoPersonalSeeds() { assertTrue(RecommendationEngine.seeds(emptyList(),2000).isEmpty()) }
}
