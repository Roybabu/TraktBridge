package com.traktbridge.app

import android.content.ComponentName
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.media.MediaMetadata
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import org.json.JSONArray
import org.json.JSONObject

class PlaybackListener : NotificationListenerService() {
    private lateinit var history: WatchHistory
    private val prefs by lazy { getSharedPreferences("tracker", MODE_PRIVATE) }
    private var lastUsageCheck=0L
    private var lastTick=0L
    private val retained=mutableMapOf<String,Pair<String,String>>()
    private val mediaTitles=mutableMapOf<String,Pair<Long,String>>()
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val n=sbn ?: return
        val pkg=n.packageName
        if(!prefs.getBoolean("enabled",false) || pkg !in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())) return
        val extras=n.notification.extras
        if(!extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) return
        val title=extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        if(PlayerRules.validTitle(title)) { mediaTitles[pkg]=System.currentTimeMillis() to title; DebugTrace.event("media-notification","package="+pkg+" titlePresent=true") }
    }
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) { sbn?.let { mediaTitles.remove(it.packageName) } }
    override fun onCreate() { super.onCreate(); DebugTrace.init(this); DebugTrace.event("listener","service created"); history = WatchHistory(this); history.recover() }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            try { capture(); notifyPending(); notifyUsageFallback() } catch (e: Exception) { DebugTrace.failure("poll",e); prefs.edit().putString("sample_error",e.javaClass.simpleName).apply(); history.finishAll() } finally { handler.postDelayed(this, 5000) }
        }
    }
    override fun onListenerConnected() { super.onListenerConnected(); DebugTrace.event("listener","connected"); prefs.edit().putBoolean("listener_connected",true).apply(); handler.removeCallbacks(poll); handler.post(poll) }
    override fun onListenerDisconnected() { DebugTrace.event("listener","disconnected"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); history.finishAll(); super.onListenerDisconnected() }
    override fun onDestroy() { DebugTrace.event("listener","destroyed"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); history.finishAll(); history.close(); super.onDestroy() }
    private fun notifyPending() {
        if (!getSharedPreferences("tracker",MODE_PRIVATE).getBoolean("enabled",false) || !ViewingNotifications.enabled(this)) return
        for (record in history.pendingNotifications().take(3)) {
            val posted=ViewingNotifications.post(this,record); DebugTrace.event("notification","viewing review posted="+posted); if(posted) history.markNotified(record.id)
        }
    }
    private fun notifyUsageFallback() {
        if (!prefs.getBoolean("enabled",false) || !prefs.contains("usage_since") || !UsageFallback.allowed(this) || !ViewingNotifications.enabled(this)) return
        val now=System.currentTimeMillis()
        if (now-lastUsageCheck<15000) return
        lastUsageCheck=now
        val since=maxOf(prefs.getLong("usage_since",now),now-10*60*1000L)
        val allowed=prefs.getStringSet("allowed",emptySet()) ?: emptySet()
        for (activity in UsageFallback.recent(this,allowed,since).sortedBy { it.end }) {
            // Allow time for media sessions to finish and suppress overlap with actual playback.
            if(now-activity.end<15000 || history.overlapsActivity(activity.source,activity.start,activity.end)) continue
            val key="usage_reminded:"+activity.source
            if(activity.end<=prefs.getLong(key,0)) continue
            val hint=TitleAccessibility.hint(this,activity.source,activity.start,activity.end)
            val posted=if(hint!=null) {
                val id=history.addSuggestedActivity(activity.source,hint.first,hint.second,activity.start,activity.end)
                val record=history.record(id)
                if(record!=null && ViewingNotifications.post(this,record)) { history.markNotified(id); true } else false
            } else ViewingNotifications.postUsage(this,activity); DebugTrace.event("usage","fallback notification posted="+posted+" durationSec="+((activity.end-activity.start)/1000)); if(posted) prefs.edit().putLong(key,activity.end).apply()
        }
    }
    private fun capture() {
        val tick=SystemClock.elapsedRealtime()
        if(lastTick>0 && tick-lastTick>12000) DebugTrace.event("sampling","gapMs="+(tick-lastTick))
        lastTick=tick
        prefs.edit().putLong("last_sample",System.currentTimeMillis()).remove("sample_error").apply()
        if (!prefs.getBoolean("enabled", false)) { DebugTrace.event("sampling","disabled"); history.finishAll(); retained.clear(); prefs.edit().remove("sessions").apply(); return }
        val allowed = prefs.getStringSet("allowed", emptySet()) ?: emptySet()
        val sessions = try {
            getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, PlaybackListener::class.java))
        } catch (e: SecurityException) { DebugTrace.failure("media access",e); prefs.edit().putString("sample_error","Media session access denied").apply(); history.finishAll(); return }
        DebugTrace.event("sampling","all="+sessions.size+" selected="+sessions.count { it.packageName in allowed }+" allowed="+allowed.size)
        val rows = JSONArray()
        val samples = mutableListOf<WatchSample>()
        val selected=sessions.filter { it.packageName in allowed }
        val keys=selected.map { it.packageName+":"+it.sessionToken.hashCode() }.toSet()
        retained.keys.retainAll(keys)
        selected.forEach { c ->
            val m = c.metadata
            val p = c.playbackState
            DebugTrace.event("media","selected package="+c.packageName+" metadata="+(m!=null)+" titlePresent="+(!m?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() || !m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank())+" state="+(p?.state ?: -1)+" positionMs="+(p?.position ?: -1)+" durationMs="+(m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0))
            val direct=m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { PlayerRules.validTitle(it) } ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
            val now=System.currentTimeMillis()
            val note=mediaTitles[c.packageName]?.takeIf { now-it.first in 0..120000 }?.second.orEmpty()
            val key=c.packageName+":"+c.sessionToken.hashCode()
            val playing=p?.state==PlaybackState.STATE_PLAYING
            val hint=if(playing) TitleAccessibility.hint(this,c.packageName,now-120000,now) else null
            val prior=retained[key]
            val real=direct.takeIf { PlayerRules.validTitle(it) } ?: note.takeIf { PlayerRules.validTitle(it) }
            val title=real ?: hint?.first ?: prior?.first.orEmpty()
            val subtitle=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() } ?: hint?.takeIf { it.first==title }?.second ?: prior?.takeIf { it.first==title }?.second.orEmpty()
            if(playing && title.isNotBlank()) retained[key]=title to subtitle
            if(p?.state in setOf(PlaybackState.STATE_STOPPED,PlaybackState.STATE_NONE,PlaybackState.STATE_ERROR)) retained.remove(key)
            val mediaDuration=m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val mediaPosition=PlaybackPosition.sanitize(p?.position ?: -1L,mediaDuration)
            val screen=if(playing && mediaPosition<0) TitleAccessibility.timing(this,c.packageName,title,now) else null
            val duration=screen?.duration ?: mediaDuration
            val position=screen?.position ?: mediaPosition
            if(playing && p!=null && p.position>=0 && position<0) DebugTrace.event("progress","package="+c.packageName+" rejected position without valid runtime")
            samples.add(WatchSample(key,c.packageName,title,subtitle,duration,position,playing))
            rows.put(JSONObject().apply {
                put("package", c.packageName)
                put("title", title)
                put("displayTitle", m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "")
                put("subtitle", subtitle)
                put("durationMs", duration)
                put("positionMs", position)
                put("state", p?.state ?: 0)
                put("sampledAt", System.currentTimeMillis())
            })
        }
        history.ingest(samples, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        prefs.edit().putString("sessions", rows.toString()).putInt("session_count",sessions.size).apply {
            if(rows.length()>0) putString("last_nonempty_sessions",rows.toString())
        }.apply()
    }
}
