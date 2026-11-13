package com.traktbridge.app

import android.content.Context
import org.json.JSONObject
import java.time.Instant

/** Encrypted, account-scoped snapshots. Only fully fetched categories replace a
 * previous complete snapshot. Never writes to WatchHistory or the upload queue. */
object RemoteAccountCache {
    private const val META="trakt_account_cache_meta"
    private const val ACTIVE="active_account"
    private const val GENERATION="generation"
    data class Status(val count:Int,val complete:Boolean,val updated:Long,val marker:String)
    private fun meta(c:Context)=c.getSharedPreferences(META,Context.MODE_PRIVATE)
    private fun store(c:Context,k:TraktData)=c.getSharedPreferences("trakt_account_cache_"+k.name.lowercase(java.util.Locale.ROOT),Context.MODE_PRIVATE)
    @Synchronized fun active(c:Context):String? {
        if(!TraktAuth(c).hasConnection()) return null
        return meta(c).getString(ACTIVE,null)?.let { runCatching { PrivateHistoryCache.open(it,ACTIVE) }.getOrNull() }
    }
    @Synchronized fun activate(c:Context,account:String):Long {
        require(account.isNotBlank())
        if(active(c)!=account) {
            clear(c)
            check(meta(c).edit().putString(ACTIVE,PrivateHistoryCache.seal(account,ACTIVE)).commit())
        }
        return meta(c).getLong(GENERATION,0)
    }
    fun generation(c:Context):Long=meta(c).getLong(GENERATION,0)
    @Synchronized fun current(c:Context,account:String,generation:Long)=active(c)==account && meta(c).getLong(GENERATION,0)==generation
    @Synchronized fun clear(c:Context) {
        PersistentHistorySnapshot.clear(c)
        val next=meta(c).getLong(GENERATION,0)+1
        check(meta(c).edit().clear().putLong(GENERATION,next).commit())
        for(k in TraktData.entries) check(store(c,k).edit().clear().commit())
    }
    @Synchronized fun status(c:Context,account:String,kind:TraktData):Status? {
        if(active(c)!=account) return null
        val p=store(c,kind)
        if(!p.contains("updated")) return null
        return Status(p.getInt("count",0),p.getBoolean("complete",false),p.getLong("updated",0),p.getString("marker","").orEmpty())
    }
    internal fun encode(row:RemoteItem)=JSONObject().put("type",row.type).put("id",row.id).put("title",row.title.take(1000)).put("show",row.showId).put("season",row.season).put("episode",row.episode).put("watched",row.watchedAt).put("event",row.eventId).put("rating",row.rating).put("progress",row.progress).toString()
    internal fun decode(s:String):RemoteItem {
        val o=JSONObject(s)
        return RemoteItem(o.getString("type"),o.getLong("id"),o.getString("title"),o.getLong("show"),o.getInt("season"),o.getInt("episode"),o.getString("watched"),o.getLong("event"),o.getInt("rating"),o.getDouble("progress"))
    }
    private fun scope(account:String,kind:TraktData,key:String)=AccountScope.key(account)+":"+kind.name+":"+key
    fun publish(c:Context,account:String,generation:Long,kind:TraktData,rows:Map<String,RemoteItem>,complete:Boolean,marker:String):Boolean {
        if(!current(c,account,generation)) return false
        val previous=status(c,account,kind)
        // Do not replace a valid full snapshot with a truncated download.
        if(!complete && previous!=null && previous.complete) return false
        val encrypted=rows.mapValues { (key,row)->PrivateHistoryCache.seal(encode(row),scope(account,kind,key)) }
        return synchronized(this) {
        if(!current(c,account,generation)) return@synchronized false
        val p=store(c,kind)
        val edit=p.edit().clear()
        for((key,value) in encrypted) edit.putString("row:"+key,value)
        edit.putInt("count",rows.size).putBoolean("complete",complete).putLong("updated",System.currentTimeMillis()).putString("marker",if(complete) marker else "")
        edit.commit()
        }
    }
    fun rows(c:Context,account:String,kind:TraktData,progress:(Int,Int)->Unit={_,_->}):List<RemoteItem> {
        if(active(c)!=account) return emptyList()
        val entries=store(c,kind).all.entries.filter { it.key.startsWith("row:") }
        val total=entries.size
        progress(0,total)
        val list=entries.mapIndexedNotNull { index,(key,value) ->
            if(Thread.currentThread().isInterrupted) throw InterruptedException()
            val row=decode(PrivateHistoryCache.open(value as String,scope(account,kind,key.removePrefix("row:"))))
            if((index+1)%50==0 || index+1==total) progress(index+1,total)
            row
        }
        if(active(c)!=account) return emptyList()
        return if(kind==TraktData.HISTORY) list.sortedByDescending { runCatching { Instant.parse(it.watchedAt) }.getOrDefault(Instant.EPOCH) }
        else list.sortedBy { it.title.lowercase(java.util.Locale.ROOT) }
    }
}
