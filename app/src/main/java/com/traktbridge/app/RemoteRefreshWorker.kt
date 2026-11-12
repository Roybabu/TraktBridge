package com.traktbridge.app
import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
object RemoteRefreshSchedule {
    fun enable(context:Context,account:String) {
        require(account.isNotBlank())
        context.getSharedPreferences("tracker",Context.MODE_PRIVATE).edit().putString("auto_refresh_account",account).apply()
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("remote-history-refresh",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<RemoteRefreshWorker>(24,TimeUnit.HOURS).setConstraints(constraints).build())
    }
    fun disable(context:Context) {
        context.getSharedPreferences("tracker",Context.MODE_PRIVATE).edit().remove("auto_refresh_account").apply()
        WorkManager.getInstance(context).cancelUniqueWork("remote-history-refresh")
    }
}
class RemoteRefreshWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val prefs=applicationContext.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val account=prefs.getString("auto_refresh_account","").orEmpty()
        if(account.isBlank()) return Result.success()
        return try { TraktLibrary.refreshRecentForAccount(applicationContext,account); Result.success() }
        catch(_:Exception) { if(runAttemptCount<3) Result.retry() else Result.failure() }
    }
}
