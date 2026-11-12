package com.traktbridge.app

import android.content.Context
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

object HistorySync {
    fun schedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        manager.enqueueUniqueWork("trakt-history-now", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<HistorySyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build())
        manager.enqueueUniquePeriodicWork("trakt-history-recovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HistorySyncWorker>(15,TimeUnit.MINUTES).setConstraints(constraints).build())
    }
    private fun encode(s: String) = URLEncoder.encode(s,"UTF-8")
    private fun utc(ms: Long) = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS)
    private data class Response(val text: String,val pages: Int?)
    private class ApiError(val status: Int): Exception("Trakt HTTP " + status)
    private fun call(token: String,path: String,body: JSONObject? = null): Response {
        val c = URL("https://api.trakt.tv"+path).openConnection() as HttpsURLConnection
        try {
            c.instanceFollowRedirects=false; c.connectTimeout=15000; c.readTimeout=15000
            c.setRequestProperty("Authorization","Bearer "+token)
            c.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID)
            c.setRequestProperty("trakt-api-version","2")
            c.setRequestProperty("Accept","application/json")
            if(body != null) {
                c.requestMethod="POST"; c.doOutput=true
                c.setRequestProperty("Content-Type","application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status=c.responseCode
            DebugTrace.event("sync","HTTP "+status+" method="+c.requestMethod)
            if(status !in 200..299) throw ApiError(status)
            return Response(c.inputStream.bufferedReader().use { reader ->
                val out=StringBuilder(); val buffer=CharArray(8192)
                while(true) { val n=reader.read(buffer); if(n<0) break; require(out.length+n<=8_000_000) { "Trakt response too large" }; out.append(buffer,0,n) }
                out.toString()
            },c.getHeaderField("X-Pagination-Page-Count")?.toIntOrNull())
        } finally { c.disconnect() }
    }
    private fun account(token: String): String {
        val ids=JSONObject(call(token,"/users/settings").text).getJSONObject("user").getJSONObject("ids")
        return ids.optString("uuid").takeIf { it.isNotBlank() } ?: ids.optString("slug").takeIf { it.isNotBlank() } ?: error("No stable Trakt account identifier")
    }
    fun accountForApproval(context: Context): String = TraktAuth(context).authorized { account(it) }
    private enum class Reconciliation { ABSENT, EXACT, CONFLICT }
    private fun exists(token: String,r: WatchRecord): Reconciliation {
        val target=utc(r.ended)
        val plural=if(r.matchType=="movie") "movies" else "episodes"
        val start=encode(target.minusSeconds(2).toString()); val end=encode(target.plusSeconds(2).toString())
        for(page in 1..100) {
            val response=call(token,"/sync/history/"+plural+"/"+r.traktId+"?start_at="+start+"&end_at="+end+"&page="+page+"&limit=100")
            val rows=JSONArray(response.text)
            var malformed=false
            for(i in 0 until rows.length()) {
                val o=rows.getJSONObject(i)
                val id=o.optJSONObject(r.matchType)?.optJSONObject("ids")?.optLong("trakt") ?: 0
                val watched=runCatching { Instant.parse(o.getString("watched_at")).truncatedTo(ChronoUnit.SECONDS) }.getOrNull()
                if(id==r.traktId && watched==target) return Reconciliation.EXACT
                if(id<=0 || watched==null) malformed=true
            }
            if(malformed) return Reconciliation.CONFLICT
            if(rows.length()==0 || (response.pages!=null && page>=response.pages)) return Reconciliation.ABSENT
        }
        error("History reconciliation exceeded page limit")
    }
    // All network operations serialize with OAuth refresh and account changes.
    internal fun process(context: Context,db: WatchHistory,r: WatchRecord): Boolean {
        val approved=db.approvedAccount(r.id)
        val fresh=db.record(r.id) ?: return true
        if(approved.isBlank() || fresh.status!=r.status || fresh.matchType!=r.matchType || fresh.traktId!=r.traktId || fresh.ended!=r.ended) return true
        var expected=r.status
        fun state(status: String,message: String) = db.updateApprovedState(r,approved,expected,status,message)
        return try {
            TraktAuth(context).authorized { token ->
                if(account(token)!=approved) {
                    // Never downgrade an ambiguous POST to a resendable state.
                    state(if(expected=="uncertain") "uncertain" else "blocked","Different Trakt account. Reconnect original account.")
                    return@authorized true
                }
                when(exists(token,r)) {
                    Reconciliation.EXACT -> { state("synced","Already present in Trakt history"); return@authorized true }
                    Reconciliation.CONFLICT -> { state(expected,"Remote history response could not be verified; no upload sent"); return@authorized false }
                    Reconciliation.ABSENT -> Unit
                }
                if(expected=="uncertain") {
                    state(SyncRecoveryPolicy.afterLookup(expected,false,true),"Previous upload outcome unknown. No duplicate POST sent. Check Trakt and reconcile again.")
                    return@authorized true
                }
                if(!db.beginApprovedUpload(r,approved)) return@authorized true
                expected="uncertain"
                val plural=if(r.matchType=="movie") "movies" else "episodes"
                val item=JSONObject().put("ids",JSONObject().put("trakt",r.traktId)).put("watched_at",utc(r.ended).toString())
                try {
                    val result=JSONObject(call(token,"/sync/history",JSONObject().put(plural,JSONArray().put(item))).text)
                    if(result.optJSONObject("added")?.optInt(plural,0)==1) state("synced","Added to Trakt history")
                    else state("uncertain","Upload response not confirmed; reconcile with Trakt.")
                } catch(e: ApiError) {
                    if(e.status in listOf(400,401,403,404,422,429)) {
                        state(if(e.status==429) "queued" else "blocked",e.message ?: "Request rejected")
                        return@authorized e.status!=429
                    }
                    state("uncertain","Upload outcome unknown; checking history on retry.")
                    return@authorized false
                } catch(_: Exception) {
                    state("uncertain","Upload outcome unknown; checking history on retry.")
                    return@authorized false
                }
                true
            }
        } catch(e: ApiError) {
            if(e.status==401 || e.status==403) {
                state(if(expected=="queued") "blocked" else "uncertain","Reconnect Trakt to continue synchronization")
                true
            } else false
        } catch(_: Exception) {
            // Never restore the pre-POST queued snapshot after a possible write.
            state(expected,"Network or authorization unavailable; retry pending")
            false
        }
    }
}
class HistorySyncWorker(context: Context, params: WorkerParameters): Worker(context,params) {
    companion object { private val lock=Any() }
    override fun doWork(): Result = synchronized(lock) {
        DebugTrace.init(applicationContext)
        DebugTrace.event("sync","worker started")
        WatchHistory(applicationContext).use { db ->
            var retry=false
            val uncertain=db.syncQueue(20,true)
            val queue=(uncertain+db.syncQueue(80)).distinctBy { it.id }
            for(r in queue) {
                if(isStopped) return@synchronized Result.retry()
                if(!HistorySync.process(applicationContext,db,r)) { retry=true; if(r.status=="queued") break }
            }
            if(retry || db.syncQueue(1).any { it.status=="queued" }) Result.retry() else Result.success()
        }
    }
}
