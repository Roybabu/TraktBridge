package com.traktbridge.app

import java.util.Locale

data class ScreenLabel(val id:String,val text:String,val heading:Boolean=false,val button:Boolean=false,val x:Double=-1.0,val y:Double=-1.0,val w:Double=0.0,val h:Double=0.0,val seek:Boolean=false)
data class ContentHint(val title:String,val detail:String,val kind:String)
object ContentRecognition {
    private val play=Regex("(?i)^(play|watch|resume|continue watching|watch now|play movie|play episode)(\\s+(s\\d+\\s*e\\d+|episode\\s+\\d+))?$")
    private val episode=Regex("(?i)\\b(S\\d{1,3}\\s*E\\d{1,4}|season\\s+\\d+\\s*[,·•:-]?\\s*episode\\s+\\d+|episode\\s+\\d+)\\b")
    private val metadata=Regex("(?i)^(19|20)\\d{2}$|^\\d+\\s*(min|minutes|h|hr|hours)$|^\\d+h\\s*\\d+m$|^(U/A|TV-|PG-|NC-|R$)")
    private val rejected=setOf("home","search","downloads","my stuff","watchlist","prime video","netflix","mx player","episodes","more like this","related","details","overview","cast","trailer","watch trailer","rent","buy","subscribe","included with prime","add to watchlist","remove from watchlist","share","back","close","settings","audio & subtitles","x-ray","skip intro","skip recap","next episode","continue","play","resume","watch","watch now","play movie","play episode")
    fun usable(text:String):Boolean {
        val t=text.trim()
        return PlayerRules.validTitle(t) && t.length<=100 && t.lowercase(Locale.ROOT) !in rejected && !play.matches(t) && !metadata.containsMatchIn(t) && !episode.matches(t) && !t.contains('\n') && !t.startsWith("http",true) && !t.contains(Regex("(?i)\\b(password|sign in|log in|email|payment|credit card|profile|account)\\b"))
    }
    private val playbackControl=Regex("(?i)^(pause|play|rewind|fast forward|skip (forward|back|intro|recap)|seek|next episode|audio (and|&) subtitles|subtitles|playback speed)([ ,:]+[0-9]+( seconds?)?)?$")
    private val time=Regex("^(-|−)?[0-9]{1,3}:[0-9]{2}(:[0-9]{2})?$")
    private fun region(l:ScreenLabel):Boolean=l.x in 0.0..1.0 && l.y in 0.0..1.0 && (l.y<=0.30 || l.y>=0.70 || l.x<=0.20 || l.x>=0.80)
    private fun control(l:ScreenLabel):Boolean=l.seek || (l.button && playbackControl.matches(l.text.trim()))
    fun playerContext(labels:List<ScreenLabel>,explicit:Boolean):Boolean {
        val controls=labels.filter(::control)
        return explicit || (controls.any { it.seek } && controls.any { !it.seek }) ||
            (controls.count { !it.seek }>=2 && labels.any { time.matches(it.text.trim()) })
    }
    private fun layoutHint(labels:List<ScreenLabel>):ContentHint? {
        val controls=labels.filter(::control)
        if(controls.isEmpty()) return null
        val ranked=labels.filter { !it.button && !it.seek && region(it) && usable(it.text) && !time.matches(it.text.trim()) && !playbackControl.matches(it.text.trim()) && it.h in 0.001..0.16 && it.w>0.025 && it.text.split(Regex("\\s+")).size<=12 }
            .groupBy { it.text.trim() }.map { (title,rows) ->
                title to rows.maxOf { l ->
                    (if(l.heading) 4 else 0)+(if(l.id.contains("title")) 4 else 0)+(if(l.text.matches(Regex("(?i).+\\.(mkv|mp4|avi|webm|mov|m4v)$"))) 4 else 0)+
                    (if(l.y<=0.25 || l.y>=0.75) 2 else 1)+(if(controls.any { kotlin.math.abs(it.y-l.y)<=0.20 && it.y>=0 }) 2 else 0)
                }
            }.sortedByDescending { it.second }
        val first=ranked.firstOrNull() ?: return null
        if(first.second<3 || (ranked.size>1 && first.second-ranked[1].second<2)) return null
        val detail=labels.filter { region(it) }.firstNotNullOfOrNull { episode.find(it.text)?.value }.orEmpty()
        return ContentHint(first.first,detail,"layout")
    }
    data class PlayerTime(val position:Long,val duration:Long)
    private fun millis(value:String):Long? {
        val v=value.trim().removePrefix("-").removePrefix("−")
        if(!time.matches(value.trim())) return null
        val p=v.split(':').map { it.toLongOrNull() ?: return null }
        if(p.drop(1).any { it>=60 }) return null
        return p.fold(0L) { a,b -> a*60+b }*1000
    }
    // Require a seek control and an explicit negative remaining timer; two unsigned timers
    // have ambiguous semantics and are never assumed to mean elapsed + total.
    fun timing(labels:List<ScreenLabel>,player:Boolean):PlayerTime? {
        if(!player || labels.none { it.seek }) return null
        val times=labels.filter { region(it) && time.matches(it.text.trim()) }
        val remaining=times.filter { it.text.trim().startsWith("-") || it.text.trim().startsWith("−") }.mapNotNull { millis(it.text) }.distinct().singleOrNull() ?: return null
        val elapsed=times.filterNot { it.text.trim().startsWith("-") || it.text.trim().startsWith("−") }.mapNotNull { millis(it.text) }.distinct().singleOrNull() ?: return null
        val total=elapsed+remaining
        return if(PlaybackPosition.valid(elapsed,total) && total<=24*60*60*1000L) PlayerTime(elapsed,total) else null
    }
    // YouTube mixes recommendations and playlist titles into the player accessibility tree.
    fun allowAutomaticScreenTitle(pkg:String):Boolean = pkg != YouTubePlayback.PACKAGE
    fun detect(pkg:String,labels:List<ScreenLabel>,player:Boolean):ContentHint? {
        if(!allowAutomaticScreenTitle(pkg)) return null
        val controls=labels.filter { it.button && play.matches(it.text.trim()) }
        val explicit=labels.filter { PlayerRules.titleId(pkg,it.id,player) && usable(it.text) }
        val strong=explicit.filter { !PlayerRules.detailId(it.id) }
        val unique=strong.map { it.text.trim() }.distinct()
        if(player && unique.size==1) return ContentHint(unique.single(),explicit.firstOrNull { PlayerRules.detailId(it.id) && it.text!=unique.single() }?.text.orEmpty(),"player")
        if(player) return layoutHint(labels)
        // A detail-page candidate requires an explicit play action and exactly one plausible
        // heading or explicit detail title. Never infer from arbitrary browse-list text.
        if(controls.isEmpty()) return null
        val heads=labels.filter { !it.button && (it.heading || it.id in setOf("detail_title","details_title","hero_title","title_name","movie_title","show_title","content_title")) && usable(it.text) }.map { it.text.trim() }.distinct()
        val title=heads.singleOrNull() ?: return null
        val detail=labels.firstNotNullOfOrNull { episode.find(it.text)?.value }.orEmpty()
        return ContentHint(title,detail,"details")
    }
}
object PlaybackPosition {
    // Position without a credible runtime is not a usable resume position.
    fun valid(position:Long,duration:Long):Boolean=duration>=60000L && position in 0..duration
    fun sanitize(position:Long,duration:Long):Long=if(valid(position,duration)) position else -1L
}
