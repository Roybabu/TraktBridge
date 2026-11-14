package com.traktbridge.app

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Never upload a rewatch unless the user opted in, identity is confirmed,
 * prior completion belongs to the current account and measured evidence is strong. */
object RewatchAutomation {
    private val worker=Executors.newSingleThreadExecutor()
    private val running=AtomicBoolean(false)
    @Volatile private var last=0L
    private data class Index(val account:String,val generation:Long,val updated:Long,val completed:Map<String,Long>)
    @Volatile private var index:Index?=null
    private fun key(r:WatchRecord)=r.matchType+":"+r.traktId
    fun isKnownRewatch(c:Context,r:WatchRecord):Boolean {
        val current=index ?: return false
        val account=RemoteAccountCache.active(c) ?: return false
        if(current.account!=account || current.generation!=RemoteAccountCache.generation(c) || current.updated!=RemoteAccountCache.status(c,account,TraktData.HISTORY)?.updated) return false
        return (current.completed[key(r)] ?: Long.MAX_VALUE)<r.started
    }
    fun request(context:Context) {
        val now=SystemClock.elapsedRealtime()
        if(now-last<15000 || !running.compareAndSet(false,true)) return
        last=now
        val app=context.applicationContext
        worker.execute {
            try {
                val prefs=app.getSharedPreferences("tracker",Context.MODE_PRIVATE)
                if(!prefs.getBoolean("enabled",false) || prefs.getBoolean("privacy_lock",false)) return@execute
                val account=RemoteAccountCache.active(app) ?: return@execute
                val generation=RemoteAccountCache.generation(app)
                val status=RemoteAccountCache.status(app,account,TraktData.HISTORY) ?: return@execute
                val old=index
                val current=if(old!=null && old.account==account && old.generation==generation && old.updated==status.updated) old else {
                    val completed=TraktLibrary.cachedHistory(app,account).mapNotNull { row ->
                        runCatching { (row.type+":"+row.id) to java.time.Instant.parse(row.watchedAt).toEpochMilli() }.getOrNull()
                    }.groupBy({it.first},{it.second}).mapValues { it.value.minOrNull() ?: Long.MAX_VALUE }
                    Index(account,generation,status.updated,completed).also { index=it }
                }
                WatchHistory(app).use { db ->
                    db.applyLearned()
                    if(!prefs.getBoolean("auto_rewatch_sync",false) && !prefs.getBoolean("auto_watch_sync",false)) return@use
                    var queued=false
                    val attempts=app.getSharedPreferences("automatic_completion_attempts",Context.MODE_PRIVATE)
                    for(r in db.records(200)) {
                        if(!RemoteAccountCache.current(app,account,generation) || !TraktAuth(app).hasConnection() || prefs.getBoolean("privacy_lock",false) || !prefs.getBoolean("enabled",false)) break
                        val prior=current.completed[key(r)]
                        val threshold=prefs.getInt("completion_threshold",90)
                        val rewatch=prior!=null
                        val eligible=if(rewatch) prefs.getBoolean("auto_rewatch_sync",false) && RewatchPolicy.eligible(r,prior ?: 0L,threshold) else
                            prefs.getBoolean("auto_watch_sync",false) && status.complete && AutomaticCompletionPolicy.eligible(r,prefs.getLong("auto_watch_since",0),threshold)
                        if(!eligible) continue
                        // An attempted automatic approval is never repeated after manual cancellation.
                        if(attempts.contains(r.id)) continue
                        if(!attempts.edit().putBoolean(r.id,true).commit()) continue
                        if(db.enqueue(r.id,account,rewatch,r.traktId,r.matchType,r.ended)) {
                            ViewingNotifications.cancel(app,r.id); queued=true
                            DebugTrace.event("completion",if(rewatch) "completed rewatch queued" else "completed watch queued automatically")
                        }
                    }
                    if(queued) HistorySync.schedule(app)
                }
            } catch(e:Exception) { DebugTrace.failure("rewatch",e) } finally { running.set(false) }
        }
    }
}
object RewatchPolicy {
    fun eligible(r:WatchRecord,prior:Long,threshold:Int):Boolean =
        prior>0 && prior<r.started && r.status=="reviewed" && r.source==YouTubePlayback.PACKAGE &&
        r.positionSource=="media" && CompletionPolicy.assess(r,threshold).suggested &&
        r.duration>0 && r.watched.toDouble()/r.duration>=0.90 && r.uncertainMs<=r.duration/10
}

/** First watches require an exact verified identity and strong measured playback,
 * not merely a catalogue suggestion or a seek to the end. */
object AutomaticCompletionPolicy {
    fun eligible(r:WatchRecord,since:Long,threshold:Int):Boolean =
        since>0 && r.started>=since && r.ended>=r.started && r.status=="reviewed" &&
        r.source in PlaybackDetectors.supported && r.positionSource=="media" &&
        r.duration>0 && r.watched.toDouble()/r.duration>=0.90 &&
        r.uncertainMs<=r.duration/10 && CompletionPolicy.assess(r,maxOf(90,threshold)).suggested
}
