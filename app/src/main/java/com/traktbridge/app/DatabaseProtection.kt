package com.traktbridge.app
/** Live encryption remains disabled until an audited lossless migration can preserve every table and approval. */
object DatabaseProtection {
    fun canPrepare(active:Int,queued:Int,uncertain:Int,blocked:Int,integrity:Boolean)=integrity && listOf(active,queued,uncertain,blocked).all { it==0 }
    const val LIVE_ENCRYPTED=false
}
