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
    private data class Count(var attempts:Int=0,var evidence:Int=0)
    private val counts=linkedMapOf<String,Count>()
    private fun observe(source:String,available:Boolean) { val count=counts.getOrPut(source) { Count() };count.attempts++;if(available) count.evidence++ }
    private data class Prediction(val source:String,val item:CatalogueItem?,val latency:Long)
    private val predictions=linkedMapOf<String,MutableMap<String,Prediction>>()
    private data class Accuracy(var tested:Int=0,var series:Int=0,var exact:Int=0,var wrong:Int=0,var missing:Int=0,var latency:Long=0)
    private val accuracy=linkedMapOf<String,Accuracy>()
    @Synchronized fun prediction(c:Context,r:WatchRecord,source:String,item:CatalogueItem?,latency:Long=0) {
        if(!enabled(c) || r.source!=HotstarPlayback.PACKAGE) return
        if(predictions.size>=200 && r.id !in predictions) predictions.remove(predictions.keys.first())
        predictions.getOrPut(r.id) { linkedMapOf() }[source]=Prediction(source,item,latency)
        append(c,"resolution source="+source+" record="+fingerprint(r.id)+" candidate="+(item?.let { safe(it.title)+" id="+it.id+" showId="+it.showId+" season="+it.season+" episode="+it.episode } ?: "none")+" latencyMs="+latency)
    }
    @Synchronized fun confirmed(c:Context,r:WatchRecord,item:CatalogueItem) {
        if(!enabled(c) || r.source!=HotstarPlayback.PACKAGE) return
        val saved=runCatching { WatchHistory(c).use { it.record(r.id) } }.getOrNull() ?: return
        if(saved.traktId!=item.id || saved.matchType!=item.type) return
        val entries=predictions.remove(r.id) ?: return
        for((source,p) in entries) {
            val a=accuracy.getOrPut(source) { Accuracy() }
            a.tested++;a.latency+=p.latency
            val i=p.item
            if(i==null) a.missing++ else {
                val parent=if(i.type=="show") i.id else i.showId
                val seriesCorrect=item.type=="episode" && parent>0 && parent==item.showId
                val exact=i.type==item.type && i.id==item.id
                if(seriesCorrect) a.series++
                if(exact) a.exact++ else if(!seriesCorrect || i.type!="show") a.wrong++
            }
        }
        append(c,"ground truth record="+fingerprint(r.id)+" title="+safe(item.title)+" id="+item.id+" showId="+item.showId)
    }
    private fun accuracyReport():String = "\nReview-confirmed resolution benchmark (one result per record and method)\n"+accuracy.entries.joinToString("\n") { (source,a) -> "$source: tested=${a.tested} correctSeries=${a.series} exact=${a.exact} wrong=${a.wrong} noResult=${a.missing} meanLatencyMs=${a.latency/maxOf(1,a.tested)}" }+"\nPending benchmark records: "+predictions.size+"\n"
    private fun benchmark():String = "Source evidence coverage (not identification accuracy)\n"+counts.entries.joinToString("\n") { (name,count) -> "$name: ${count.evidence}/${count.attempts} (${100*count.evidence/maxOf(1,count.attempts)}%)" }+"\nShare/deep links, provider catalogue and OCR: not measured by this probe. Accessibility measures evidence only.\n"+accuracyReport()
    private data class Sample(val title:String,val duration:Long,val position:Long,val time:Long,val state:Int,val speed:Float)
    private val previous=mutableMapOf<Int,Sample>()
    private fun prefs(c:Context)=c.getSharedPreferences("tracker",Context.MODE_PRIVATE)
    private fun file(c:Context)=File(c.filesDir,"hotstar-title-probe.txt")
    fun enabled(c:Context)=!prefs(c).getBoolean("privacy_lock",false) && System.currentTimeMillis()<prefs(c).getLong(KEY,0)
    @Synchronized fun start(c:Context) { clear(c);prefs(c).edit().putLong(KEY,System.currentTimeMillis()+900000).apply();last=0;lastScreen=0;lastSignals=0;previous.clear() }
    fun stop(c:Context) { prefs(c).edit().remove(KEY).apply() }
    @Synchronized fun clear(c:Context) { file(c).delete();counts.clear();predictions.clear();accuracy.clear() }
    @Synchronized fun report(c:Context):String=if(prefs(c).getBoolean("privacy_lock",false)) "Unlock or disable app privacy lock before accessing this diagnostic." else "JioHotstar title detection diagnostic\nCapture active: "+enabled(c)+"\n"+benchmark()+runCatching { file(c).takeIf { it.exists() }?.readText().orEmpty() }.getOrDefault("")
    private fun safe(value:String):String=HotstarPlayback.clean(value)?.takeIf { !it.contains(Regex("(?i)(https?://|www\\.|@|[0-9]{7,})")) }?.take(160).orEmpty()
    // Fingerprints are diagnostic candidates only, never verified content identities.
    private fun fingerprint(value:String):String {
        if(value.isBlank()) return "none"
        return java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun uriSummary(value:String):String {
        if(value.isBlank()) return "none"
        return runCatching {
            val uri=android.net.Uri.parse(value)
            val segments=uri.pathSegments
            val scheme=uri.scheme?.lowercase(java.util.Locale.ROOT)?.takeIf { it in setOf("http","https","content","file","android.resource") } ?: "other"
            "scheme="+scheme+" hostHash="+fingerprint(uri.host.orEmpty())+" pathHash="+fingerprint(uri.encodedPath.orEmpty())+" segments="+segments.size+" segmentHashes="+segments.take(12).joinToString(",") { fingerprint(it) }+" queryPresent="+(uri.encodedQuery!=null)
        }.getOrDefault("unavailable")
    }
    private fun extraSummary(bundle:android.os.Bundle?):String {
        if(bundle==null) return "none"
        return runCatching {
            val keys=bundle.keySet().sorted()
            val fields=keys.take(80).mapNotNull { key ->
                val normalized=key.substringAfterLast('.').lowercase(java.util.Locale.ROOT).replace("_","").replace("-","")
                if(normalized !in setOf("contentid","videoid","assetid","mediaid","episodeid","showid","seriesid","season","seasonnumber","episode","episodenumber","contenttype","mediatype")) return@mapNotNull null
                val value=bundle.get(key)
                val raw=when(value) { is String -> value; is Int -> value.toString(); is Long -> value.toString(); else -> "" }
                val shown=if(normalized in setOf("season","seasonnumber","episode","episodenumber")) raw.toIntOrNull()?.takeIf { it in 0..10000 }?.toString() ?: "unknown" else if(normalized in setOf("contenttype","mediatype")) raw.lowercase(java.util.Locale.ROOT).takeIf { it in setOf("movie","episode","series","show","tv","live","sports","trailer","ad") } ?: "unknown" else fingerprint(raw)
                normalized+"="+shown
            }
            "keyCount="+keys.size+" keyHashes="+keys.take(80).joinToString(",") { fingerprint(it) }+" candidates="+fields.joinToString(",")
        }.getOrDefault("unavailable")
    }
    @Synchronized fun signals(c:Context,active:Int,tokenControllers:Int,notes:List<android.service.notification.StatusBarNotification>) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastSignals!=0L && now-lastSignals<4000) return
        lastSignals=now
        observe("Media notification present",notes.any { it.notification.category==android.app.Notification.CATEGORY_TRANSPORT || it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) })
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
        runCatching { val f=file(c); if(f.length()>384000) f.writeText(f.readText().takeLast(192000).substringAfter('\n',""));f.appendText(Instant.now().toString()+" "+value.replace('\n',' ').replace('\r',' ')+"\n") }
    }
    private fun statePlaying(state:Int?)=state==android.media.session.PlaybackState.STATE_PLAYING
    @Synchronized fun media(c:Context,controllers:List<MediaController>,notifications:Int) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(last!=0L && now-last<2000) return
        last=now
        append(c,"media sessions="+controllers.size+" notifications="+notifications)
        val activeTokens=controllers.map { it.sessionToken.hashCode() }.toSet()
        previous.keys.retainAll(activeTokens)
        controllers.take(4).forEach { controller ->
            val m=controller.metadata;val p=controller.playbackState
            val title=m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val display=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty()
            val detail=m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
            val duration=m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position=p?.position ?: -1L
            val result=HotstarPlayback.recognize(title,display,detail,duration,position)
            val uri=m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()
            val episode=RecognitionEngine.episodeOf(title)
            val parts=runCatching { android.net.Uri.parse(uri).pathSegments }.getOrDefault(emptyList())
            val candidate=if(episode!=null && parts.size in 8..9) fingerprint(parts[parts.size-6]) else "none"
            if(statePlaying(p?.state) && result!=null) {
                observe("MediaSession accepted title",true)
                if(episode!=null) { observe("MediaSession episode number",true);observe("URI series candidate",candidate!="none");observe("MediaSession secondary title",safe(display).isNotBlank() || safe(detail).isNotBlank()) }
                observe("MediaSession media ID",!m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).isNullOrBlank())
            } else if(statePlaying(p?.state)) observe("MediaSession accepted title",false)
            append(c,"benchmark seriesCandidate="+candidate+" layout="+parts.size+" (unverified)")
            val token=controller.sessionToken.hashCode()
            val state=p?.state ?: -1
            val speed=p?.playbackSpeed ?: 0f
            val prior=previous.put(token,Sample(title,duration,position,now,state,speed))
            val movement=if(prior==null || prior.title!=title || prior.duration!=duration || prior.position<0 || position<0) "new-or-changed" else {
                val elapsed=now-prior.time
                val delta=position-prior.position
                val expected=elapsed*prior.speed.toDouble()
                val kind=when {
                    state!=3 || prior.state!=3 -> "state-transition-or-idle"
                    kotlin.math.abs(delta-expected)>maxOf(2500.0,elapsed*0.5) -> "seek-or-discontinuity"
                    else -> "normal"
                }
                kind+" deltaMs="+delta+" elapsedMs="+elapsed
            }
            append(c,"identity session="+token+" mediaIdHash="+fingerprint(m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty())+" uri="+uriSummary(m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).orEmpty()))
            append(c,"extras controller="+extraSummary(controller.extras)+" playback="+extraSummary(p?.extras))
            append(c,"playback speed="+speed+" movement="+movement+" description="+safe(m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION).orEmpty()))
            append(c,"metadata keys="+m?.keySet()?.filter { it.startsWith("android.media.metadata.") }?.sorted()?.joinToString(",")+" mediaIdPresent="+(!m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).isNullOrBlank())+" mediaUriPresent="+(!m?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI).isNullOrBlank())+" extrasPresent="+(controller.extras!=null))
            append(c,"state="+(p?.state ?: -1)+" positionMs="+position+" durationMs="+duration+" title="+safe(title)+" displayTitle="+safe(display)+" subtitle="+safe(detail)+" decision="+(if(result!=null) "accepted: "+safe(result.title) else "rejected: missing, conflicting or invalid playback evidence"))
        }
    }
    @Synchronized fun screen(c:Context,ids:Set<String>,labels:Set<String> = emptySet(),eventType:Int = -1) {
        if(!enabled(c)) return
        val now=android.os.SystemClock.elapsedRealtime()
        if(lastScreen!=0L && now-lastScreen<2000) return
        lastScreen=now
        observe("Accessibility callback labels",labels.any { safe(it).isNotBlank() })
        append(c,"accessibility eventType="+eventType+" visible control IDs="+ids.take(80).joinToString(",")+" candidate player labels="+labels.map(::safe).filter { it.isNotBlank() }.take(12).joinToString(" | "))
    }
}
