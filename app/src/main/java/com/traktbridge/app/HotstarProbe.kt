package com.traktbridge.app

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import java.io.File
import java.time.Instant

/** Separate explicit diagnostic consent; never add raw metadata to ordinary DebugTrace. */
object HotstarProbe {
    private const val KEY="hotstar_probe_until"
    private var last=0L
    private var lastScreen=0L
    private var lastSignals=0L
    private fun prefs(c:Context)=c.getSharedPreferences("tracker",Context.MODE_PRIVATE)
    private fun file(c:Context)=File(c.filesDir,"hotstar-title-probe.txt")
    fun enabled(c:Context)=!prefs(c).getBoolean("privacy_lock",false) && System.currentTimeMillis()<prefs(c).getLong(KEY,0)
    @Synchronized fun start(c:Context) { clear(c);prefs(c).edit().putLong(KEY,System.currentTimeMillis()+900000).apply();last=0;lastScreen=0;lastSignals=0 }
    fun stop(c:Context) { prefs(c).edit().remove(KEY).apply() }
    @Synchronized fun clear(c:Context) { file(c).delete() }
    @Synchronized fun report(c:Context):String=if(prefs(c).getBoolean("privacy_lock",false)) "Unlock or disable app privacy lock before accessing this diagnostic." else "JioHotstar title detection diagnostic\nCapture active: "+enabled(c)+"\n"+runCatching { file(c).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    private fun safe(value:String):String=HotstarPlayback.clean(value)?.takeIf { !it.contains(Regex("(?i)(https?://|www\\.|@|[0-9]{7,})")) }?.take(160).orEmpty()
    @Synchronized fun signals(c:Context,active:Int,tokenControllers:Int,notes:List<android.service.notification.StatusBarNotification>) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastSignals!=0L && now-lastSignals<4000) return
        lastSignals=now
        append(c,"sources activeSessions="+active+" notificationTokenControllers="+tokenControllers+" notifications="+notes.size)
        notes.take(4).forEach { n ->
            val e=n.notification.extras
            if(e.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) || n.notification.category==android.app.Notification.CATEGORY_TRANSPORT) append(c,"notification title="+safe(e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty())+" text="+safe(e.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()))
        }
        val audio=runCatching { c.getSystemService(android.media.AudioManager::class.java).isMusicActive }.getOrNull()
        val usage=runCatching {
            val manager=c.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val end=System.currentTimeMillis()
            val events=manager.queryEvents(end-30000,end)
            val event=android.app.usage.UsageEvents.Event()
            var latest="none"
            while(events.hasNextEvent()) {
                events.getNextEvent(event)
                if(event.packageName==HotstarPlayback.PACKAGE && (event.eventType==1 || event.eventType==2)) latest=if(event.eventType==1) "foreground" else "background"
            }
            latest
        }.getOrNull()
        append(c,"supporting globalAudioActive="+audio+" recentHotstarUsageEvent="+usage+" (not title or playback proof)")
    }
    private fun append(c:Context,value:String) {
        if(!enabled(c)) return
        runCatching { val f=file(c); if(f.length()>96000) f.writeText(f.readText().takeLast(48000).substringAfter('\n',""));f.appendText(Instant.now().toString()+" "+value.replace('\n',' ').replace('\r',' ')+"\n") }
    }
    @Synchronized fun media(c:Context,controllers:List<MediaController>,notifications:Int) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(last!=0L && now-last<2000) return
        last=now
        append(c,"media sessions="+controllers.size+" notifications="+notifications)
        controllers.take(4).forEach { controller ->
            val m=controller.metadata;val p=controller.playbackState
            val title=m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val display=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
            val detail=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
            val duration=m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position=p?.position ?: -1L
            val result=HotstarPlayback.recognize(title,display,detail,duration,position)
            append(c,"metadata keys="+m?.keySet()?.filter { it.startsWith("android.media.metadata.") }?.sorted()?.joinToString(",")+" mediaIdPresent="+(!m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).isNullOrBlank())+" mediaUriPresent="+(!m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).isNullOrBlank())+" extrasPresent="+(controller.extras!=null))
            append(c,"state="+(p?.state ?: -1)+" positionMs="+position+" durationMs="+duration+" title="+safe(title)+" displayTitle="+safe(display)+" subtitle="+safe(detail)+" decision="+(if(result!=null) "accepted: "+safe(result.title) else "rejected: missing, conflicting or invalid playback evidence"))
        }
    }
    @Synchronized fun screen(c:Context,ids:Set<String>,labels:Set<String> = emptySet(),eventType:Int = -1) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastScreen!=0L && now-lastScreen<2000) return
        lastScreen=now
        append(c,"accessibility eventType="+eventType+" visible control IDs="+ids.take(80).joinToString(",")+" candidate player labels="+labels.map(::safe).filter { it.isNotBlank() }.take(12).joinToString(" | "))
    }
}
