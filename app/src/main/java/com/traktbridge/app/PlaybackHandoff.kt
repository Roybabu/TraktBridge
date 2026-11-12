package com.traktbridge.app

import java.net.URI

/** Only public, provider-specific content identifiers cross devices; never media stream URLs. */
object PlaybackHandoff {
    fun canonical(source:String):String=when(source) {
        "com.netflix.ninja" -> "com.netflix.mediaclient"
        "com.google.android.youtube.tv" -> "com.google.android.youtube"
        "com.amazon.amazonvideo.livingroom" -> "com.amazon.avod.thirdpartyclient"
        else -> source
    }
    fun packages(source:String):List<String> = when(canonical(source)) {
        "com.netflix.mediaclient" -> listOf("com.netflix.ninja","com.netflix.mediaclient")
        "com.google.android.youtube" -> listOf("com.google.android.youtube.tv","com.google.android.youtube")
        "com.amazon.avod.thirdpartyclient" -> listOf("com.amazon.amazonvideo.livingroom","com.amazon.avod.thirdpartyclient")
        else -> listOf(source).filter { it in PlayerRules.apps.values }
    }
    fun validId(source:String,id:String):String = when(canonical(source)) {
        "com.netflix.mediaclient" -> id.takeIf { it.matches(Regex("[0-9]{5,12}")) }.orEmpty()
        "com.google.android.youtube" -> id.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }.orEmpty()
        else -> ""
    }
    fun extract(source:String,raw:String):String {
        if(raw.length>1024) return ""
        val uri=runCatching { URI(raw) }.getOrNull() ?: return ""
        if(uri.scheme!="https" || uri.rawUserInfo!=null || uri.port!=-1) return ""
        val candidate=when(canonical(source)) {
            "com.netflix.mediaclient" -> if(uri.host in setOf("netflix.com","www.netflix.com")) Regex("^/(?:title|watch)/([0-9]{5,12})/?$").matchEntire(uri.path.orEmpty())?.groupValues?.get(1).orEmpty() else ""
            "com.google.android.youtube" -> when {
                uri.host=="youtu.be" -> uri.path.orEmpty().removePrefix("/")
                uri.host in setOf("youtube.com","www.youtube.com","m.youtube.com") && uri.path=="/watch" -> uri.rawQuery.orEmpty().split("&").firstOrNull { it.startsWith("v=") }?.substringAfter("v=").orEmpty()
                else -> ""
            }
            else -> ""
        }
        return validId(source,candidate)
    }
    fun link(source:String,id:String,position:Long):String? {
        val safe=validId(source,id)
        val base=PlaybackShortcuts.providerLink(canonical(source),safe) ?: return null
        return if(canonical(source)=="com.google.android.youtube" && position in 1000..604800000) base+"&t="+(position/1000)+"s" else base
    }
}
