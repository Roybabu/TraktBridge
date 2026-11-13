package com.traktbridge.app

import java.util.Locale

/** Experimental YouTube title candidates. Never used as proof of playback. */
object YouTubeTitleProbe {
    const val PACKAGE="com.google.android.youtube"
    private val titleIds=setOf("video_title","watch_title","watch_metadata_title","video_title_text","player_title","player_title_text","player_overlay_title","player_video_title","video_player_title","slim_video_title")
    private val chrome=setOf("youtube","youtube premium","home","shorts","subscriptions","you","library","search","share","subscribe","comments","description","more","show more","skip ad","sponsored","advertisement","like","dislike","next","previous")
    fun clean(raw:String):String? {
        val title=raw.trim()
        return title.takeIf { PlayerRules.validTitle(it) && it.lowercase(Locale.ROOT) !in chrome && !it.contains('\n') }
    }
    fun screen(labels:List<ScreenLabel>,player:Boolean):String? {
        if(!player) return null
        val titles=labels.filter { it.id in titleIds && !it.seek }.mapNotNull { clean(it.text) }.distinct()
        return titles.singleOrNull()
    }
    data class Result(val title:String?,val source:String)
    fun compare(media:List<String>,notifications:List<String>,screen:String?):Result {
        val m=media.mapNotNull(::clean).distinct()
        val n=notifications.mapNotNull(::clean).distinct()
        val a=screen?.let(::clean)
        val all=(m+n+listOfNotNull(a)).distinct()
        if(all.size>1) return Result(null,"Conflicting titles — verify manually")
        if(all.isEmpty()) return Result(null,"No title available")
        val source=listOfNotNull("MediaSession".takeIf { m.isNotEmpty() },"Media notification".takeIf { n.isNotEmpty() },"Accessibility".takeIf { a!=null }).joinToString(" + ")
        return Result(all.single(),source)
    }
}
