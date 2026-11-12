package com.traktbridge.app

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest

object DeviceIdentity {
    private const val ALIAS = "traktbridge.device.identity.v1"
    data class Registration(val id: String, val label: String, val publicKey: ByteArray)
    internal fun fingerprint(key: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(key).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Synchronized fun register(context: Context): Registration {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
            }.generateKeyPair()
        }
        val key = requireNotNull(store.getCertificate(ALIAS)).publicKey.encoded
        val prefs = context.getSharedPreferences("device_registration", Context.MODE_PRIVATE)
        val label = prefs.getString("label", null) ?: Build.MODEL.take(80)
        return Registration(fingerprint(key), label, key)
    }
    @Synchronized fun rename(context: Context, label: String) {
        val clean = label.trim()
        require(clean.isNotEmpty() && clean.length <= 80 && clean.none { it.isISOControl() })
        check(context.getSharedPreferences("device_registration", Context.MODE_PRIVATE).edit().putString("label", clean).commit())
    }
}
