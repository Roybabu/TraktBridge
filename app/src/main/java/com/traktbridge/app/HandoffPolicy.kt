package com.traktbridge.app

object HandoffPolicy {
    /** A refreshed observation must identify the same session and title, not merely the same device. */
    fun sameSession(oldId:String,newId:String,oldTitle:String,newTitle:String,oldDetail:String,newDetail:String,oldPackage:String,newPackage:String):Boolean {
        return DeviceControl.valid(oldId) && oldId==newId && oldTitle.isNotBlank() && oldTitle==newTitle && oldDetail==newDetail && oldPackage==newPackage
    }
    fun estimatedPosition(position:Long,duration:Long,state:Int,age:Long):Long {
        if(!PlaybackPosition.valid(position,duration)) return -1
        // Observed position is not extrapolated: playback speed and buffering are not guaranteed.
        return if(state in setOf(2,3,6,8) && age in 0..PlaybackSharing.TTL_MS) position else -1
    }
}
