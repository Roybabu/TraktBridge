package com.traktbridge.app
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Protect downloaded remote history independently of the local SQLite database.
object PrivateHistoryCache {
    private const val ALIAS="traktbridge.remote.cache.v1"
    @Volatile private var cachedKey:SecretKey?=null
    @Synchronized private fun key():SecretKey {
        cachedKey?.let { return it }
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val loaded=(store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        cachedKey=loaded
        return loaded
    }
    fun seal(value:String,scope:String):String {
        require(value.length<=16384)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key()); cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
    }
    fun open(value:String,scope:String):String {
        require(value.length<=100000)
        val bytes=Base64.decode(value,Base64.NO_WRAP); require(bytes.size>=28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12))); cipher.updateAAD(scope.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }
}
