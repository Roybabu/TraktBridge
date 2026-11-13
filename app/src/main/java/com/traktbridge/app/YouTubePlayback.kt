package com.traktbridge.app

/** YouTube-only policy: fail closed when regular playback cannot be established. */
object YouTubePlayback {
    enum class Mode { REGULAR, SHORTS, ADVERTISEMENT, UNKNOWN }
    fun mode(ids:Collection<String>,segment:PlaybackSegment):Mode {
        if(segment==PlaybackSegment.ADVERTISEMENT || ids.any { it in setOf("ad_label","ad_badge","skip_ad_button","ad_skip_button","ad_countdown","ad_pod_text") }) return Mode.ADVERTISEMENT
        if(ids.any { it.startsWith("reel_") || it.startsWith("shorts_player") || it=="shorts_video_player" }) return Mode.SHORTS
        if(ids.any { it in setOf("watch_player","player_control_play_pause_replay_button","player_control_previous_button","player_control_next_button","fullscreen_button","time_bar_total_time") }) return Mode.REGULAR
        return Mode.UNKNOWN
    }
    fun uriMode(raw:String):Mode {
        val uri=runCatching { java.net.URI(raw) }.getOrNull() ?: return Mode.UNKNOWN
        if(uri.scheme!="https" || uri.host !in setOf("youtube.com","www.youtube.com","m.youtube.com")) return Mode.UNKNOWN
        return when {
            uri.path.orEmpty().startsWith("/shorts/") -> Mode.SHORTS
            uri.path=="/watch" && PlaybackHandoff.extract(YouTubeTitleProbe.PACKAGE,raw).isNotEmpty() -> Mode.REGULAR
            else -> Mode.UNKNOWN
        }
    }
    fun permitted(screen:Mode,uri:Mode):Boolean =
        screen !in setOf(Mode.SHORTS,Mode.ADVERTISEMENT) && uri!=Mode.SHORTS &&
            (screen==Mode.REGULAR || uri==Mode.REGULAR)
    fun recognition(direct:String,notes:List<String>,detail:String):Recognition? {
        val title=YouTubeTitleProbe.clean(direct)
        if(title!=null) return Recognition(title,detail,85,listOf("media"),false)
        val note=notes.mapNotNull(YouTubeTitleProbe::clean).distinct().singleOrNull() ?: return null
        return Recognition(note,"",65,listOf("notification"),false)
    }
}
