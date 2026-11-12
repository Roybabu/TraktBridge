package com.traktbridge.app

import android.os.SystemClock

object StartupTiming {
    fun elapsed(start:Long,end:Long)= (end-start).coerceAtLeast(0)
    fun report(start:Long,stage:String) { DebugTrace.event("startup",stage+" elapsedMs="+elapsed(start,SystemClock.elapsedRealtime())) }
}
