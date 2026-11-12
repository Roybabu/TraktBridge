package com.traktbridge.app

object DevicePlaybackDisplay {
    fun state(value: Int): String=when(value) {
        3 -> "Playing"
        2 -> "Paused"
        6,8 -> "Buffering or connecting"
        else -> "Playback state unavailable"
    }
    fun progress(position: Long,duration: Long): Int? =
        if(duration>0 && position in 0..duration) (position.toDouble()/duration*100).toInt().coerceIn(0,100) else null
}
