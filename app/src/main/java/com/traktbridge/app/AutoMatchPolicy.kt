package com.traktbridge.app

/** Catalogue matching only. A suggestion never proves playback or approves a watch. */
object AutoMatchPolicy {
    enum class Kind { MOVIE, EPISODE, UNKNOWN, EXCLUDED }
    data class Query(val title:String,val season:Int?,val episode:Int?,val episodeTitle:String,val year:Int?=null,val kind:Kind=Kind.UNKNOWN,val variants:List<String> = emptyList())
    private val episodeOnly=Regex("(?i)\\b(?:ep\\.?|episode)\\s*0*(\\d{1,4})\\b")
    private val yearPattern=Regex("\\b(19\\d{2}|20\\d{2})\\b")
    private val noise=Regex("(?i)\\s*(?:[-|:]\\s*)?\\b(?:full movie|full episode|official full movie|official video|hindi dubbed|english dubbed|dubbed|with subtitles|english subtitles|1080p|2160p|720p|4k|hd|uhd)\\b.*$")
    private val excluded=Regex("(?i)\\b(?:official trailer|teaser trailer|behind the scenes|reaction video|movie recap|movie explained|movie review|episode review|podcast|news bulletin|official music video|movie clip|film clip|highlights)\\b")
    private val chapterPrefix=Regex("(?i)^\\s*(?:chapter|part|episode|ep\\.?)\\s+(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|[ivxlcdm]+)\\s*[:.\\-–—]\\s*")
    private val aliases=mapOf("money heist" to listOf("La Casa de Papel"),"la casa de papel" to listOf("Money Heist"),"john wick 4" to listOf("John Wick: Chapter 4"),"john wick chapter 4" to listOf("John Wick 4"))
    fun query(raw:String,detail:String=""):Query? {
        val evidence=raw+" "+detail
        if(RecognitionEngine.ambiguousEpisode(evidence) || excluded.containsMatchIn(evidence)) return null
        val parts=raw.split('|').map { it.trim() }.filter { it.isNotBlank() }
        val explicit=RecognitionEngine.episodeOf(evidence)
        val numbers=episodeOnly.findAll(evidence).mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().toList()
        if(numbers.size>1 || (explicit!=null && numbers.any { it!=explicit.episode })) return null
        val ep=explicit?.episode ?: numbers.singleOrNull()
        if(ep!=null && ep<=0) return null
        val first=parts.firstOrNull().orEmpty()
        val year=yearPattern.find(first)?.value?.toIntOrNull()
        val title=RecognitionEngine.query(first.substringBeforeEpisode().replace(noise,"").replace(Regex("\\s*\\((?:19|20)\\d{2}\\)"),"").trim(' ','-',':'))
        if(title.length !in 2..160 || !DetectionProfiles.validMetadata(title)) return null
        val epPart=parts.firstOrNull { episodeOnly.containsMatchIn(it) && it!=first } ?: first
        val episodeTitle=if(ep!=null) episodeOnly.find(epPart)?.let { epPart.substring(it.range.last+1).trim(' ',':','-','"','–','—') }.orEmpty() else ""
        val kind=if(ep!=null) Kind.EPISODE else if(Regex("(?i)\\bfull movie\\b").containsMatchIn(raw)) Kind.MOVIE else Kind.UNKNOWN
        val variants=(listOf(title)+aliases[RecognitionEngine.normalized(title)].orEmpty()+listOf(title.replace(Regex("(?i)\\bchapter\\s+(?=\\d)"),"").trim())).filter { it.isNotBlank() }.distinct().take(3)
        return Query(title,explicit?.season,ep,episodeTitle,year,kind,variants)
    }
    private fun String.substringBeforeEpisode():String {
        val match=episodeOnly.find(this) ?: return this
        return substring(0,match.range.first)
    }
    private fun norm(value:String)=RecognitionEngine.normalized(value)
    private fun compact(value:String)=norm(value).replace(" ","")
    private fun distance(a:String,b:String):Int {
        var prior=IntArray(b.length+1) { it }
        for(i in a.indices) {
            val next=IntArray(b.length+1); next[0]=i+1
            for(j in b.indices) next[j+1]=minOf(next[j]+1,prior[j+1]+1,prior[j]+if(a[i]==b[j]) 0 else 1)
            prior=next
        }
        return prior[b.length]
    }
    fun similarity(a:String,b:String):Double {
        val x=norm(a); val y=norm(b)
        if(x.isBlank() || y.isBlank()) return 0.0
        if(x==y || compact(x)==compact(y)) return 1.0
        val xs=x.split(' ').toSet(); val ys=y.split(' ').toSet()
        val j=(xs intersect ys).size.toDouble()/(xs union ys).size
        val edit=1.0-distance(compact(x),compact(y)).toDouble()/maxOf(compact(x).length,compact(y).length)
        return maxOf(j,edit.coerceAtLeast(0.0))
    }
    private fun digits(s:String)=Regex("\\d+").findAll(s).map { it.value }.toList()
    private fun titleSimilarity(q:Query,title:String):Double {
        if(digits(q.title)!=digits(title) && (digits(q.title).isNotEmpty() || digits(title).isNotEmpty())) return 0.0
        return (listOf(q.title)+q.variants).maxOf { similarity(it,title) }
    }
    private fun runtimeAgrees(duration:Long,minutes:Int):Boolean? {
        if(duration<=0 || minutes<=0) return null
        val expected=minutes*60000L
        return kotlin.math.abs(duration-expected)<=maxOf(180000L,expected/8)
    }
    fun rank(q:Query,raw:String,duration:Long,items:List<CatalogueItem>,confirmedShows:Set<Long> = emptySet()):List<RankedMatch> {
        if(excluded.containsMatchIn(raw)) return emptyList()
        return items.filter { it.type in setOf("movie","show") && it.id>0 && (q.kind!=Kind.EPISODE || it.type=="show") && (q.kind!=Kind.MOVIE || it.type=="movie") }.mapNotNull { item ->
            val sim=titleSimilarity(q,item.title)
            val known=item.type=="show" && item.id in confirmedShows
            if(sim<0.80 && !known) return@mapNotNull null
            if(q.year!=null && item.year>0 && q.year!=item.year) return@mapNotNull null
            if(item.type=="movie" && duration>0 && item.runtimeMinutes>0 && duration<item.runtimeMinutes*60000L/3) return@mapNotNull null
            val runtime=if(item.type=="movie") runtimeAgrees(duration,item.runtimeMinutes) else null
            val score=(sim*85).toInt()+(if(runtime==true) 10 else if(runtime==false) -15 else 0)+(if(q.year!=null && item.year==q.year) 5 else 0)+(if(known) 10 else 0)
            if(score<60) return@mapNotNull null
            RankedMatch(item,score.coerceIn(0,100),listOfNotNull(if(sim==1.0) "Title agrees" else "Close title",if(known) "Previously confirmed series" else null,if(runtime==true) "Runtime agrees" else null,if(q.year!=null && item.year==q.year) "Year agrees" else null).joinToString(" • "))
        }.distinctBy { it.item.type+":"+it.item.id }.sortedByDescending { it.score }.take(3)
    }
    private fun episodeName(value:String)=norm(value.replace(chapterPrefix,""))
    fun episodeMatches(q:Query,episodes:List<CatalogueItem>,duration:Long=0):CatalogueItem? {
        val number=q.episode ?: return null
        val candidates=episodes.filter { it.type=="episode" && it.episode==number && (q.season==null || it.season==q.season) }
        if(q.episodeTitle.isNotBlank()) {
            val expected=episodeName(q.episodeTitle)
            if(expected.isBlank()) return null
            val ranked=candidates.map { it to similarity(expected,episodeName(it.title.substringAfter(" — ",it.title))) }.filter { it.second>=0.86 }.sortedByDescending { it.second }
            val first=ranked.firstOrNull() ?: return null
            if(ranked.size>1 && first.second-ranked[1].second<0.10) {
                val agreeing=ranked.filter { runtimeAgrees(duration,it.first.runtimeMinutes)==true }
                return agreeing.singleOrNull()?.first
            }
            return first.first
        }
        return candidates.singleOrNull()
    }
}
