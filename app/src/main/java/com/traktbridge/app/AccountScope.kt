package com.traktbridge.app
import java.security.MessageDigest
object AccountScope {
    fun key(account:String):String {
        require(account.isNotBlank() && account.length<=512)
        return MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun cacheKey(account:String,event:Long):String { require(event>0); return key(account)+":"+event }
}
