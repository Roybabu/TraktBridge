package com.traktbridge.app
import java.net.URI
object ComponentPolicy {
    fun reviewId(raw:String):String? {
        val uri=runCatching { URI(raw) }.getOrNull() ?: return null
        if(uri.scheme!="traktbridge" || uri.rawAuthority!="review" || uri.rawQuery!=null || uri.rawFragment!=null) return null
        return uri.rawPath.removePrefix("/").takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,200}")) }
    }
    fun columns(widthDp:Int,fontScale:Float,tv:Boolean):Int = when {
        fontScale>=1.5f -> 1
        tv || widthDp>=720 -> 2
        else -> 1
    }
}
