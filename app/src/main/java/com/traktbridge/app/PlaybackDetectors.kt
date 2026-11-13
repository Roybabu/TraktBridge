package com.traktbridge.app

/** Explicit registry. New providers are opt-in only after a dedicated detector is implemented. */
object PlaybackDetectors {
    val supported:Set<String> = setOf(YouTubePlayback.PACKAGE)
    fun selected(requested:Set<String>):Set<String> = requested.intersect(supported)
    fun supports(pkg:String):Boolean = pkg in supported
}
