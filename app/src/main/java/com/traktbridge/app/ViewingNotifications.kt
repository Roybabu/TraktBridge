package com.traktbridge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

object ViewingNotifications {
    const val CHANNEL = "viewing_review"
    const val REVIEW = "com.traktbridge.app.REVIEW"
    const val USAGE_REVIEW = "com.traktbridge.app.USAGE_REVIEW"
    const val IGNORE = "com.traktbridge.app.IGNORE"
    private const val ID = 1
    fun enabled(context: Context): Boolean {
        if (!context.getSharedPreferences("tracker", Context.MODE_PRIVATE).getBoolean("review_notifications",false)) return false
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }
    fun post(context: Context, record: WatchRecord): Boolean {
        if (!enabled(context)) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Viewing review",NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Review detected viewing sessions"; lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val uri = Uri.Builder().scheme("traktbridge").authority("review").appendPath(record.id).build()
        val review = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setAction(REVIEW).setData(uri).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ignore = PendingIntent.getBroadcast(context,0,Intent(context,ReviewReceiver::class.java).setAction(IGNORE).setData(uri),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = record.title.ifBlank { "Unidentified video" }
        val time = if (record.watched >= 60000) (record.watched / 60000).toString() + " min" else (record.watched / 1000).toString() + " sec"
        val text = if(record.id.startsWith("usage:")) title+" • Possible viewing; title and playback unverified. Review before matching." else title + " • " + time + " sampled watch time. Review your Trakt match."
        val publicVersion = Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Viewing activity detected").setContentText("Unlock to review.").build()
        val notification = Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Viewing activity detected").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).setContentIntent(review).setAutoCancel(true).setOnlyAlertOnce(true).addAction(Notification.Action.Builder(null,"Review / identify",review).build()).addAction(Notification.Action.Builder(null,"Dismiss reminder",ignore).build()).build()
        return try { manager.notify(record.id,ID,notification); true } catch (_: SecurityException) { false }
    }
    fun postUsage(context: Context, activity: AppActivity): Boolean {
        if (!enabled(context)) return false
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL,"Viewing review",NotificationManager.IMPORTANCE_DEFAULT))
        if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) return false
        val label=runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(activity.source,0)).toString() }.getOrDefault(activity.source)
        val review=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).setAction(USAGE_REVIEW).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text="Did you watch anything in "+label+"? Playback and title could not be verified."
        val publicVersion=Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("App activity review").build()
        val notification=Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Possible viewing activity").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(review).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion).addAction(Notification.Action.Builder(null,"Review app activity",review).build()).build()
        return try { manager.notify("usage:"+activity.source,2,notification); true } catch(_: SecurityException) { false }
    }
    fun cancel(context: Context,id: String) { context.getSystemService(NotificationManager::class.java).cancel(id,ID) }
}
