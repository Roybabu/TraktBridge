package com.traktbridge.app
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

object RelaySettings {
    private const val ALIAS="traktbridge.relay.v1"
    data class Config(val host:String,val port:Int,val secret:String)
    fun valid(config:Config):Boolean =
        config.host.length in 1..253 && config.host.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*[A-Za-z0-9]|[A-Za-z0-9]")) &&
        config.port in 1..65535 && config.secret.matches(Regex("[A-Za-z0-9_-]{43}")) &&
        runCatching { Base64.getUrlDecoder().decode(config.secret).size==32 }.getOrDefault(false)
    private fun prefs(context:Context)=context.getSharedPreferences("device_relay",Context.MODE_PRIVATE)
    @Synchronized private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(ALIAS,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    fun save(context:Context,config:Config) {
        require(valid(config))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val data=JSONObject().put("host",config.host).put("port",config.port).put("secret",config.secret).toString().toByteArray(Charsets.UTF_8)
        val blob=DevicePairing.encode(cipher.iv)+"."+DevicePairing.encode(cipher.doFinal(data))
        check(prefs(context).edit().putString("encrypted_config",blob).commit())
    }
    fun load(context:Context):Config? = runCatching {
        val blob=prefs(context).getString("encrypted_config",null) ?: return null
        val parts=blob.split(".")
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,DevicePairing.decode(parts[0])))
        val data=JSONObject(cipher.doFinal(DevicePairing.decode(parts[1])).toString(Charsets.UTF_8))
        Config(data.getString("host"),data.getInt("port"),data.getString("secret")).takeIf { valid(it) }
    }.getOrNull()
    fun configured(context:Context)=prefs(context).contains("encrypted_config")
    fun disable(context:Context) { check(prefs(context).edit().clear().commit()) }
}
