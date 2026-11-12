package com.traktbridge.app
import java.net.URI
object PlaybackShortcuts {
    fun shortcut(raw:String):String? {
        val u=runCatching { URI(raw) }.getOrNull() ?: return null
        if(u.scheme!="traktbridge" || u.rawAuthority!="shortcut" || u.rawQuery!=null || u.rawFragment!=null) return null
        return u.rawPath.removePrefix("/").takeIf { it in setOf("home","review","manual","tracking","library","activity") }
    }
    fun providerLink(source:String,id:String):String? = when {
        source=="com.netflix.mediaclient" && id.matches(Regex("[0-9]{5,12}")) -> "https://www.netflix.com/title/"+id
        source=="com.google.android.youtube" && id.matches(Regex("[A-Za-z0-9_-]{11}")) -> "https://www.youtube.com/watch?v="+id
        else -> null
    }
    fun pollDelay(enabled:Boolean,playing:Boolean):Long=if(!enabled) 30000L else if(playing) 5000L else 10000L
}
