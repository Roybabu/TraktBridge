package com.traktbridge.app

import java.time.Instant

enum class TraktData(val title:String,val endpoint:String) {
    HISTORY("Watch history","/sync/history"),
    WATCHLIST("Watchlist","/sync/watchlist"),
    RATINGS("Ratings","/sync/ratings"),
    PLAYBACK("Saved playback progress","/sync/playback"),
    FAVORITES("Favorites","/sync/favorites"),
    MOVIES("Movie collection","/sync/collection/movies"),
    SHOWS("TV collection","/sync/collection/shows");
    fun marker(activity:org.json.JSONObject):String {
        // Use the relevant activity timestamps when available. Missing fields
        // deliberately force a fresh download instead of assuming no change.
        val pairs=when(this) {
            HISTORY->listOf("movies" to "watched_at","episodes" to "watched_at")
            WATCHLIST->listOf("movies" to "watchlisted_at","shows" to "watchlisted_at","seasons" to "watchlisted_at","episodes" to "watchlisted_at")
            RATINGS->listOf("movies" to "rated_at","shows" to "rated_at","seasons" to "rated_at","episodes" to "rated_at")
            PLAYBACK->listOf("movies" to "paused_at","episodes" to "paused_at")
            FAVORITES->listOf("movies" to "favorited_at","shows" to "favorited_at")
            MOVIES->listOf("movies" to "collected_at")
            SHOWS->listOf("episodes" to "collected_at")
        }
        val times=pairs.map { (kind,field)->activity.optJSONObject(kind)?.optString(field).orEmpty() }
        return if(times.any { it.isBlank() }) "" else times.joinToString("|")
    }
}
object TraktDownloadRules {
    const val PAGE_SIZE=250
    const val MAX_PAGES=1000
    fun key(kind:TraktData,item:RemoteItem):String? {
        if(item.id<=0 || item.type !in setOf("movie","show","season","episode") || item.title.isBlank()) return null
        if(kind==TraktData.HISTORY) {
            if(item.type !in setOf("movie","episode") || item.eventId<=0 || runCatching { Instant.parse(item.watchedAt) }.isFailure) return null
            return "event:"+item.eventId
        }
        if(kind==TraktData.RATINGS && item.rating !in 1..10) return null
        if(kind==TraktData.PLAYBACK && item.progress !in 0.0..100.0) return null
        return item.type+":"+item.id
    }
    fun lastPage(page:Int,reportedPages:Int?,received:Int):Boolean =
        received==0 || (reportedPages!=null && reportedPages>=0 && page>=reportedPages)
    fun canSkip(previous:String,current:String,complete:Boolean)=complete && previous.isNotBlank() && previous==current
}
data class TraktSyncResult(val kind:TraktData,val count:Int,val complete:Boolean,val error:String="",val unchanged:Boolean=false)
data class TraktAccountSummary(val results:List<TraktSyncResult>) {
    val complete get()=results.size==TraktData.entries.size && results.all { it.complete && it.error.isBlank() }
}
