package com.traktbridge.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent, bounded suggestions; network results never alter history. Calls are on the main thread. */
object AutoMatchSuggestions {
    data class State(val status:String,val matches:List<RankedMatch> = emptyList())
    private val running=mutableSetOf<String>()
    private fun store(context:Context)=context.getSharedPreferences("catalogue_suggestions",Context.MODE_PRIVATE)
    private fun key(r:WatchRecord)=r.id
    private fun fingerprint(r:WatchRecord)=r.source+"|"+r.title+"|"+r.subtitle+"|"+r.duration
    fun eligible(r:WatchRecord)=r.status=="pending" && r.traktId<=0 && r.title.isNotBlank() && r.source==YouTubePlayback.PACKAGE
    fun read(context:Context,r:WatchRecord):State {
        if(!eligible(r)) return State("unavailable")
        val k=key(r)
        if(k in running) return State("searching")
        val o=runCatching { JSONObject(store(context).getString(k,"").orEmpty()) }.getOrNull() ?: return State("waiting")
        if(o.optString("fingerprint")!=fingerprint(r)) return State("waiting")
        val state=o.optString("status")
        if(state=="error" && System.currentTimeMillis()-o.optLong("at")>=120000) return State("waiting")
        if(state=="none" && System.currentTimeMillis()-o.optLong("at")>=7*86400000L) return State("waiting")
        val array=o.optJSONArray("matches") ?: JSONArray()
        val matches=(0 until array.length()).mapNotNull { i ->
            val row=array.optJSONObject(i) ?: return@mapNotNull null
            val item=CatalogueItem(row.optString("type"),row.optLong("id"),row.optString("title"),row.optInt("year"),row.optLong("showId"),row.optInt("season",-1),row.optInt("episode",-1),row.optInt("runtime"))
            if(item.id<=0 || item.type !in setOf("movie","show","episode")) null else RankedMatch(item,row.optInt("score"),row.optString("reason"))
        }
        return State(state,matches)
    }
    private fun write(context:Context,r:WatchRecord,status:String,matches:List<RankedMatch> = emptyList()) {
        val prefs=store(context)
        val array=JSONArray()
        matches.take(3).forEach { m -> array.put(JSONObject().apply {
            val i=m.item
            put("type",i.type); put("id",i.id); put("title",i.title); put("year",i.year); put("showId",i.showId); put("season",i.season); put("episode",i.episode); put("runtime",i.runtimeMinutes); put("score",m.score); put("reason",m.reason)
        }) }
        val edit=prefs.edit()
        if(prefs.all.size>=500) prefs.all.entries.sortedBy { runCatching { JSONObject(it.value.toString()).optLong("at") }.getOrDefault(0L) }.take(50).forEach { edit.remove(it.key) }
        edit.putString(key(r),JSONObject().put("fingerprint",fingerprint(r)).put("at",System.currentTimeMillis()).put("status",status).put("matches",array).toString()).apply()
    }
    fun reject(context:Context,r:WatchRecord) { write(context,r,"rejected") }
    fun remove(context:Context,id:String) { store(context).edit().remove(id).apply() }
    fun enqueue(context:Context,records:List<WatchRecord>,onChanged:()->Unit={}) {
        if(running.isNotEmpty()) return
        val app=context.applicationContext
        val record=records.firstOrNull { eligible(it) && read(app,it).status=="waiting" } ?: return
        val k=key(record)
        running.add(k)
        TraktCatalogue.suggest(record) { result ->
            running.remove(k)
            val current=runCatching { WatchHistory(app).use { it.record(k) } }.getOrNull()
            if(current!=null && eligible(current) && fingerprint(current)==fingerprint(record) && read(app,current).status!="rejected") {
                result.fold({ matches -> write(app,current,if(matches.isEmpty()) "none" else "ready",matches) },{ write(app,current,"error") })
            }
            onChanged()
        }
    }
}
