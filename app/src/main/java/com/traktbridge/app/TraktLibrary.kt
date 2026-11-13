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
    fun plural(type:String):String=when(type) { "movie"->"movies"; "show"->"shows"; "episode"->"episodes"; "season"->"seasons"; else->error("Unsupported type") }
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
        val type=o.optString("type").ifBlank { listOf("episode","season","movie","show").firstOrNull { o.has(it) }.orEmpty() }
        if(type !in setOf("movie","show","season","episode")) return null
        val item=o.optJSONObject(type) ?: return null
        val itemId=id(item); if(itemId<=0) return null
        val show=o.optJSONObject("show")
        val name=if(type=="episode") show?.optString("title").orEmpty()+" — "+item.optString("title")+" • S"+item.optInt("season")+" E"+item.optInt("number") else if(type=="season") show?.optString("title").orEmpty()+" • Season "+item.optInt("number") else item.optString("title")
        if(name.isBlank()) return null
        return RemoteItem(type,itemId,name,id(show),if(type=="season") item.optInt("number",-1) else item.optInt("season",-1),if(type=="episode") item.optInt("number",-1) else -1,o.optString("watched_at"),o.optLong("id",0),o.optInt("rating",0),o.optDouble("progress",-1.0))
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
    private val downloadLock=Any()
    fun syncAccount(context:Context,progress:(String,Int,Int)->Unit={_,_,_->},done:(Result<TraktAccountSummary>)->Unit) {
        val app=context.applicationContext
        worker.execute {
            val result=runCatching { downloadAccount(app,null,false) { stage,done,total -> main.post { progress(stage,done,total) } } }
            main.post { done(result) }
        }
    }
    fun downloadAccount(context:Context,expectedAccount:String?,automatic:Boolean,progress:(String,Int,Int)->Unit={_,_,_->}):TraktAccountSummary=synchronized(downloadLock) {
        val app=context.applicationContext
        val auth=TraktAuth(app)
        val prefs=app.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val (account,generation,activity)=auth.authorized { token ->
            val id=account(token)
            require(expectedAccount==null || expectedAccount==id) { "Connected account changed" }
            require(!automatic || prefs.getString("auto_refresh_account","")==id) { "Automatic refresh disabled" }
            val gen=RemoteAccountCache.activate(app,id)
            val a=runCatching { JSONObject(request(token,"/sync/last_activities").text) }.getOrDefault(JSONObject())
            Triple(id,gen,a)
        }
        fun valid()=RemoteAccountCache.current(app,account,generation) && (!automatic || prefs.getString("auto_refresh_account","")==account)
        val results=mutableListOf<TraktSyncResult>()
        for(kind in TraktData.entries) {
            if(!valid()) break
            progress("Downloading "+kind.title,0,0)
            val marker=kind.marker(activity)
            val prior=RemoteAccountCache.status(app,account,kind)
            if(automatic && prior!=null && TraktDownloadRules.canSkip(prior.marker,marker,prior.complete)) {
                results.add(TraktSyncResult(kind,prior.count,true,unchanged=true)); continue
            }
            val rows=linkedMapOf<String,RemoteItem>()
            var complete=false
            var skipped=0
            try {
                for(page in 1..TraktDownloadRules.MAX_PAGES) {
                    val response=auth.authorized { token ->
                        check(valid()) { "Account changed or refresh cancelled" }
                        request(token,kind.endpoint+"?page="+page+"&limit="+TraktDownloadRules.PAGE_SIZE)
                    }
                    val a=JSONArray(response.text)
                    progress("Downloading "+kind.title,page,response.pages ?: 0)
                    for(i in 0 until a.length()) {
                        val item=a.optJSONObject(i)?.let { parse(it) }
                        val key=item?.let { TraktDownloadRules.key(kind,it) }
                        if(item==null || key==null) skipped++ else rows[key]=item
                    }
                    if(TraktDownloadRules.lastPage(page,response.pages,a.length())) { complete=true; break }
                }
                check(valid()) { "Account changed or refresh cancelled" }
                complete=complete && skipped==0
                progress("Saving "+kind.title,0,0)
                val saved=RemoteAccountCache.publish(app,account,generation,kind,rows,complete,marker)
                val error=when {
                    !saved->"Previous complete snapshot retained or refresh cancelled"
                    skipped>0->"$skipped unsupported or invalid rows"
                    !complete->"Page safety limit reached; partial download"
                    else->""
                }
                results.add(TraktSyncResult(kind,if(saved) rows.size else prior?.count ?: 0,complete && saved,error))
            } catch(e:Exception) {
                val message=e.message.orEmpty()
                val reason=if(message.startsWith("Trakt HTTP ")) message else "Download interrupted; previous cache retained"
                results.add(TraktSyncResult(kind,prior?.count ?: 0,false,reason))
                if(message in setOf("Trakt HTTP 401","Trakt HTTP 403","Trakt HTTP 429") || !valid()) break
            }
        }
        progress("Download finished",results.size,TraktData.entries.size)
        TraktAccountSummary(results)
    }
    fun refreshHistory(context:Context,maxPages:Int,done:(Result<Int>)->Unit)=task(context,done) { token,account ->
        require(maxPages in 1..20)
        var saved=0
        for(page in 1..maxPages) {
            val response=request(token,"/sync/history?page="+page+"&limit=100")
            val a=JSONArray(response.text)
            val rows=(0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) }
            saved+=cacheHistory(context,RemotePage(rows,page,response.pages,account))
            if(a.length()<100 || (response.pages!=null && page>=response.pages)) break
        }
        saved
    }
    fun refreshRecentForAccount(context:Context,expectedAccount:String):Int=TraktAuth(context).authorized { token ->
        val current=account(token)
        require(current==expectedAccount) { "Account changed" }
        val response=request(token,"/sync/history?page=1&limit=100")
        val a=JSONArray(response.text)
        val rows=(0 until a.length()).mapNotNull { parse(a.getJSONObject(it)) }
        if(context.getSharedPreferences("tracker",Context.MODE_PRIVATE).getString("auto_refresh_account","")!=current) 0
        else cacheHistory(context,RemotePage(rows,1,response.pages,current))
    }
    fun accountId(context:Context,done:(Result<String>)->Unit)=task(context,done) { _,account -> account }
    // Download a bounded page into an account-scoped local cache. Never convert
    // remote watches into sampled playback or approved upload records.
    fun cacheHistory(context:Context,page:RemotePage):Int {
        val prefs=context.getSharedPreferences("trakt_remote_history_encrypted",Context.MODE_PRIVATE)
        val edit=prefs.edit()
        var count=0
        for(item in page.items) {
            if(item.eventId<=0 || item.watchedAt.isBlank() || runCatching { Instant.parse(item.watchedAt) }.isFailure) continue
            val key=AccountScope.cacheKey(page.account,item.eventId)
            edit.putString(key,PrivateHistoryCache.seal(JSONObject().put("type",item.type).put("id",item.id).put("title",item.title).put("show",item.showId).put("season",item.season).put("episode",item.episode).put("watched",item.watchedAt).put("event",item.eventId).toString(),key)); count++
        }
        check(edit.commit())
        return count
    }
    private data class HistoryMemory(val account:String,val generation:Long,val updated:Long,val rows:List<RemoteItem>)
    @Volatile private var historyMemory:HistoryMemory?=null
    fun cachedHistory(context:Context,account:String,progress:(Int,Int)->Unit={_,_->}):List<RemoteItem> {
        val generation=RemoteAccountCache.generation(context)
        val status=RemoteAccountCache.status(context,account,TraktData.HISTORY)
        if(RemoteAccountCache.active(context)!=account) return emptyList()
        historyMemory?.takeIf { it.account==account && it.generation==generation && status!=null && status.complete && it.updated==status.updated }?.let {
            progress(it.rows.size,it.rows.size); return it.rows
        }
        val snapshot=RemoteAccountCache.rows(context,account,TraktData.HISTORY,progress)
        val legacy=if(status?.complete==true) emptyList() else {
            val prefix=AccountScope.key(account)+":"
            val entries=context.getSharedPreferences("trakt_remote_history_encrypted",Context.MODE_PRIVATE).all.filterKeys { it.startsWith(prefix) }.entries.toList()
            val total=snapshot.size+entries.size
            progress(snapshot.size,total)
            entries.mapIndexed { index,(key,value) ->
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                val o=JSONObject(PrivateHistoryCache.open(value as String,key))
                val row=RemoteItem(o.getString("type"),o.getLong("id"),o.getString("title"),o.getLong("show"),o.getInt("season"),o.getInt("episode"),o.getString("watched"),o.getLong("event"))
                if((index+1)%50==0 || index+1==entries.size) progress(snapshot.size+index+1,total)
                row
            }
        }
        check(RemoteAccountCache.current(context,account,generation)) { "Account changed during history loading" }
        val rows=(snapshot+legacy).distinctBy { it.eventId }.sortedByDescending { runCatching { Instant.parse(it.watchedAt) }.getOrDefault(Instant.EPOCH) }
        if(status?.complete==true && RemoteAccountCache.status(context,account,TraktData.HISTORY)?.updated==status.updated) historyMemory=HistoryMemory(account,generation,status.updated,rows)
        return rows
    }
    fun clearCache(context:Context) { historyMemory=null; RemoteAccountCache.clear(context); context.getSharedPreferences("trakt_remote_history_encrypted",Context.MODE_PRIVATE).edit().clear().apply(); context.getSharedPreferences("trakt_remote_history",Context.MODE_PRIVATE).edit().clear().apply() }
}
