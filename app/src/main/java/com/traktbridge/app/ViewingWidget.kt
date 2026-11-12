package com.traktbridge.app
import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews

class ViewingWidget:AppWidgetProvider() {
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray) {
        val result=goAsync()
        Thread {
            try {
                val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
                val mode=if(prefs.getBoolean("privacy_lock",false)) "private" else DashboardPreferences.widgetMode(prefs.getString("widget_mode","private").orEmpty())
                val groups=if(mode=="private") emptyList() else WatchHistory(context).use { ViewingGroups.build(it.records(0)) }
                val g=if(mode=="continue") SeriesLibrary.continueWatching(groups).firstOrNull() else null
                for(id in ids) {
                    val views=RemoteViews(context.packageName,R.layout.viewing_widget)
                    views.setTextViewText(R.id.widget_title,when(mode) { "review" -> groups.count { it.pending || it.queued }.toString()+" awaiting review"; "continue" -> g?.representative?.let { it.matchLabel.ifBlank { it.title } } ?: "No unfinished viewing"; else -> "TraktBridge" })
                    views.setTextViewText(R.id.widget_status,if(g!=null && g.positionMs>=0 && g.durationMs>0) (g.positionMs.toDouble()/g.durationMs*100).toInt().coerceIn(0,100).toString()+"% • last observed" else "Open TraktBridge")
                    views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    manager.updateAppWidget(id,views)
                }
            } finally { result.finish() }
        }.start()
    }
}
