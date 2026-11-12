package com.traktbridge.app

data class RecommendationSeed(val type:String,val id:Long,val title:String,val weight:Double)
data class RelatedBatch(val seed:RecommendationSeed,val items:List<CatalogueItem>)
data class PersonalRecommendation(val item:CatalogueItem,val score:Double,val reasons:List<String>)
object RecommendationEngine {
    fun identity(r:WatchRecord):String? = when {
        r.matchType=="movie" && r.traktId>0 -> "movie:"+r.traktId
        r.matchType=="episode" && r.traktId>0 && r.showId>0 -> "show:"+r.showId
        else -> null
    }
    fun seen(records:List<WatchRecord>):Set<String> = records.filter { it.status=="synced" || (it.status in setOf("reviewed","queued","uncertain","blocked") && PlaybackAccuracy.nearEnd(it.position,it.duration)) }.mapNotNull(::identity).toSet()
    fun seeds(records:List<WatchRecord>,now:Long,limit:Int=5):List<RecommendationSeed> {
        return records.distinctBy { it.id }.filter { it.status=="synced" && it.ended in 1..now }.mapNotNull { r -> identity(r)?.let { it to r } }.groupBy({ it.first },{ it.second }).map { (key,rows) ->
            val last=rows.maxBy { it.ended }; val days=(now-last.ended)/86400000.0
            val minutes=rows.sumOf { it.watched.coerceIn(0,86400000L)/60000.0 }
            val weight=1.0+kotlin.math.ln1p(minutes)+2.0/(1.0+days/30.0)
            val label=if(key.startsWith("show:")) last.matchLabel.substringBefore(" — ").substringBefore(" • S") else last.title
            RecommendationSeed(key.substringBefore(":"),key.substringAfter(":").toLong(),label.ifBlank { last.title }.ifBlank { "Viewed title" },weight)
        }.sortedWith(compareByDescending<RecommendationSeed> { it.weight }.thenBy { it.type }.thenBy { it.id }).take(limit.coerceIn(1,10))
    }
    fun rank(batches:List<RelatedBatch>,seen:Set<String>,hidden:Set<String> = emptySet()):List<PersonalRecommendation> {
        data class Acc(val item:CatalogueItem,var score:Double=0.0,val reasons:MutableList<String> = mutableListOf())
        val results=linkedMapOf<String,Acc>()
        val seedKeys=batches.map { it.seed.type+":"+it.seed.id }.toSet()
        for(batch in batches.distinctBy { it.seed.type+":"+it.seed.id }.take(10)) {
            for((index,item) in batch.items.distinctBy { it.type+":"+it.id }.take(30).withIndex()) {
                val key=item.type+":"+item.id
                if(item.type!=batch.seed.type || item.id<=0 || item.title.isBlank() || key in seen || key in hidden || key in seedKeys) continue
                val acc=results.getOrPut(key) { Acc(item) }
                acc.score+=batch.seed.weight/(1.0+index/10.0)
                acc.reasons.add(batch.seed.title)
            }
        }
        return results.values.map { PersonalRecommendation(it.item,it.score,it.reasons.distinct()) }.sortedWith(compareByDescending<PersonalRecommendation> { it.score }.thenBy { it.item.title }).take(40)
    }
}
