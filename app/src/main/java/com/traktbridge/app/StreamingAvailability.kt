package com.traktbridge.app

import java.net.URLEncoder
import java.util.Locale

object StreamingAvailability {
    fun region(value:String):String? = value.trim().uppercase(Locale.ROOT).takeIf { it in Locale.getISOCountries().toSet() }
    fun trakt(type:String,id:Long):String? = if(type in setOf("movie","show") && id>0) "https://trakt.tv/search/trakt/"+id+"?id_type="+type else null
    fun search(title:String,region:String):String? {
        val country=region(region)?.lowercase(Locale.ROOT) ?: return null
        val query=title.trim().takeIf { it.isNotEmpty() && it.length<=300 } ?: return null
        return "https://www.justwatch.com/"+country+"/search?q="+URLEncoder.encode(query,"UTF-8")
    }
}
object TrackingHealthPolicy {
    fun shouldRebind(now:Long,lastSample:Long,elapsed:Long,lastRebind:Long):Boolean =
        (lastSample<=0 || now<lastSample || now-lastSample>20000L) && elapsed-lastRebind>=60000L
}
