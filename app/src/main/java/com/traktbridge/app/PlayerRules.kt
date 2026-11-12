package com.traktbridge.app

import java.util.Locale

// Names are candidate controls, not assertions about any particular app version.
object PlayerRules {
    val apps=linkedMapOf(
        "Netflix" to "com.netflix.mediaclient","Prime Video" to "com.amazon.avod.thirdpartyclient",
        "JioHotstar" to "in.startv.hotstar","SonyLIV" to "com.sonyliv","ZEE5" to "com.graymatrix.did",
        "Crunchyroll" to "com.crunchyroll.crunchyroid","VLC" to "org.videolan.vlc",
        "MX Player / Amazon MX Player" to "com.mxtech.videoplayer.ad","MX Player Pro" to "com.mxtech.videoplayer.pro",
        "HBO Max" to "com.wbd.stream","HBO Max (legacy)" to "com.hbo.hbonow",
        "Discovery+" to "com.discovery.discoveryplus.mobile","Discovery+ India" to "com.discoveryplus.discoveryplus.mobile",
        "Disney+" to "com.disney.disneyplus","Apple TV" to "com.apple.atve.androidtv.appletv",
        "Paramount+" to "com.cbs.app","Peacock" to "com.peacocktv.peacockandroid","Hulu" to "com.hulu.plus",
        "YouTube" to "com.google.android.youtube","Tubi" to "com.tubitv","Pluto TV" to "tv.pluto.android",
        "Plex" to "com.plexapp.android","Jellyfin" to "org.jellyfin.mobile","Kodi" to "org.xbmc.kodi",
        "Stremio" to "com.stremio.one","Google TV" to "com.google.android.videos",
        "Viki" to "com.viki.android","iQIYI" to "com.iqiyi.i18n","WeTV" to "com.tencent.qqlivei18n",
        "Viu" to "com.vuclip.viu","Hoichoi" to "com.viewlift.hoichoi","Sun NXT" to "com.suntv.sunnxt",
        "aha" to "ahaflix.tv","Lionsgate Play" to "com.lionsgateplay.videoapp","JioTV" to "com.jio.jioplay.tv",
        "MUBI" to "com.mubi","BBC iPlayer" to "bbc.iplayer.android","Bilibili" to "com.bstar.intl",
        "Emby" to "com.mb.android","Just Player" to "com.brouken.player"
    ).apply { ProviderPackages.tv.forEach { (name,pkg) -> if(pkg !in values) put(name,pkg) } }
    private val explicit=setOf("player_title","player_subtitle","playback_title","video_title","episode_title","show_title","player_episode_title","player_show_title","player_title_text","player_episode_label","player_season_episode","video_player_title","player_subtitle_text","media_title","content_title","player_header_title","player_video_title")
    private val local=setOf("title","filename","file_name","media_title","video_title","video_name","player_overlay_title","video_title_text")
    private val generic=setOf("title","subtitle","episode_name","episode_number","season_episode","title_text","txt_title","tv_title","header_title")
    private val playerControls=setOf("exo_play","exo_pause","exo_progress","exo_position","exo_duration","seek_bar","seekbar","play_pause","play_pause_button","video_surface","video_view","surface_view","player_control_view","time_current","time_total","player_controls","video_controls")
    fun id(raw:String)=raw.substringAfterLast('/').lowercase(Locale.ROOT)
    fun isPlayer(id:String)=id in playerControls || id.contains("playback") || id.contains("player_control") || id.contains("video_player") || id.startsWith("exo_") || id=="player"
    fun titleId(pkg:String,id:String,insidePlayer:Boolean):Boolean =
        id in explicit || ((id in generic || (pkg in setOf("org.videolan.vlc","com.mxtech.videoplayer.ad","com.mxtech.videoplayer.pro") && id in local)) && insidePlayer)
    fun detailId(id:String)=id.contains("episode") || id.contains("subtitle") || id.contains("season")
    fun inspectId(id:String)=id in explicit || id in generic || id in local || isPlayer(id) || id.contains("duration") || id.contains("position") || id.contains("remaining")
    fun validTitle(value:String):Boolean {
        val v=value.trim()
        return v.length in 2..160 && v.lowercase(Locale.ROOT) !in setOf("play","pause","resume","next episode","skip intro","skip recap","back","settings","loading","watch now","subtitles","audio","continue watching") && !v.contains('@') && !v.contains("://") && !v.matches(Regex("[0-9: ./%-]+"))
    }
}
