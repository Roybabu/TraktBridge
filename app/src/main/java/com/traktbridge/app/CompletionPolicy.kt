package com.traktbridge.app

data class CompletionAssessment(val percent:Int?,val suggested:Boolean,val reason:String)
object CompletionPolicy {
    const val DEFAULT_THRESHOLD=90
    fun threshold(value:Int):Int=value.coerceIn(75,100)
    fun assess(record:WatchRecord,setting:Int=DEFAULT_THRESHOLD):CompletionAssessment {
        val target=threshold(setting)
        if(record.positionSource !in setOf("media","server","screen") || !PlaybackPosition.valid(record.position,record.duration)) return CompletionAssessment(null,false,"Verified playback position unavailable")
        val percent=(record.position.toDouble()/record.duration*100).toInt().coerceIn(0,100)
        if(record.status !in setOf("pending","reviewed")) return CompletionAssessment(percent,false,"Session is active or already processed")
        if(record.traktId<=0 || record.matchType !in setOf("movie","episode")) return CompletionAssessment(percent,false,"Identify exact title first")
        if(record.matchType=="episode" && (record.showId<=0 || record.season<0 || record.episode<=0)) return CompletionAssessment(percent,false,"Episode identity incomplete")
        if(record.watched<minOf(120000L,record.duration/4)) return CompletionAssessment(percent,false,"Insufficient sampled playback")
        if(record.catalogueRuntime>=60000 && kotlin.math.abs(record.duration-record.catalogueRuntime)>maxOf(180000L,record.catalogueRuntime/10)) return CompletionAssessment(percent,false,"Player and catalogue runtime differ")
        if(percent<target) return CompletionAssessment(percent,false,"Below the "+target+"% review threshold")
        if(record.watched.toDouble()/record.duration<target/100.0*0.5) return CompletionAssessment(percent,false,"Position alone does not establish sufficient viewing")
        return CompletionAssessment(percent,true,"Playback evidence meets review threshold; confirmation required")
    }
}
