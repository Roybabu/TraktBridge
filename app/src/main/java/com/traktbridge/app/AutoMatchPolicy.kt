package com.traktbridge.app

/** Catalogue suggestions are not playback detection and never approve a watch. */
object AutoMatchPolicy {
    data class Query(val title:String,val season:Int?,val episode:Int?,val episodeTitle:String)
    private val episodeOnly=Regex("(?i)\\b(?:ep\\.?|episode)\\s*0*(\\d{1,4})\\b")
    fun query(raw:String,detail:String=""):Query? {
        val parts=raw.split('|').map { it.trim() }.filter { it.isNotBlank() }
        val explicit=RecognitionEngine.episodeOf(raw+" "+detail)
        val ep=explicit?.episode ?: episodeOnly.find(raw)?.groupValues?.get(1)?.toIntOrNull()
        val first=parts.firstOrNull().orEmpty()
        val title=RecognitionEngine.query(first.replace(episodeOnly,"").trim(' ','-',':'))
        if(title.length !in 2..160 || !DetectionProfiles.validMetadata(title)) return null
        val episodeTitle=if(ep!=null) parts.drop(1).firstOrNull { episodeOnly.containsMatchIn(it) }?.replace(episodeOnly,"")?.trim(' ',':','-','"').orEmpty() else ""
        return Query(title,explicit?.season,ep,episodeTitle)
    }
    fun rank(q:Query,raw:String,duration:Long,items:List<CatalogueItem>):List<RankedMatch> {
        val normalized=RecognitionEngine.normalized(q.title)
        if(normalized.isBlank()) return emptyList()
        return items.filter { it.type in setOf("movie","show") && it.id>0 && (q.episode==null || it.type=="show") }.mapNotNull { item ->
            val n=RecognitionEngine.normalized(item.title)
            val tokens=normalized.split(' ').toSet()
            val other=n.split(' ').toSet()
            val overlap=(tokens intersect other).size.toDouble()/(tokens union other).size.coerceAtLeast(1)
            val exact=n==normalized
            if(!exact && (overlap<0.75 || tokens.size<2)) return@mapNotNull null
            // A short clip with a movie's name is not a useful movie suggestion.
            if(item.type=="movie" && duration>0 && item.runtimeMinutes>0 && duration<item.runtimeMinutes*60000L/3) return@mapNotNull null
            val ranked=RecognitionEngine.rank(q.title,"",duration,listOf(item)).single()
            RankedMatch(item,if(exact) ranked.score.coerceAtLeast(75) else (overlap*70).toInt(),if(exact) "Title agrees" else "Close title")
        }.sortedByDescending { it.score }.take(3)
    }
    fun episodeMatches(q:Query,episodes:List<CatalogueItem>):CatalogueItem? {
        val number=q.episode ?: return null
        val candidates=episodes.filter { it.type=="episode" && it.episode==number && (q.season==null || it.season==q.season) }
        if(q.episodeTitle.isNotBlank()) {
            val expected=RecognitionEngine.normalized(q.episodeTitle)
            val named=candidates.filter { RecognitionEngine.normalized(it.title.substringAfter(" — ",it.title))==expected }
            if(named.size==1) return named.single()
            if(named.size>1) return null
        }
        return candidates.singleOrNull()
    }
}
