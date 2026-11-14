package com.traktbridge.app

object RecoveryRetryPolicy {
    fun delay(attempts:Int):Long=when {
        attempts<=0 -> 0L
        attempts==1 -> 15000L
        attempts==2 -> 30000L
        attempts==3 -> 60000L
        else -> 120000L
    }
}
