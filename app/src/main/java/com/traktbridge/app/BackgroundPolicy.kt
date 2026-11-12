package com.traktbridge.app
object BackgroundPolicy {
    fun delay(enabled:Boolean,playing:Boolean,failures:Int):Long = when {
        !enabled -> 30000L
        failures>0 -> (5000L shl failures.coerceIn(1,3)).coerceAtMost(40000L)
        playing -> 5000L
        else -> 10000L
    }
}
