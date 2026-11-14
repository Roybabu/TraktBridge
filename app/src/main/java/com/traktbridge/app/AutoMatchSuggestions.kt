package com.traktbridge.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent, bounded suggestions; network results never alter history. Calls are on the main thread. */
object AutoMatchSuggestions {
    data class State(val status:String,val matches:List<RankedMatch> = emptyList())
    private val running=mutableSetOf<String>()
    fun store(context:Context)=context.getSharedPreferences("catalogue_suggestions",Context.MODE_PRIVATE)
    private fun key(r:WatchRecord)=r.id
    private fun fingerprint(r:WatchRecord)=r.source+"|"+r.title+"|"+r.subtitle+"|"+r.duration
    fun eligible(r:WatchRecord)=r.status=="pending" && r.traktId<=0 && r.title.isNotBlank() && r.source==YouTubePlayback.PACKAGE
    fun read(context:Context,r:WatchRecord):State {
        if(!eligible(r)) return State("unavailable")
        val k=key(r)
        if(k in running) return State("searching")
        val o=runCatching { JSONObject(store(context).getString(k,"").orEmpty()) }.getOrNull() ?: return State("waiting")
        if(o.optString("fingerprint")!=fingerprint(r)) return State("waiting")
        if(o.optString("status")!="rejected" && o.optInt("policyVersion")!=3) return State("waiting")
        val state=o.optString("status")
        if(state=="ready" && (o.optJSONArray("matches")?.length() ?: 0)==0) return State("waiting")
        if(state=="error" && System.currentTimeMillis()-o.optLong("at")>=120000) return State("waiting")
        if(state=="none" && System.currentTimeMillis()-o.optLong("at")>=7*86400000L) return State("waiting")
        val array=o.optJSONArray("matches") ?: JSONArray()
        val matches=(0 until array.length()).mapNotNull { i ->
            val row=array.optJSONObject(i) ?: return@mapNotNull null
            val item=CatalogueItem(row.optString("type"),row.optLong("id"),row.optString("title"),row.optInt("year"),row.optLong("showId"),row.optInt("season",-1),row.optInt("episode",-1),row.optInt("runtime"))
            if(item.id<=0 || item.type !in setOf("movie","show","episode")) null else RankedMatch(item,row.optInt("score"),row.optString("reason"))
        }
        return if(state=="ready" && matches.isEmpty()) State("waiting") else State(state,matches)
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
        edit.putString(key(r),JSONObject().put("policyVersion",3).put("fingerprint",fingerprint(r)).put("at",System.currentTimeMillis()).put("status",status).put("matches",array).toString()).apply()
    }
    private fun feedback(context:Context)=context.getSharedPreferences("catalogue_feedback",Context.MODE_PRIVATE)
    private fun identity(r:WatchRecord):String = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest((r.source+"|"+RecognitionEngine.normalized(r.title)+"|"+RecognitionEngine.normalized(r.subtitle)).toByteArray(Charsets.UTF_8)),android.util.Base64.NO_WRAP)
    private fun candidateKey(item:CatalogueItem)=item.type+":"+item.id
    private fun rejected(context:Context,r:WatchRecord):Set<String> = feedback(context).getStringSet("reject:"+identity(r),emptySet()).orEmpty()
    fun reject(context:Context,r:WatchRecord) {
        val previous=read(context,r).matches
        val best=previous.firstOrNull()?.item
        if(best!=null) {
            val p=feedback(context); val k="reject:"+identity(r)
            p.edit().putStringSet(k,rejected(context,r)+candidateKey(best)).apply()
        }
        val remaining=previous.drop(1)
        write(context,r,if(remaining.isEmpty()) "rejected" else "ready",remaining)
    }
    fun confirmed(context:Context,r:WatchRecord,item:CatalogueItem) {
        val p=feedback(context); val k="reject:"+identity(r)
        p.edit().putStringSet(k,rejected(context,r)-candidateKey(item)).apply()
    }
    private fun confirmedSeries(record:WatchRecord,records:List<WatchRecord>):List<CatalogueItem> {
        val q=AutoMatchPolicy.query(record.title,record.subtitle) ?: return emptyList()
        if(q.episode==null) return emptyList()
        return records.filter { it.source==record.source && it.matchType=="episode" && it.showId>0 && it.traktId>0 && it.status in setOf("reviewed","queued","synced") }
            .mapNotNull { prior ->
                val priorQuery=AutoMatchPolicy.query(prior.title,prior.subtitle) ?: return@mapNotNull null
                if(AutoMatchPolicy.similarity(q.title,priorQuery.title)!=1.0) return@mapNotNull null
                val showTitle=prior.matchLabel.substringBefore(" — ").trim()
                if(showTitle.isBlank() || AutoMatchPolicy.similarity(q.title,showTitle)<0.80) return@mapNotNull null
                CatalogueItem("show",prior.showId,showTitle)
            }.distinctBy { it.id }.take(3)
    }
    fun remove(context:Context,id:String) { store(context).edit().remove(id).apply() }
    fun enqueue(context:Context,records:List<WatchRecord>) {
        if(running.isNotEmpty()) return
        val app=context.applicationContext
        val record=records.firstOrNull { eligible(it) && read(app,it).status=="waiting" } ?: return
        val learned=runCatching { WatchHistory(app).use { it.learned(record) } }.getOrNull()
        if(learned!=null && candidateKey(learned) !in rejected(app,record)) {
            runCatching { WatchHistory(app).use { it.match(record.id,learned,false) } }; return
        }
        val k=key(record)
        running.add(k)
        val confirmed=confirmedSeries(record,records)
        TraktCatalogue.suggest(record,confirmed) { result ->
            running.remove(k)
            val current=runCatching { WatchHistory(app).use { it.record(k) } }.getOrNull()
            if(current!=null && eligible(current) && fingerprint(current)==fingerprint(record) && read(app,current).status!="rejected") {
                result.fold({ matches ->
                    val denied=rejected(app,current)
                    val accepted=matches.filter { candidateKey(it.item) !in denied }
                    write(app,current,if(accepted.isEmpty()) "none" else "ready",accepted)
                },{ write(app,current,"error") })
            }
        }
    }
}
