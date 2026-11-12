package com.traktbridge.app

import java.util.Locale

data class ScreenLabel(val id:String,val text:String,val heading:Boolean=false,val button:Boolean=false)
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
    fun detect(pkg:String,labels:List<ScreenLabel>,player:Boolean):ContentHint? {
        val controls=labels.filter { it.button && play.matches(it.text.trim()) }
        val explicit=labels.filter { PlayerRules.titleId(pkg,it.id,player) && usable(it.text) }
        val strong=explicit.filter { !PlayerRules.detailId(it.id) }
        val unique=strong.map { it.text.trim() }.distinct()
        if(player && unique.size==1) return ContentHint(unique.single(),explicit.firstOrNull { PlayerRules.detailId(it.id) && it.text!=unique.single() }?.text.orEmpty(),"player")
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
