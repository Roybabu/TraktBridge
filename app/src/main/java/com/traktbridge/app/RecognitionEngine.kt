package com.traktbridge.app

import java.util.Locale
import java.text.Normalizer

data class Evidence(val title:String,val detail:String,val source:String,val weight:Int)
data class Recognition(val title:String,val detail:String,val score:Int,val sources:List<String>,val conflict:Boolean)
data class EpisodeNumber(val season:Int,val episode:Int)
data class RankedMatch(val item:CatalogueItem,val score:Int,val reason:String)
object RecognitionEngine {
    private val episode=Regex("(?i)\\bS(\\d{1,3})[ ._-]*E(\\d{1,4})\\b|\\bSeason\\s+(\\d{1,3})\\s*[,·•:-]?\\s*Episode\\s+(\\d{1,4})\\b")
    private val year=Regex("\\b(19\\d{2}|20\\d{2})\\b")
    fun episodeOf(value:String):EpisodeNumber? {
        val m=episode.find(value) ?: return null
        val s=(m.groupValues[1].ifBlank { m.groupValues[3] }).toIntOrNull() ?: return null
        val e=(m.groupValues[2].ifBlank { m.groupValues[4] }).toIntOrNull() ?: return null
        return if(e>0) EpisodeNumber(s,e) else null
    }
    fun normalized(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
    fun query(value:String):String=TitleNormalizer.suggestedQuery(value).replace(Regex("\\s*\\((19\\d{2}|20\\d{2})\\)\\s*$"),"").trim()
    fun fuse(input:List<Evidence>):Recognition? {
        val usable=input.filter { ContentRecognition.usable(it.title) }.distinctBy { it.source }
        if(usable.isEmpty()) return null
        val groups=usable.groupBy { normalized(query(it.title)) }.values.sortedByDescending { rows -> rows.maxOf { it.weight }+((rows.size-1)*12) }
        val top=groups.first()
        val best=top.maxBy { it.weight }
        val episodes=top.mapNotNull { episodeOf(it.title+" "+it.detail) }.distinct()
        val conflict=groups.size>1 || episodes.size>1
        val detail=if(episodes.size==1) "S"+episodes.single().season+" E"+episodes.single().episode else best.detail.takeIf { episodes.size<2 }.orEmpty()
        val score=(top.maxOf { it.weight }+(top.size-1)*12-(if(conflict) 25 else 0)).coerceIn(0,100)
        return Recognition(best.title,detail,score,top.map { it.source },conflict)
    }
    fun rank(title:String,detail:String,duration:Long,items:List<CatalogueItem>):List<RankedMatch> {
        val q=normalized(query(title))
        if(q.isBlank()) return emptyList()
        val y=year.find(title)?.value?.toIntOrNull()
        val ep=episodeOf(title+" "+detail)
        return items.map { item ->
            val n=normalized(item.title)
            val exact=q==n
            val tokens=q.split(' ').toSet(); val other=n.split(' ').toSet()
            val overlap=if(tokens.isEmpty() || other.isEmpty()) 0 else 55*(tokens intersect other).size/(tokens union other).size
            var score=if(exact) 78 else overlap
            val reasons=mutableListOf(if(exact) "Exact title" else "Similar title")
            if(y!=null && item.year>0) { if(y==item.year) { score+=12; reasons.add("year agrees") } else { score-=30; reasons.add("year differs") } }
            if(duration>=60000 && item.runtimeMinutes>0 && item.type=="movie") {
                val runtime=item.runtimeMinutes*60000L
                if(kotlin.math.abs(duration-runtime)<=maxOf(180000L,runtime/20)) { score+=10; reasons.add("runtime agrees") } else { score-=10; reasons.add("runtime differs") }
            }
            if(ep!=null && item.type=="movie") { score-=45; reasons.add("episode evidence") }
            RankedMatch(item,score.coerceIn(0,100),reasons.joinToString(" • "))
        }.sortedByDescending { it.score }
    }
    // Provider-scoped keys include exact episode number; bare series names must never learn episode mappings.
    fun key(source:String,title:String,detail:String,itemType:String):String? {
        if(!ContentRecognition.usable(title)) return null
        val ep=episodeOf(title+" "+detail)
        if(itemType=="episode" && ep==null) return null
        if(itemType=="movie" && ep!=null) return null
        val normalized=normalized(title)
        if(normalized.isBlank()) return null
        return source+"|"+normalized+"|"+(ep?.let { "S"+it.season+"E"+it.episode } ?: "movie")
    }
    fun providerKey(source:String,id:String):String?=if(source.isNotBlank() && id.matches(Regex("[A-Za-z0-9.\\-]{1,200}"))) source+"|provider|"+id else null
}
