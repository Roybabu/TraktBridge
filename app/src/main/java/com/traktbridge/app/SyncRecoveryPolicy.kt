package com.traktbridge.app
object SyncRecoveryPolicy {
    fun afterLookup(status:String,exact:Boolean,verified:Boolean):String = when {
        exact -> "synced"
        !verified -> status
        status=="uncertain" -> "uncertain"
        status=="queued" -> "queued"
        else -> status
    }
    fun batchSize(value:Int):Int=value.coerceIn(1,100)
}
