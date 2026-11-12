package com.traktbridge.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(AndroidJUnit4::class)
class CrossDeviceIntegrationTest {
    @Test fun signedPairingHistoryAndRevocation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val tracker=context.getSharedPreferences("tracker",0)
        val keys=listOf("enabled","device_sharing","device_history")
        val prior=keys.associateWith { if(tracker.contains(it)) tracker.getBoolean(it,false) else null }
        val pair=KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val now=System.currentTimeMillis()
        val nonce=DevicePairing.encode(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) })
        val body=listOf("TB1",DevicePairing.encode(pair.public.encoded),DevicePairing.encode("Test peer".toByteArray()),(now+120000).toString(),nonce).joinToString(".")
        val sig=Signature.getInstance("SHA256withECDSA").run { initSign(pair.private); update(body.toByteArray(Charsets.US_ASCII)); sign() }
        val token=body+"."+DevicePairing.encode(sig)
        val pin=DevicePairing.verificationCode(token)
        val peer=DevicePairing.verify(token,pin,now)
        assertTrue(runCatching { DevicePairing.verify(token,pin,now+121000) }.isFailure)
        assertTrue(runCatching { DevicePairing.verify(token,if(pin=="00000000") "11111111" else "00000000",now) }.isFailure)
        val event=DeviceHistory.Event(java.util.UUID.randomUUID().toString(),"instrumentation","Cross device test "+nonce,"S1 E1",now-60000,now,50000,120000,50000)
        try {
            tracker.edit().apply { keys.forEach { putBoolean(it,true) } }.commit()
            DevicePairing.approve(context,peer)
            assertTrue(DevicePairing.authorized(context,peer.id))
            assertTrue(runCatching { DevicePairing.approve(context,peer) }.isFailure)
            DeviceHistory.merge(context,peer.id,listOf(event,event))
            val db=WatchHistory(context)
            try {
                assertNull(db.record(event.id))
                assertFalse(db.syncQueue().any { it.id==event.id })
                db.readableDatabase.rawQuery("SELECT count(*) FROM device_history WHERE peer=? AND event_id=?",arrayOf(peer.id,event.id)).use { c -> assertTrue(c.moveToFirst()); assertEquals(1,c.getInt(0)) }
                assertTrue(HistoryBackup.integrity(db))
            } finally { db.close() }
            assertEquals(1,DeviceHistory.visible(context).count { it.event.id==event.id })
            DevicePairing.revoke(context,peer.id)
            assertFalse(DevicePairing.authorized(context,peer.id))
            assertFalse(DeviceHistory.visible(context).any { it.event.id==event.id })
            DeviceHistory.clear(context,peer.id)
            DeviceHistory.merge(context,peer.id,listOf(event))
            val after=WatchHistory(context)
            try {
                after.readableDatabase.rawQuery("SELECT count(*) FROM device_history WHERE peer=?",arrayOf(peer.id)).use { c -> assertTrue(c.moveToFirst()); assertEquals(0,c.getInt(0)) }
            } finally { after.close() }
        } finally {
            DevicePairing.revoke(context,peer.id)
            DeviceHistory.clear(context,peer.id)
            tracker.edit().apply { prior.forEach { (key,value) -> if(value==null) remove(key) else putBoolean(key,value) } }.commit()
        }
    }
}
