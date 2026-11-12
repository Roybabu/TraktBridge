package com.traktbridge.app

/** Explicit package aliases; selection remains per installed package, never inferred consent. */
object ProviderPackages {
    val tv=linkedMapOf(
        "Netflix (TV)" to "com.netflix.ninja",
        "Prime Video (TV)" to "com.amazon.amazonvideo.livingroom",
        "YouTube (TV)" to "com.google.android.youtube.tv",
        "Jellyfin (TV)" to "org.jellyfin.androidtv",
        "Emby (TV)" to "tv.emby.embyatv",
        "Crunchyroll (TV)" to "com.crunchyroll.crunchyroid",
        "Plex (TV)" to "com.plexapp.android"
    )
    private val aliases=mapOf("com.netflix.ninja" to "com.netflix.mediaclient","com.amazon.amazonvideo.livingroom" to "com.amazon.avod.thirdpartyclient","com.google.android.youtube.tv" to "com.google.android.youtube","org.jellyfin.androidtv" to "org.jellyfin.mobile","tv.emby.embyatv" to "com.mb.android")
    fun canonical(pkg:String)=aliases[pkg] ?: pkg
    fun variants(pkg:String):List<String> {
        val canonical=canonical(pkg)
        return (aliases.filterValues { it==canonical }.keys.toList()+canonical).distinct()
    }
}
