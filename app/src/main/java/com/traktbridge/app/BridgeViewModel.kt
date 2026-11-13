package com.traktbridge.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.Executors

data class HistorySnapshot(val records:List<WatchRecord>,val groups:List<ViewingTitle>,val unified:List<WatchRecord> = records)

/** Read-only application repository. Approval and tracking writes stay in their existing owners. */
class ViewingRepository(private val app:Application) {
    fun snapshot():HistorySnapshot {
        val db=WatchHistory(app)
        try {
            val rows=db.records(0)
            val account=RemoteAccountCache.active(app)
            val unified=if(account==null) rows else {
                val remote=TraktLibrary.cachedHistory(app,account)
                val approved=rows.filter { it.status=="synced" && db.approvedAccount(it.id)==account }.map { it.id }.toSet()
                if(RemoteAccountCache.active(app)==account) UnifiedTraktHistory.merge(rows,remote,approved) else rows
            }
            return HistorySnapshot(rows,ViewingGroups.build(rows),unified)
        } finally { db.close() }
    }
}

class BridgeViewModel(app:Application):AndroidViewModel(app) {
    private val repository=ViewingRepository(app)
    private val worker=java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.LinkedBlockingQueue<Runnable>())
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var task:java.util.concurrent.Future<*>?=null
    private var generation=0L
    var selectedTab by mutableStateOf(0)
        private set
    fun select(index:Int) { if(index in 0..4) selectedTab=index }
    fun load(deliver:(Result<HistorySnapshot>)->Unit) {
        val request=++generation
        task?.cancel(true)
        worker.purge()
        task=worker.submit {
            val start=android.os.SystemClock.elapsedRealtime()
            val result=runCatching { repository.snapshot() }
            StartupTiming.report(start,"history-read")
            if(!Thread.currentThread().isInterrupted) main.post { if(request==generation) deliver(result) }
        }
    }
    fun cancel() { generation++; task?.cancel(true); task=null }
    override fun onCleared() { cancel(); worker.shutdownNow(); super.onCleared() }
}
