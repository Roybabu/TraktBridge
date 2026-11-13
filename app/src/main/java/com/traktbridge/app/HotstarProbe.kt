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
    private fun prefs(c:Context)=c.getSharedPreferences("tracker",Context.MODE_PRIVATE)
    private fun file(c:Context)=File(c.filesDir,"hotstar-title-probe.txt")
    fun enabled(c:Context)=!prefs(c).getBoolean("privacy_lock",false) && System.currentTimeMillis()<prefs(c).getLong(KEY,0)
    @Synchronized fun start(c:Context) { clear(c);prefs(c).edit().putLong(KEY,System.currentTimeMillis()+900000).apply();last=0;lastScreen=0 }
    fun stop(c:Context) { prefs(c).edit().remove(KEY).apply() }
    @Synchronized fun clear(c:Context) { file(c).delete() }
    @Synchronized fun report(c:Context):String=if(prefs(c).getBoolean("privacy_lock",false)) "Unlock or disable app privacy lock before accessing this diagnostic." else "JioHotstar title detection diagnostic\nCapture active: "+enabled(c)+"\n"+runCatching { file(c).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    private fun safe(value:String):String=HotstarPlayback.clean(value)?.take(160).orEmpty()
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
            append(c,"state="+(p?.state ?: -1)+" positionMs="+position+" durationMs="+duration+" title="+safe(title)+" displayTitle="+safe(display)+" subtitle="+safe(detail)+" decision="+(if(result!=null) "accepted: "+result.title else "rejected: missing, conflicting or invalid playback evidence"))
        }
    }
    @Synchronized fun screen(c:Context,ids:Set<String>) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastScreen!=0L && now-lastScreen<2000) return
        lastScreen=now
        append(c,"visible control IDs="+ids.take(80).joinToString(","))
    }
}
