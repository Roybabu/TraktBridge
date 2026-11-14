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
    companion object {
        @Volatile private var instance:PlaybackListener?=null
        fun liveConnected()=instance?.connected==true
        fun reconnect() {
            val listener=instance ?: return
            listener.handler.post {
                if(listener.connected) runCatching { listener.requestUnbind() }.onFailure { DebugTrace.failure("listener-unbind",it) }
                listener.handler.postDelayed({ TrackingRecovery.request(listener.applicationContext,"stale connected listener") },2000)
            }
        }
        fun wake():Boolean {
            val listener=instance ?: return false
            if(!listener.connected) return false
            listener.handler.post {
                if(listener.connected) {
                    listener.handler.removeCallbacks(listener.poll)
                    listener.handler.post(listener.poll)
                }
            }
            return true
        }
    }
    private lateinit var history: WatchHistory
    private val prefs by lazy { getSharedPreferences("tracker", MODE_PRIVATE) }
    private var lastTick=0L
    private var lastSuggestionCheck= -15000L
    @Volatile private var connected=false
    private var sessionCallbackRegistered=false
    private val sessionCallback=MediaSessionManager.OnActiveSessionsChangedListener { if(connected) wake() }
    private var consecutiveFailures=0
    private val sessionIds=mutableMapOf<android.media.session.MediaSession.Token,String>()
    private fun sessionKey(controller:android.media.session.MediaController):String = sessionIds.getOrPut(controller.sessionToken) { controller.packageName+":"+java.util.UUID.randomUUID().toString() }
    private val youtubeIdentities=mutableMapOf<String,String>()
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val n=sbn ?: return
        val pkg=n.packageName
        if(!prefs.getBoolean("enabled",false) || pkg !in PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty())) return
        val extras=n.notification.extras
        if(!extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) return
        wake()
    }
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) { if(sbn?.packageName==YouTubePlayback.PACKAGE) wake() }
    override fun onCreate() { super.onCreate(); PlaybackTrackingMigration.apply(this); DebugTrace.init(this); DebugTrace.event("listener","service created"); prefs.edit().putBoolean("listener_connected",false).apply(); history = WatchHistory(this); runCatching { history.recover() }.onFailure { DebugTrace.failure("history-recovery",it) } }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if(!connected) return
            try { capture(); requestCatalogueSuggestions(); notifyPending(); consecutiveFailures=0 } catch (e: Exception) { consecutiveFailures=(consecutiveFailures+1).coerceAtMost(3); DebugTrace.failure("poll",e); prefs.edit().putString("sample_error",e.javaClass.simpleName).apply(); runCatching { history.finishAll() } } finally { if(connected) handler.postDelayed(this, ResourcePolicy.pollDelay(prefs.getBoolean("enabled",false),prefs.getInt("tracked_session_count",0)>0,consecutiveFailures,getSystemService(android.os.PowerManager::class.java).isPowerSaveMode,android.os.Build.VERSION.SDK_INT>=28 && getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)) }
        }
    }
    override fun onListenerConnected() { super.onListenerConnected(); connected=true; instance=this; if(!sessionCallbackRegistered) runCatching { getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(sessionCallback,ComponentName(this,PlaybackListener::class.java),handler); sessionCallbackRegistered=true }.onFailure { DebugTrace.failure("session-callback",it) }; DeviceControl.dispatch={ session,action -> runCatching { remoteCommand(session,action) }.getOrDefault(false) }; DebugTrace.event("listener","connected"); prefs.edit().putBoolean("listener_connected",true).apply(); handler.removeCallbacks(poll); handler.post(poll) }
    private fun unregisterSessionCallback() {
        if(sessionCallbackRegistered) runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionCallback) }
        sessionCallbackRegistered=false
        if(instance===this) instance=null
    }
    override fun onListenerDisconnected() { connected=false; unregisterSessionCallback(); DeviceControl.dispatch=null; DebugTrace.event("listener","disconnected"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); TrackingRecovery.clearObservation(this); if(prefs.getBoolean("enabled",false)) handler.postDelayed({ TrackingRecovery.request(applicationContext,"listener disconnected") },2000); runCatching { history.finishAll() }; sessionIds.clear(); youtubeIdentities.clear(); YouTubePlayback.clear(); super.onListenerDisconnected() }
    override fun onDestroy() { connected=false; unregisterSessionCallback(); DeviceControl.dispatch=null; DebugTrace.event("listener","destroyed"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); TrackingRecovery.clearObservation(this); runCatching { history.finishAll() }; runCatching { history.close() }; super.onDestroy() }
    private fun requestCatalogueSuggestions() {
        if(!prefs.getBoolean("enabled",false) || prefs.getBoolean("privacy_lock",false)) return
        val now=SystemClock.elapsedRealtime()
        if(now-lastSuggestionCheck<15000) return
        lastSuggestionCheck=now
        runCatching { AutoMatchSuggestions.enqueue(this,history.records(200)) }.onFailure { DebugTrace.failure("catalogue-suggestion",it) }
    }
    private fun notifyPending() {
        if (!getSharedPreferences("tracker",MODE_PRIVATE).getBoolean("enabled",false) || !ViewingNotifications.enabled(this)) return
        for (record in history.pendingNotifications().take(3)) {
            val posted=ViewingNotifications.post(this,record); DebugTrace.event("notification","viewing review posted="+posted); if(posted) history.markNotified(record.id)
        }
    }
    private fun remoteCommand(session:String,action:String):Boolean {
        if(!connected || !DeviceControl.enabled(this)) return false
        val allowed=PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty())
        val controller=getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this,PlaybackListener::class.java)).firstOrNull {
            it.packageName in allowed && sessionIds[it.sessionToken]?.substringAfterLast(":")==session
        } ?: return false
        val state=controller.playbackState ?: return false
        if(!DeviceControl.permits(DeviceControl.mask(state.actions),action)) return false
        if(action=="play") controller.transportControls.play() else controller.transportControls.pause()
        return true
    }
    private fun capture() {
        val tick=SystemClock.elapsedRealtime()
        if(lastTick>0 && tick-lastTick>12000) DebugTrace.event("sampling","gapMs="+(tick-lastTick))
        lastTick=tick
        
        if (!prefs.getBoolean("enabled", false)) { DebugTrace.event("sampling","disabled"); history.finishAll(); youtubeIdentities.clear(); YouTubePlayback.clear(); prefs.edit().remove("sessions").apply(); return }
        val allowed=PlaybackDetectors.selected(prefs.getStringSet("allowed",emptySet()).orEmpty())
        val active = try {
            getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(this, PlaybackListener::class.java))
        } catch (e: SecurityException) { DebugTrace.failure("media access",e); prefs.edit().putString("sample_error","Media session access denied").apply(); history.finishAll(); return }
        val notificationControllers=runCatching { activeNotifications.orEmpty().mapNotNull { n ->
            if(n.packageName !in allowed) return@mapNotNull null
            @Suppress("DEPRECATION") val token=n.notification.extras.getParcelable<android.media.session.MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION) ?: return@mapNotNull null
            runCatching { android.media.session.MediaController(this,token) }.getOrNull()?.takeIf { it.packageName==n.packageName }
        } }.getOrDefault(emptyList())
        val sessions=(active+notificationControllers).distinctBy { it.sessionToken }
        DebugTrace.event("sampling","notificationControllers="+notificationControllers.size)
        DebugTrace.event("sampling","all="+sessions.size+" selected="+sessions.count { it.packageName in allowed }+" allowed="+allowed.size)
        val rows = JSONArray()
        val samples = mutableListOf<WatchSample>()
        val selected=sessions.filter { it.packageName in allowed }
        sessionIds.keys.retainAll(selected.map { it.sessionToken }.toSet())
        val keys=selected.map { sessionKey(it) }.toSet()
        youtubeIdentities.keys.retainAll(keys)
        val youtubeScreenAllowed=prefs.getBoolean("title_optin",false) && prefs.getBoolean("title_connected",false)
        if(!youtubeScreenAllowed) { YouTubePlayback.clear(); youtubeIdentities.clear() }
        selected.forEach { c ->
            val m = c.metadata
            val p = c.playbackState
            DebugTrace.event("media","selected package="+c.packageName+" metadata="+(m!=null)+" titlePresent="+(!m?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() || !m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank())+" state="+(p?.state ?: -1)+" positionMs="+(p?.position ?: -1)+" durationMs="+(m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0))
            val direct=m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { YouTubePlayback.clean(it)!=null } ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { YouTubePlayback.clean(it)!=null }.orEmpty()
            val now=System.currentTimeMillis()
            val explicitSegment=SegmentDetection.metadata(m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty())
            val segment=if(explicitSegment!=PlaybackSegment.UNKNOWN) explicitSegment else if(selected.count { it.packageName==c.packageName }==1) TitleAccessibility.segment(this,c.packageName,now) else PlaybackSegment.UNKNOWN
            val key=sessionKey(c)
            val phase=when(p?.state) {
                PlaybackState.STATE_PLAYING -> PlaybackPhase.PLAYING
                PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING -> PlaybackPhase.BUFFERING
                PlaybackState.STATE_PAUSED -> PlaybackPhase.PAUSED
                PlaybackState.STATE_STOPPED,PlaybackState.STATE_NONE,PlaybackState.STATE_ERROR -> PlaybackPhase.STOPPED
                else -> PlaybackPhase.UNKNOWN
            }
            val playing=phase==PlaybackPhase.PLAYING
            val metaDetail=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
            val youtubeNotes=if(direct.isBlank()) runCatching { activeNotifications.orEmpty().filter { n ->
                if(n.packageName!=YouTubePlayback.PACKAGE || now-n.postTime !in 0..30000) return@filter false
                @Suppress("DEPRECATION") val token=n.notification.extras.getParcelable<android.media.session.MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION)
                token==c.sessionToken
            }.mapNotNull { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() } }.getOrDefault(emptyList()) else emptyList()
            val recognition=YouTubePlayback.recognition(direct,youtubeNotes,metaDetail)
            run {
                val screenMode=YouTubePlayback.screenMode(now,youtubeScreenAllowed)
                val rawUri=m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
                val rawId=m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
                val modes=listOf(YouTubePlayback.uriMode(rawUri),YouTubePlayback.uriMode(rawId))
                val uriMode=when { YouTubePlayback.Mode.SHORTS in modes -> YouTubePlayback.Mode.SHORTS; YouTubePlayback.Mode.REGULAR in modes -> YouTubePlayback.Mode.REGULAR; else -> YouTubePlayback.Mode.UNKNOWN }
                val identity=recognition?.title.orEmpty()
                if(identity.isBlank() || segment==PlaybackSegment.ADVERTISEMENT || !YouTubePlayback.allowContinuation(screenMode,uriMode,youtubeIdentities[key],identity)) {
                    youtubeIdentities.remove(key)
                    DebugTrace.event("youtube","excluded: mode="+screenMode+" uri="+uriMode+" titlePresent="+identity.isNotBlank())
                    return@forEach
                }
                if(phase==PlaybackPhase.STOPPED) youtubeIdentities.remove(key) else youtubeIdentities[key]=identity
            }
            val title=recognition?.title ?: return@forEach
            val subtitle=recognition.detail
            if(playing) DebugTrace.event("recognition","package="+c.packageName+" score="+recognition.score+" sources="+recognition.sources.joinToString(","))
            val duration=m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position=PlaybackPosition.sanitize(p?.position ?: -1L,duration)
            val positionSource=if(position>=0) "media" else "unknown"
            samples.add(WatchSample(key,c.packageName,title,subtitle,duration,position,playing,phase,positionSource,p?.playbackSpeed?.takeIf { it>0 } ?: 1f,segment))
            rows.put(JSONObject().apply {
                put("package", c.packageName)
                put("sessionLabel","Session "+key.substringAfterLast(":").take(8))
                put("sessionId",key.substringAfterLast(":"))
                put("contentId",PlaybackHandoff.extract(c.packageName,m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()))
                put("controlMask",if(DeviceControl.enabled(this@PlaybackListener)) DeviceControl.mask(p?.actions ?: 0L) else 0)
                put("title", title)
                put("displayTitle", m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "")
                put("subtitle", subtitle)
                put("durationMs", duration)
                put("positionMs", position)
                put("state", p?.state ?: 0)
                put("output",when(c.playbackInfo?.playbackType) { android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE -> "remote"; android.media.session.MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL -> "local"; else -> "unknown" })
                put("segment",segment.name)
                put("recognitionScore",recognition.score)
                put("recognitionConflict",recognition.conflict)
                put("positionSource",positionSource)
                put("sampledAt", System.currentTimeMillis())
            })
        }
        val local=ConcurrentPlayback.select(samples)
        history.ingest(local,SystemClock.elapsedRealtime(),System.currentTimeMillis())
        prefs.edit().putLong("last_sample",System.currentTimeMillis()).remove("sample_error").putString("sessions", rows.toString()).putInt("session_count",selected.size).putInt("tracked_session_count",local.size).apply {
            if(rows.length()>0) putString("last_nonempty_sessions",rows.toString())
        }.apply()
    }
}
