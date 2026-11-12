package com.traktbridge.app
import java.net.URI
object TitleDetailPolicy {
    fun trailer(raw:String):String? {
        val uri=runCatching { URI(raw) }.getOrNull() ?: return null
        if(uri.scheme!="https" || uri.userInfo!=null || uri.port !in listOf(-1,443) || uri.fragment!=null) return null
        val host=uri.host?.lowercase(java.util.Locale.ROOT) ?: return null
        val video=when {
            host in setOf("www.youtube.com","youtube.com","m.youtube.com") && uri.path=="/watch" -> uri.rawQuery.orEmpty().split('&').filter { it.startsWith("v=") }.singleOrNull()?.substringAfter("=")
            host=="youtu.be" -> uri.rawPath.removePrefix("/")
            else -> null
        }
        return video?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://www.youtube.com/watch?v="+it }
    }
}
