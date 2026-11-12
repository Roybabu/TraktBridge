package com.traktbridge.app

enum class PlaybackPhase { PLAYING, PAUSED, BUFFERING, STOPPED, UNKNOWN }
data class PlaybackInterval(val watched:Long=0,val paused:Long=0,val buffering:Long=0,val uncertain:Long=0,val seeks:Int=0,val replay:Int=0)
object PlaybackAccuracy {
    const val MAX_GAP=12000L
    // Do not extrapolate across missing observations or state transitions.
    fun interval(old:WatchSample,next:WatchSample,elapsed:Long):PlaybackInterval {
        if(elapsed !in 1..MAX_GAP) return PlaybackInterval()
        if(SegmentDetection.excluded(old.segment) || SegmentDetection.excluded(next.segment)) return PlaybackInterval(uncertain=elapsed)
        if(old.phase==PlaybackPhase.BUFFERING || next.phase==PlaybackPhase.BUFFERING) return PlaybackInterval(buffering=elapsed)
        if(old.phase==PlaybackPhase.PAUSED && next.phase==PlaybackPhase.PAUSED) return PlaybackInterval(paused=elapsed)
        if(old.phase!=PlaybackPhase.PLAYING || next.phase!=PlaybackPhase.PLAYING) return PlaybackInterval(uncertain=elapsed)
        val comparable=PlaybackPosition.valid(old.position,old.duration) && PlaybackPosition.valid(next.position,next.duration) &&
            kotlin.math.abs(old.duration-next.duration)<=maxOf(3000L,old.duration/100) && old.positionSource==next.positionSource && old.positionSource!="unknown"
        if(!comparable) return PlaybackInterval(watched=elapsed)
        val movement=next.position-old.position
        val expected=elapsed*old.speed.coerceIn(0.25f,4f)
        // A seek or rewind is not viewing time. Allow timer quantization and sampling jitter.
        if(movement < -2500 || movement > expected+maxOf(5000f,expected)) return PlaybackInterval(uncertain=elapsed,seeks=1,replay=if(movement<0) 1 else 0)
        // Repeated/stale UI snapshots cannot demonstrate continued progress. For media/server
        // positions that remain stationary, keep the interval uncertain rather than inflating time.
        if(movement<=0 && next.positionSource!="screen") return PlaybackInterval(uncertain=elapsed)
        return PlaybackInterval(watched=elapsed)
    }
    fun compatibleIdentity(old:WatchSample,next:WatchSample):Boolean {
        if(old.title.isBlank() || next.title.isBlank()) return true
        if(RecognitionEngine.normalized(RecognitionEngine.query(old.title))!=RecognitionEngine.normalized(RecognitionEngine.query(next.title))) return false
        val a=RecognitionEngine.episodeOf(old.title+" "+old.subtitle)
        val b=RecognitionEngine.episodeOf(next.title+" "+next.subtitle)
        return a==null || b==null || a==b
    }
    fun mergeIdentity(old:WatchSample,next:WatchSample):WatchSample {
        val title=next.title.ifBlank { old.title }
        val detail=if(next.title.isBlank() || next.subtitle.isBlank()) old.subtitle else next.subtitle
        return next.copy(title=title,subtitle=detail)
    }
    fun nearEnd(position:Long,duration:Long):Boolean =
        PlaybackPosition.valid(position,duration) && position.toDouble()/duration>=0.90
}
