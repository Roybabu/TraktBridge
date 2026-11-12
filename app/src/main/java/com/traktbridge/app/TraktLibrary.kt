package com.traktbridge.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.URL
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

data class RemoteItem(val type:String,val id:Long,val title:String,val showId:Long=0,val season:Int=-1,val episode:Int=-1,val watchedAt:String="",val eventId:Long=0,val rating:Int=0,val progress:Double=-1.0)
data class RemotePage(val items:List<RemoteItem>,val page:Int,val pages:Int?,val account:String)
data class RemoteProgress(val aired:Int,val completed:Int,val next:String)
object RemoteRules {
    fun plural(type:String):String=when(type) { "movie"->"movies"; "show"->"shows"; "episode"->"episodes"; else->error("Unsupported type") }
    fun identity(type:String,id:Long):String { require(id>0); return plural(type)+":"+id }
    fun rating(value:Int):Int { require(value in 1..10); return value }
    fun historyKey(account:String,eventId:Long):String { require(account.isNotBlank() && eventId>0); return account+":"+eventId }
}
data class UpcomingEpisode(val show:String,val title:String,val season:Int,val episode:Int,val airs:String)
object TraktLibrary {
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private data class Response(val text:String,val pages:Int?)
    private fun request(token:String,path:String,body:JSONObject?=null):Response {
        val c=URL("https://api.trakt.tv"+path).openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects=false; c.connectTimeout=15000; c.readTimeout=15000
            c.setRequestProperty("Authorization","Bearer "+token)
            c.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID)
            c.setRequestProperty("trakt-api-version","2")
            c.setRequestProperty("Accept","application/json")
            if(body!=null) {
                c.requestMethod="POST"; c.doOutput=true; c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code=c.responseCode
            DebugTrace.event("library","HTTP "+code+" method="+c.requestMethod)
            if(code !in 200..299) error("Trakt HTTP "+code)
            val text=c.inputStream.bufferedReader().use { reader ->
                val out=StringBuilder(); val buf=CharArray(8192)
                while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=8_000_000) { "Response too large" }; out.append(buf,0,n) }; out.toString()
            }
            return Response(text,c.getHeaderField("X-Pagination-Page-Count")?.toIntOrNull())
        } finally { c.disconnect() }
    }
    private fun account(token:String):String {
        val ids=JSONObject(request(token,"/users/settings").text).getJSONObject("user").getJSONObject("ids")
        return ids.optString("uuid").takeIf { it.isNotBlank() } ?: ids.optString("slug").takeIf { it.isNotBlank() } ?: error("Account identity unavailable")
    }
    private fun <T> task(context:Context,done:(Result<T>)->Unit,work:(String,String)->T) {
        val app=context.applicationContext
        worker.execute {
            val result=runCatching { TraktAuth(app).authorized { token -> work(token,account(token)) } }
            main.post { done(result) }
        }
    }
    private fun id(o:JSONObject?)=o?.optJSONObject("ids")?.optLong("trakt",0) ?: 0
    private fun parse(o:JSONObject):RemoteItem? {
        val type=o.optString("type")
        if(type !in setOf("movie","show","episode")) return null
        val item=o.optJSONObject(type) ?: return null
        val itemId=id(item); if(itemId<=0) return null
        val show=o.optJSONObject("show")
        val name=if(type=="episode") show?.optString("title").orEmpty()+" — "+item.optString("title")+" • S"+item.optInt("season")+" E"+item.optInt("number") else item.optString("title")
        if(name.isBlank()) return null
        return RemoteItem(type,itemId,name,id(show),item.optInt("season",-1),item.optInt("number",-1),o.optString("watched_at"),o.optLong("id",0),o.optInt("rating",0),o.optDouble("progress",-1.0))
    }
    fun page(context:Context,kind:String,page:Int,done:(Result<RemotePage>)->Unit)=task(context,done) { token,account ->
        require(page in 1..10000)
        val endpoint=when(kind) { "history"->"/sync/history"; "watchlist"->"/sync/watchlist"; "ratings"->"/sync/ratings"; "playback"->"/sync/playback"; else->error("Unknown library") }
        val response=request(token,endpoint+"?page="+page+"&limit=100")
        val a=JSONArray(response.text)
        RemotePage((0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) },page,response.pages,account)
    }
    fun progress(context:Context,showId:Long,done:(Result<RemoteProgress>)->Unit)=task(context,done) { token,_ ->
        require(showId>0)
        val o=JSONObject(request(token,"/shows/"+showId+"/progress/watched").text)
        val next=o.optJSONObject("next_episode")
        RemoteProgress(o.optInt("aired"),o.optInt("completed"),next?.let { "S"+it.optInt("season")+" E"+it.optInt("number")+" • "+it.optString("title") }.orEmpty())
    }
    fun mutate(context:Context,expectedAccount:String,item:RemoteItem,action:String,rating:Int=0,done:(Result<String>)->Unit)=task(context,done) { token,account ->
        require(account==expectedAccount) { "Connected Trakt account changed" }
        require(item.id>0)
        val plural=RemoteRules.plural(item.type)
        val entry=JSONObject().put("ids",JSONObject().put("trakt",item.id))
        val path=when(action) {
            "watchlist-add"->"/sync/watchlist"
            "watchlist-remove"->"/sync/watchlist/remove"
            "rate"->{ entry.put("rating",RemoteRules.rating(rating)); "/sync/ratings" }
            "unrate"->"/sync/ratings/remove"
            else->error("Unsupported action")
        }
        val response=JSONObject(request(token,path,JSONObject().put(plural,JSONArray().put(entry))).text)
        val notFound=response.optJSONObject("not_found")?.optJSONArray(plural)?.length() ?: 0
        val added=response.optJSONObject("added")?.optInt(plural,0) ?: 0
        val deleted=response.optJSONObject("deleted")?.optInt(plural,0) ?: 0
        val existing=response.optJSONObject("existing")?.optInt(plural,0) ?: 0
        val updated=response.optJSONObject("updated")?.optInt(plural,0) ?: 0
        if(notFound>0) error("Trakt did not find this item")
        if(added+deleted+existing+updated>0) "Trakt change confirmed" else "No change confirmed; refresh to verify"
    }
    fun upcoming(context:Context,days:Int,done:(Result<List<UpcomingEpisode>>)->Unit)=task(context,done) { token,_ ->
        require(days in 1..31)
        val date=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val a=JSONArray(request(token,"/calendars/my/shows/"+date+"/"+days).text)
        (0 until a.length()).mapNotNull { i ->
            val o=a.optJSONObject(i) ?: return@mapNotNull null
            val ep=o.optJSONObject("episode") ?: return@mapNotNull null
            val show=o.optJSONObject("show") ?: return@mapNotNull null
            val airs=o.optString("first_aired")
            if(runCatching { Instant.parse(airs) }.isFailure) null else UpcomingEpisode(show.optString("title"),ep.optString("title"),ep.optInt("season"),ep.optInt("number"),airs)
        }.sortedBy { it.airs }
    }
    fun accountId(context:Context,done:(Result<String>)->Unit)=task(context,done) { _,account -> account }
    // Download a bounded page into an account-scoped local cache. Never convert
    // remote watches into sampled playback or approved upload records.
    fun cacheHistory(context:Context,page:RemotePage):Int {
        val prefs=context.getSharedPreferences("trakt_remote_history",Context.MODE_PRIVATE)
        val edit=prefs.edit()
        var count=0
        for(item in page.items) {
            if(item.eventId<=0 || item.watchedAt.isBlank() || runCatching { Instant.parse(item.watchedAt) }.isFailure) continue
            val key=RemoteRules.historyKey(page.account,item.eventId)
            edit.putString(key,JSONObject().put("type",item.type).put("id",item.id).put("title",item.title).put("show",item.showId).put("season",item.season).put("episode",item.episode).put("watched",item.watchedAt).put("event",item.eventId).toString()); count++
        }
        check(edit.commit())
        return count
    }
    fun cachedHistory(context:Context,account:String):List<RemoteItem> {
        val prefix=account+":"
        return context.getSharedPreferences("trakt_remote_history",Context.MODE_PRIVATE).all.filterKeys { it.startsWith(prefix) }.values.mapNotNull { value ->
            runCatching { val o=JSONObject(value as String); RemoteItem(o.getString("type"),o.getLong("id"),o.getString("title"),o.getLong("show"),o.getInt("season"),o.getInt("episode"),o.getString("watched"),o.getLong("event")) }.getOrNull()
        }.sortedByDescending { it.watchedAt }
    }
    fun clearCache(context:Context) { context.getSharedPreferences("trakt_remote_history",Context.MODE_PRIVATE).edit().clear().apply() }
}
