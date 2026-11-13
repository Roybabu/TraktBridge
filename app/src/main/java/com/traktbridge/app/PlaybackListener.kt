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
    private var lastUsageCheck=0L
    private var lastLearningCheck=0L
    private var lastTick=0L
    @Volatile private var connected=false
    private var sessionCallbackRegistered=false
    private val sessionCallback=MediaSessionManager.OnActiveSessionsChangedListener { if(connected) wake() }
    private var consecutiveFailures=0
    private val serverSampler by lazy { ServerSampler(applicationContext) }
    private data class RetainedTitle(val title:String,val detail:String,val observed:Long)
    private val retained=mutableMapOf<String,RetainedTitle>()
    private val sessionIds=mutableMapOf<android.media.session.MediaSession.Token,String>()
    private fun sessionKey(controller:android.media.session.MediaController):String = sessionIds.getOrPut(controller.sessionToken) { controller.packageName+":"+java.util.UUID.randomUUID().toString() }
    private val mediaTitles=mutableMapOf<String,Pair<Long,String>>()
    private val youtubeIdentities=mutableMapOf<String,String>()
    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val n=sbn ?: return
        val pkg=n.packageName
        if(!prefs.getBoolean("enabled",false) || pkg !in (prefs.getStringSet("allowed",emptySet()) ?: emptySet())) return
        val extras=n.notification.extras
        if(!extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) return
        wake()
        val title=extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        if(pkg!=YouTubePlayback.PACKAGE && PlayerRules.validTitle(title)) { mediaTitles[pkg]=System.currentTimeMillis() to title; DebugTrace.event("media-notification","package="+pkg+" titlePresent=true") }
    }
    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) { sbn?.let { mediaTitles.remove(it.packageName) } }
    override fun onCreate() { super.onCreate(); DebugTrace.init(this); DebugTrace.event("listener","service created"); history = WatchHistory(this); runCatching { history.recover() }.onFailure { DebugTrace.failure("history-recovery",it) } }
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            if(!connected) return
            try { capture(); if(prefs.getBoolean("enabled",false) && prefs.getBoolean("learned_auto",false) && SystemClock.elapsedRealtime()-lastLearningCheck>=30000) { lastLearningCheck=SystemClock.elapsedRealtime(); val n=history.applyLearned(); if(n>0) DebugTrace.event("recognition","learned matches="+n) }; notifyPending(); notifyUsageFallback(); consecutiveFailures=0 } catch (e: Exception) { consecutiveFailures=(consecutiveFailures+1).coerceAtMost(3); DebugTrace.failure("poll",e); prefs.edit().putString("sample_error",e.javaClass.simpleName).apply(); runCatching { history.finishAll() } } finally { if(connected) handler.postDelayed(this, ResourcePolicy.pollDelay(prefs.getBoolean("enabled",false),prefs.getInt("tracked_session_count",0)>0,consecutiveFailures,getSystemService(android.os.PowerManager::class.java).isPowerSaveMode,android.os.Build.VERSION.SDK_INT>=28 && getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted)) }
        }
    }
    override fun onListenerConnected() { super.onListenerConnected(); connected=true; instance=this; if(!sessionCallbackRegistered) runCatching { getSystemService(MediaSessionManager::class.java).addOnActiveSessionsChangedListener(sessionCallback,ComponentName(this,PlaybackListener::class.java),handler); sessionCallbackRegistered=true }.onFailure { DebugTrace.failure("session-callback",it) }; DeviceControl.dispatch={ session,action -> runCatching { remoteCommand(session,action) }.getOrDefault(false) }; DebugTrace.event("listener","connected"); prefs.edit().putBoolean("listener_connected",true).apply(); handler.removeCallbacks(poll); handler.post(poll) }
    private fun unregisterSessionCallback() {
        if(sessionCallbackRegistered) runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionCallback) }
        sessionCallbackRegistered=false
        if(instance===this) instance=null
    }
    override fun onListenerDisconnected() { connected=false; unregisterSessionCallback(); DeviceControl.dispatch=null; DebugTrace.event("listener","disconnected"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); TrackingRecovery.clearObservation(this); runCatching { history.finishAll() }; retained.clear(); sessionIds.clear(); mediaTitles.clear(); youtubeIdentities.clear(); YouTubePlayback.clear(); super.onListenerDisconnected() }
    override fun onDestroy() { connected=false; unregisterSessionCallback(); DeviceControl.dispatch=null; DebugTrace.event("listener","destroyed"); handler.removeCallbacks(poll); prefs.edit().putBoolean("listener_connected",false).apply(); runCatching { history.finishAll() }; runCatching { serverSampler.close() }; runCatching { history.close() }; super.onDestroy() }
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
            if(activity.source==YouTubePlayback.PACKAGE) continue // App usage cannot distinguish Shorts from regular videos.
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
    private fun remoteCommand(session:String,action:String):Boolean {
        if(!connected || !DeviceControl.enabled(this)) return false
        val allowed=prefs.getStringSet("allowed",emptySet()) ?: emptySet()
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
        prefs.edit().putLong("last_sample",System.currentTimeMillis()).remove("sample_error").apply()
        if (!prefs.getBoolean("enabled", false)) { DebugTrace.event("sampling","disabled"); history.finishAll(); serverSampler.clear(); retained.clear(); mediaTitles.clear(); youtubeIdentities.clear(); YouTubePlayback.clear(); prefs.edit().remove("sessions").apply(); return }
        val allowed = prefs.getStringSet("allowed", emptySet()) ?: emptySet()
        mediaTitles.keys.retainAll(allowed)
        val wallNow=System.currentTimeMillis()
        mediaTitles.entries.removeAll { wallNow-it.value.first !in 0..120000 }
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
        retained.keys.retainAll(keys)
        youtubeIdentities.keys.retainAll(keys)
        val youtubeScreenAllowed=prefs.getBoolean("title_optin",false) && prefs.getBoolean("title_connected",false)
        if(!youtubeScreenAllowed) { YouTubePlayback.clear(); youtubeIdentities.clear() }
        selected.forEach { c ->
            val config=resources.configuration
            val kind=DeviceDetection.kind(config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION,config.smallestScreenWidthDp)
            val profile=DeviceDetection.profile(DetectionProfiles.forPackage(c.packageName),kind)
            val m = c.metadata
            val p = c.playbackState
            DebugTrace.event("media","selected package="+c.packageName+" metadata="+(m!=null)+" titlePresent="+(!m?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() || !m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).isNullOrBlank())+" state="+(p?.state ?: -1)+" positionMs="+(p?.position ?: -1)+" durationMs="+(m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0))
            val isYouTube=c.packageName==YouTubePlayback.PACKAGE
            fun validTitle(value:String)=if(isYouTube) YouTubePlayback.clean(value)!=null else DetectionProfiles.validMetadata(value)
            val direct=m?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { validTitle(it) } ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { validTitle(it) }.orEmpty()
            val now=System.currentTimeMillis()
            val explicitSegment=SegmentDetection.metadata(m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty())
            val segment=if(explicitSegment!=PlaybackSegment.UNKNOWN) explicitSegment else if(selected.count { it.packageName==c.packageName }==1) TitleAccessibility.segment(this,c.packageName,now) else PlaybackSegment.UNKNOWN
            val note=mediaTitles[c.packageName]?.takeIf { now-it.first in 0..DeviceDetection.notificationAge(profile,kind) }?.second.orEmpty()
            val key=sessionKey(c)
            val phase=when(p?.state) {
                PlaybackState.STATE_PLAYING -> PlaybackPhase.PLAYING
                PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING -> PlaybackPhase.BUFFERING
                PlaybackState.STATE_PAUSED -> PlaybackPhase.PAUSED
                PlaybackState.STATE_STOPPED,PlaybackState.STATE_NONE,PlaybackState.STATE_ERROR -> PlaybackPhase.STOPPED
                else -> PlaybackPhase.UNKNOWN
            }
            val playing=phase==PlaybackPhase.PLAYING
            val hint=if(playing && !isYouTube) TitleAccessibility.hint(this,c.packageName,now-120000,now) else null
            val prior=retained[key]?.takeIf { tick-it.observed in 0..profile.retentionMs }
            val metaDetail=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
            val youtubeNotes=if(isYouTube && direct.isBlank()) runCatching { activeNotifications.orEmpty().filter { n ->
                if(n.packageName!=YouTubePlayback.PACKAGE || now-n.postTime !in 0..30000) return@filter false
                @Suppress("DEPRECATION") val token=n.notification.extras.getParcelable<android.media.session.MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION)
                token==c.sessionToken
            }.mapNotNull { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() } }.getOrDefault(emptyList()) else emptyList()
            val recognition=if(isYouTube) YouTubePlayback.recognition(direct,youtubeNotes,metaDetail) else RecognitionEngine.fuse(listOfNotNull(
                Evidence(direct,metaDetail,"media",profile.mediaWeight),
                Evidence(note.takeIf { DetectionProfiles.validMetadata(it) && selected.count { other -> other.packageName==c.packageName }==1 }.orEmpty(),"","notification",profile.notificationWeight),
                hint?.takeIf { selected.count { other -> other.packageName==c.packageName }==1 }?.let { Evidence(it.first,it.second,"accessibility",profile.screenWeight) }
            ))
            if(isYouTube) {
                val screenMode=YouTubePlayback.screenMode(now,youtubeScreenAllowed)
                val rawUri=m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
                val rawId=m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
                val modes=listOf(YouTubePlayback.uriMode(rawUri),YouTubePlayback.uriMode(rawId))
                val uriMode=when { YouTubePlayback.Mode.SHORTS in modes -> YouTubePlayback.Mode.SHORTS; YouTubePlayback.Mode.REGULAR in modes -> YouTubePlayback.Mode.REGULAR; else -> YouTubePlayback.Mode.UNKNOWN }
                val identity=recognition?.title.orEmpty()
                if(identity.isBlank() || segment==PlaybackSegment.ADVERTISEMENT || !YouTubePlayback.allowContinuation(screenMode,uriMode,youtubeIdentities[key],identity)) {
                    youtubeIdentities.remove(key); retained.remove(key)
                    DebugTrace.event("youtube","excluded: mode="+screenMode+" uri="+uriMode+" titlePresent="+identity.isNotBlank())
                    return@forEach
                }
                if(phase==PlaybackPhase.STOPPED) youtubeIdentities.remove(key) else youtubeIdentities[key]=identity
            }
            val safeRecognition=recognition?.takeIf { !it.conflict && it.score>=65 }
            if(recognition?.conflict==true || segment==PlaybackSegment.ADVERTISEMENT) retained.remove(key)
            val title=if(isYouTube) safeRecognition?.title.orEmpty() else if(recognition?.conflict==true) "" else safeRecognition?.title ?: prior?.title.orEmpty()
            val subtitle=if(isYouTube) safeRecognition?.detail.orEmpty() else if(recognition?.conflict==true) "" else safeRecognition?.detail?.takeIf { it.isNotBlank() } ?: prior?.takeIf { it.title==title }?.detail.orEmpty()
            if(playing && recognition!=null) DebugTrace.event("recognition","package="+c.packageName+" score="+recognition.score+" sources="+recognition.sources.joinToString(",")+" conflict="+recognition.conflict)
            if(!isYouTube && playing && safeRecognition!=null && segment!=PlaybackSegment.ADVERTISEMENT) retained[key]=RetainedTitle(title,subtitle,tick)
            if(p?.state in setOf(PlaybackState.STATE_STOPPED,PlaybackState.STATE_NONE,PlaybackState.STATE_ERROR)) retained.remove(key)
            val mediaDuration=m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val mediaPosition=PlaybackPosition.sanitize(p?.position ?: -1L,mediaDuration)
            val screen=if(!isYouTube && profile.screenTiming && playing && mediaPosition<0 && title.isNotBlank() && selected.count { it.packageName==c.packageName }==1) TitleAccessibility.timing(this,c.packageName,title,now) else null
            val duration=screen?.duration ?: mediaDuration
            val position=screen?.position ?: mediaPosition
            if(playing && p!=null && p.position>=0 && position<0) DebugTrace.event("progress","package="+c.packageName+" rejected position without valid runtime")
            if(recognition?.conflict!=true && segment!=PlaybackSegment.ADVERTISEMENT) samples.add(WatchSample(key,c.packageName,title,subtitle,duration,position,playing,phase,if(screen!=null) "screen" else if(mediaPosition>=0) "media" else "unknown",p?.playbackSpeed?.takeIf { it>0 } ?: 1f,segment))
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
                put("recognitionScore",recognition?.score ?: 0)
                put("recognitionConflict",recognition?.conflict ?: false)
                put("positionSource",if(screen!=null) "screen" else if(mediaPosition>=0) "media" else "unknown")
                put("sampledAt", System.currentTimeMillis())
            })
        }
        val remote=serverSampler.samples(System.currentTimeMillis())
        // Avoid double-counting local player sessions when the explicitly selected server client is active.
        val remotePackages=remote.map { when(it.source) { "server.plex"->"com.plexapp.android"; "server.jellyfin"->"org.jellyfin.mobile"; "server.emby"->"com.mb.android"; "server.kodi"->"org.xbmc.kodi"; else->"" } }.toSet()
        val local=ConcurrentPlayback.select(samples.filterNot { it.source in remotePackages })
        history.ingest(local+remote, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        prefs.edit().putString("sessions", rows.toString()).putInt("session_count",selected.size).putInt("tracked_session_count",local.size+remote.size).apply {
            if(rows.length()>0) putString("last_nonempty_sessions",rows.toString())
        }.apply()
    }
}
