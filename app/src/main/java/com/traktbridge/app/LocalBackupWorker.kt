package com.traktbridge.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import javax.crypto.spec.GCMParameterSpec

object LocalBackupVault {
    private const val ALIAS="traktbridge-local-backup-v1"
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun file(context:Context)=File(context.filesDir,"local-history-backup.enc")
    @Synchronized fun save(context:Context) {
        val db=WatchHistory(context)
        val json=try { require(HistoryBackup.integrity(db)) { "Database integrity check failed; existing backup retained" }; HistoryBackup.snapshot(db).toString() } finally { db.close() }
        require(json.length<=HistoryBackup.MAX_CHARS)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val data=cipher.iv+cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        require(data.size<=EncryptedBackup.MAX_BYTES) { "Backup exceeds encrypted size limit" }
        val verify=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))) }
        check(String(verify.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8)==json)
        val atomic=android.util.AtomicFile(file(context))
        val stream=atomic.startWrite()
        try { stream.write(data); atomic.finishWrite(stream) } catch(e:Exception) { atomic.failWrite(stream); throw e }
    }
    @Synchronized fun read(context:Context):String {
        val f=file(context)
        val data=android.util.AtomicFile(f).openRead().use { input ->
            val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true) { val n=input.read(buffer); if(n<0) break; require(out.size()+n<=EncryptedBackup.MAX_BYTES); out.write(buffer,0,n) }; out.toByteArray()
        }
        require(data.size>=29)
        require(data.size<=EncryptedBackup.MAX_BYTES)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,data.copyOfRange(0,12))) }
        return String(cipher.doFinal(data.copyOfRange(12,data.size)),Charsets.UTF_8)
    }
    fun exists(context:Context)=file(context).exists()
    @Synchronized fun delete(context:Context) { android.util.AtomicFile(file(context)).delete() }
    fun schedule(context:Context,enable:Boolean) {
        val wm=WorkManager.getInstance(context)
        if(enable) wm.enqueueUniquePeriodicWork("local-history-backup",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<LocalBackupWorker>(24,TimeUnit.HOURS).build())
        else wm.cancelUniqueWork("local-history-backup")
    }
}
class LocalBackupWorker(context:Context,params:WorkerParameters):Worker(context,params) {
    override fun doWork():Result {
        if(!applicationContext.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("daily_backup",false)) return Result.success()
        return try { LocalBackupVault.save(applicationContext); Result.success() } catch(_:Exception) { Result.retry() }
    }
}
