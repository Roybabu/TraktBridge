package com.traktbridge.app

/** Conflicting same-origin IDs cannot silently overwrite received observations. */
object DeviceHistoryConflict {
    enum class Decision { INSERT,UNCHANGED,CONFLICT }
    fun decide(existing:DeviceHistory.Event?,incoming:DeviceHistory.Event):Decision=when {
        existing==null->Decision.INSERT
        existing==incoming->Decision.UNCHANGED
        else->Decision.CONFLICT
    }
}
