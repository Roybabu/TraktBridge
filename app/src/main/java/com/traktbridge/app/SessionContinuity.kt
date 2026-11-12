package com.traktbridge.app

object SessionContinuity {
    const val RESUME_WINDOW_MS=120000L
    // A restart is not evidence of the time spent between observations.
    fun canResume(record:WatchRecord,sample:WatchSample,wall:Long):Boolean {
        if(record.status!="pending" || record.traktId!=0L || record.source!=sample.source || !sample.playing) return false
        if(record.ended<record.started || wall<record.ended || wall-record.ended>RESUME_WINDOW_MS) return false
        if(record.watched<0 || record.title.isBlank() || sample.title.isBlank()) return false
        val oldTitle=RecognitionEngine.normalized(RecognitionEngine.query(record.title))
        if(oldTitle.isBlank() || oldTitle!=RecognitionEngine.normalized(RecognitionEngine.query(sample.title))) return false
        val oldEpisode=RecognitionEngine.episodeOf(record.title+" "+record.subtitle) ?: return false
        if(oldEpisode!=RecognitionEngine.episodeOf(sample.title+" "+sample.subtitle)) return false
        if(!PlaybackPosition.valid(record.position,record.duration) || !PlaybackPosition.valid(sample.position,sample.duration)) return false
        if(record.positionSource!=sample.positionSource || record.positionSource=="unknown") return false
        if(kotlin.math.abs(record.duration-sample.duration)>maxOf(3000L,record.duration/100)) return false
        if(PlaybackAccuracy.nearEnd(record.position,record.duration)) return false
        return sample.position-record.position in -2500L..15000L
    }
    // An observed restart after a near-end session is a suggestion, never completion.
    fun possibleRewatch(previous:WatchRecord,next:WatchRecord):Boolean {
        if(previous.traktId<=0 || previous.traktId!=next.traktId || previous.matchType!=next.matchType || previous.matchType !in setOf("movie","episode")) return false
        if(previous.id==next.id || next.started<previous.ended || next.watched<=0) return false
        return (previous.status=="synced" || PlaybackAccuracy.nearEnd(previous.position,previous.duration)) &&
            PlaybackPosition.valid(next.position,next.duration) && next.position<=next.duration/5
    }
}
