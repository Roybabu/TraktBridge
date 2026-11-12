package com.traktbridge.app

object MigrationReadiness {
    fun eligible(active:Int,queued:Int,uncertain:Int,blocked:Int,integrity:Boolean):Boolean =
        integrity && listOf(active,queued,uncertain,blocked).all { it==0 }
    fun message(active:Int,queued:Int,uncertain:Int,blocked:Int,integrity:Boolean):String =
        if(!integrity) "Database integrity check failed; do not migrate."
        else if(!eligible(active,queued,uncertain,blocked,integrity)) "Resolve active playback and outstanding synchronization before migration."
        else "Database is ready for an encrypted backup. Full SQLite encryption is not installed."
}
