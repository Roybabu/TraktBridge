package com.traktbridge.app

import android.content.Context
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale

/** Invitations pin an identity; live authentication must separately prove possession of its key. */
object DevicePairing {
    private const val TTL = 120000L
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    data class Peer(val id: String, val key: ByteArray, val label: String)
    fun encode(bytes: ByteArray): String = encoder.encodeToString(bytes)
    fun decode(value: String): ByteArray = decoder.decode(value)
    fun verificationCode(token: String): String {
        val hash=MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.US_ASCII))
        val value=java.nio.ByteBuffer.wrap(hash).int.toLong() and 0xffffffffL
        return String.format(Locale.ROOT,"%08d",value%100000000L)
    }
    fun invite(context: Context, now: Long=System.currentTimeMillis()): String {
        val device=DeviceIdentity.register(context)
        val nonce=ByteArray(16).also { SecureRandom().nextBytes(it) }
        val body=listOf("TB1",encode(device.publicKey),encode(device.label.toByteArray(Charsets.UTF_8)),(now+TTL).toString(),encode(nonce)).joinToString(".")
        return body+"."+encode(DeviceIdentity.sign(context,body.toByteArray(Charsets.US_ASCII)))
    }
    fun verify(token: String, code: String, now: Long=System.currentTimeMillis()): Peer {
        require(token.length in 40..2048 && code.matches(Regex("[0-9]{8}")))
        require(MessageDigest.isEqual(verificationCode(token).toByteArray(),code.toByteArray()))
        val fields=token.split(".")
        require(fields.size==6 && fields[0]=="TB1")
        val expires=fields[3].toLong()
        require(expires>=now && expires-now<=TTL+30000)
        val key=decode(fields[1])
        require(key.size in 80..150 && decode(fields[4]).size==16)
        val label=decode(fields[2]).toString(Charsets.UTF_8)
        require(label.length in 1..80 && label.none { it.isISOControl() })
        val publicKey=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(key)) as java.security.interfaces.ECPublicKey
        require(publicKey.params.curve.field.fieldSize==256)
        val signature=Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey)
        signature.update(fields.take(5).joinToString(".").toByteArray(Charsets.US_ASCII))
        require(signature.verify(decode(fields[5])))
        return Peer(DeviceIdentity.fingerprint(key),key,label)
    }
    @Synchronized fun approve(context: Context, peer: Peer) {
        require(peer.id==DeviceIdentity.fingerprint(peer.key) && peer.id!=DeviceIdentity.register(context).id)
        val prefs=context.getSharedPreferences("paired_devices",Context.MODE_PRIVATE)
        require(prefs.contains(peer.id) || prefs.all.size<32)
        check(prefs.edit().putString(peer.id,encode(peer.key)+"."+encode(peer.label.toByteArray(Charsets.UTF_8))).commit())
    }
    fun peers(context: Context): List<Peer> =
        context.getSharedPreferences("paired_devices",Context.MODE_PRIVATE).all.mapNotNull { (id,value) ->
            runCatching {
                val parts=(value as String).split(".")
                val key=decode(parts[0])
                require(id==DeviceIdentity.fingerprint(key))
                Peer(id,key,decode(parts[1]).toString(Charsets.UTF_8))
            }.getOrNull()
        }
}
