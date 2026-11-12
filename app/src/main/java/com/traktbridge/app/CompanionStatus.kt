package com.traktbridge.app

object ApiRetryPolicy {
    fun delay(status:Int,attempt:Int,retryAfterSeconds:Long?):Long? {
        if(attempt !in 0..1 || status !in setOf(408,429,500,502,503,504)) return null
        val seconds=retryAfterSeconds ?: (1L shl attempt)
        return if(seconds in 0..5) seconds.coerceAtLeast(1)*1000 else null
    }
}
data class CompanionSummary(val active:Int,val pending:Int,val watchedMs:Long)
object CompanionStatus {
    fun summarize(records:List<WatchRecord>):CompanionSummary {
        val unique=records.distinctBy { it.id }
        return CompanionSummary(unique.count { it.status=="active" },unique.count { it.status in setOf("pending","reviewed","queued","uncertain","blocked") },unique.sumOf { it.watched.coerceAtLeast(0) })
    }
}
