package com.traktbridge.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

data class HistorySnapshot(val records:List<WatchRecord>,val groups:List<ViewingTitle>,val unified:List<WatchRecord> = records)
data class HistoryLoadProgress(val stage:String,val completed:Int=0,val total:Int=0)

/** Local database and remote history are separate stages. No network on screen load. */
class ViewingRepository(private val app:Application) {
    fun local():HistorySnapshot {
        val db=WatchHistory(app)
        try { val rows=db.records(0); return HistorySnapshot(rows,ViewingGroups.build(rows)) } finally { db.close() }
    }
    fun unified(local:HistorySnapshot,progress:(Int,Int)->Unit):HistorySnapshot {
        val account=RemoteAccountCache.active(app) ?: return local
        val generation=RemoteAccountCache.generation(app)
        val remote=TraktLibrary.cachedHistory(app,account,progress)
        val db=WatchHistory(app)
        val approved=try { local.records.filter { it.status=="synced" && db.approvedAccount(it.id)==account }.map { it.id }.toSet() } finally { db.close() }
        if(!RemoteAccountCache.current(app,account,generation)) return local
        return local.copy(unified=UnifiedTraktHistory.merge(local.records,remote,approved))
    }
    fun snapshot():HistorySnapshot=unified(local()){_,_->}
}

class BridgeViewModel(app:Application):AndroidViewModel(app) {
    private val repository=ViewingRepository(app)
    private val worker=Executors.newFixedThreadPool(2)
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var task:java.util.concurrent.Future<*>?=null
    private var generation=0L
    private var running=false
    private var latest:HistorySnapshot?=null
    private var observer:((Result<HistorySnapshot>,Boolean)->Unit)?=null
    private var progressObserver:((HistoryLoadProgress)->Unit)?=null
    private var lastProgress=HistoryLoadProgress("Reading local history")
    private var timeout:Runnable?=null
    var selectedTab by mutableStateOf(0)
        private set
    fun select(index:Int) { if(index in 0..4) selectedTab=index }
    fun load(deliver:(Result<HistorySnapshot>,Boolean)->Unit,onProgress:(HistoryLoadProgress)->Unit) {
        observer=deliver; progressObserver=onProgress
        if(running) {
            latest?.let { deliver(Result.success(it),false) }
            onProgress(lastProgress); return
        }
        val request=++generation
        running=true
        lastProgress=HistoryLoadProgress("Reading local history")
        onProgress(lastProgress)
        fun postProgress(value:HistoryLoadProgress) { main.post { if(request==generation && running) { lastProgress=value; progressObserver?.invoke(value); armTimeout(request) } } }
        armTimeout(request)
        task=worker.submit {
            val result=runCatching {
                val local=repository.local()
                main.post { if(request==generation && running) { latest=local; observer?.invoke(Result.success(local),false) } }
                postProgress(HistoryLoadProgress("Loading downloaded Trakt history"))
                val result=repository.unified(local){ done,total -> postProgress(HistoryLoadProgress("Loading downloaded Trakt history",done,total)) }
                postProgress(HistoryLoadProgress("Preparing viewing library"))
                result
            }
            main.post {
                if(request==generation && running) {
                    running=false; timeout?.let { main.removeCallbacks(it) }; timeout=null
                    result.getOrNull()?.let { latest=it }
                    observer?.invoke(result,true)
                }
            }
        }
    }
    private fun armTimeout(request:Long) {
        timeout?.let { main.removeCallbacks(it) }
        val action=Runnable {
            if(request==generation && running) {
                generation++; running=false; task?.cancel(true)
                observer?.invoke(Result.failure(IllegalStateException("No loading progress for 45 seconds")),true)
            }
        }
        timeout=action; main.postDelayed(action,45000)
    }
    fun cancel() { generation++; running=false; task?.cancel(true); task=null; timeout?.let { main.removeCallbacks(it) }; timeout=null; observer=null; progressObserver=null; latest=null }
    override fun onCleared() { cancel(); worker.shutdownNow(); super.onCleared() }
}
