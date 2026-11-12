package com.traktbridge.app

/** Conservative reconciliation: nearby non-identical watches require review, never an automatic POST. */
object CrossDeviceUploadPolicy {
    const val WINDOW_SECONDS=120L
    fun nearby(candidateId:Long,targetId:Long,deltaSeconds:Long)=candidateId==targetId && targetId>0 && deltaSeconds in -WINDOW_SECONDS..WINDOW_SECONDS
}
