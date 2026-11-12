package com.traktbridge.app
import android.app.*
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit
import java.time.Instant
import android.os.Build
object EpisodeReminders {
    fun schedule(context:Context,episode:UpcomingEpisode) {
        val at=Instant.parse(episode.airs).toEpochMilli()
        require(at>System.currentTimeMillis())
        val key=java.security.MessageDigest.getInstance("SHA-256").digest((episode.show+"|"+episode.season+"|"+episode.episode+"|"+episode.airs).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val data=workDataOf("show" to episode.show.take(300),"season" to episode.season,"episode" to episode.episode,"at" to at)
        WorkManager.getInstance(context).enqueueUniqueWork("episode:"+key,ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<EpisodeReminderWorker>().setInputData(data).setInitialDelay((at-System.currentTimeMillis()).coerceAtLeast(0),TimeUnit.MILLISECONDS).addTag("episode-reminder").build())
    }
    fun cancelAll(context:Context) { WorkManager.getInstance(context).cancelAllWorkByTag("episode-reminder") }
}
class EpisodeReminderWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        val context=applicationContext
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) return Result.success()
        if(!context.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("episode_reminders",true)) return Result.success()
        val manager=context.getSystemService(NotificationManager::class.java)
        if(!manager.areNotificationsEnabled()) return Result.success()
        manager.createNotificationChannel(NotificationChannel("episode-reminders","Episode reminders",NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility=Notification.VISIBILITY_PRIVATE })
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val text=inputData.getString("show").orEmpty()+" • S"+inputData.getInt("season",0)+" E"+inputData.getInt("episode",0)
        val n=Notification.Builder(context,"episode-reminders").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Scheduled episode air time").setContentText(text).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()
        return try { manager.notify(id.toString(),1,n); Result.success() } catch(_:SecurityException) { Result.success() }
    }
}
