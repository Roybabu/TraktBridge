package com.traktbridge.app
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import java.nio.ByteBuffer

object EncryptedBackup {
    private val magic="TBENC001".toByteArray(Charsets.US_ASCII)
    const val MAX_BYTES=100_000_000
    private fun cipher(mode:Int,password:CharArray,salt:ByteArray,iv:ByteArray):Cipher {
        require(password.size>=10) { "Use at least ten characters" }
        val spec=PBEKeySpec(password,salt,600000,256)
        val raw=try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try { Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode,SecretKeySpec(raw,"AES"),GCMParameterSpec(128,iv)); updateAAD(magic) } } finally { raw.fill(0) }
    }
    fun encrypt(text:String,password:CharArray):ByteArray {
        require(text.length<=HistoryBackup.MAX_CHARS)
        val salt=ByteArray(16); val iv=ByteArray(12); val random=SecureRandom(); random.nextBytes(salt); random.nextBytes(iv)
        val input=text.toByteArray(Charsets.UTF_8)
        val encrypted=try { cipher(Cipher.ENCRYPT_MODE,password,salt,iv).doFinal(input) } finally { input.fill(0) }
        return magic+salt+iv+encrypted
    }
    fun decrypt(data:ByteArray,password:CharArray):String {
        require(data.size in 52..MAX_BYTES && data.copyOfRange(0,8).contentEquals(magic)) { "Invalid encrypted backup" }
        val plain=cipher(Cipher.DECRYPT_MODE,password,data.copyOfRange(8,24),data.copyOfRange(24,36)).doFinal(data.copyOfRange(36,data.size))
        return try { String(plain,Charsets.UTF_8).also { require(it.length<=HistoryBackup.MAX_CHARS) } } finally { plain.fill(0) }
    }
}
