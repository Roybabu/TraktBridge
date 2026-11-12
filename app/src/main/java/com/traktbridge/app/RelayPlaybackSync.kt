package com.traktbridge.app

import android.content.Context
import org.json.JSONArray
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocket

object RelayProtocol {
    fun route(first:String,second:String):String {
        require(first!=second && listOf(first,second).all { it.matches(Regex("[0-9a-f]{64}")) })
        return DeviceIdentity.fingerprint(listOf(first,second).sorted().joinToString(":").toByteArray(Charsets.US_ASCII))
    }
    fun hello(route:String,role:Int,secret:String,nowSeconds:Long,nonce:String):String {
        require(role in 0..1 && route.matches(Regex("[0-9a-f]{64}")) && nonce.matches(Regex("[A-Za-z0-9_-]{22}")))
        val body="TB1 $route $role $nowSeconds $nonce"
        val mac=Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(DevicePairing.decode(secret),"HmacSHA256")) }
        return body+" "+DevicePairing.encode(mac.doFinal(body.toByteArray(Charsets.US_ASCII)))+"\n"
    }
}

/** Optional self-hosted TCP rendezvous; the relay never terminates device TLS. */
class RelayPlaybackSync(context:Context,val config:RelaySettings.Config):java.io.Closeable {
    private val app=context.applicationContext
    private val scheduler=Executors.newSingleThreadScheduledExecutor()
    private val workers=Executors.newFixedThreadPool(8)
    private val running=ConcurrentHashMap.newKeySet<String>()
    private val sockets=ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var closed=false
    private fun allowed()=!closed && PlaybackSharing.enabled(app) && RelaySettings.load(app)==config
    fun start() { scheduler.scheduleWithFixedDelay({ runCatching { cycle() } },0,5,TimeUnit.SECONDS) }
    private fun cycle() {
        if(!allowed()) return
        val identity=DeviceIdentity.register(app)
        DevicePairing.peers(app).sortedBy { it.id }.take(8).forEach { peer ->
            if(running.add(peer.id)) {
                try { workers.execute { try { exchange(identity.id,peer) } finally { running.remove(peer.id) } } }
                catch(_:RejectedExecutionException) { running.remove(peer.id) }
            }
        }
    }
    private fun exchange(local:String,expected:DevicePairing.Peer) {
        runCatching {
            require(allowed())
            val role=if(local<expected.id) 0 else 1
            val route=RelayProtocol.route(local,expected.id)
            val nonce=DevicePairing.encode(ByteArray(16).also { SecureRandom().nextBytes(it) })
            val raw=Socket()
            sockets.add(raw)
            try {
                raw.use {
                    raw.soTimeout=15000; raw.sendBufferSize=65536
                    raw.connect(InetSocketAddress(config.host,config.port),3000)
                    raw.outputStream.write(RelayProtocol.hello(route,role,config.secret,System.currentTimeMillis()/1000,nonce).toByteArray(Charsets.US_ASCII))
                    raw.outputStream.flush()
                    require(raw.inputStream.read()==1 && allowed())
                    val ssl=DeviceTls.context(app)
                    (ssl.socketFactory.createSocket(raw,config.host,config.port,true) as SSLSocket).use { secure ->
                        DeviceTls.configure(secure,role==1)
                        val peer=DeviceTls.authenticate(app,secure)
                        require(peer.id==expected.id && allowed())
                        val outgoing=PlaybackSharing.snapshot(app)
                        for(i in 0 until outgoing.length()) outgoing.optJSONObject(i)?.put("controlMask",0)
                        PlaybackSharing.write(DataOutputStream(secure.outputStream),outgoing)
                        val incoming=PlaybackSharing.read(DataInputStream(secure.inputStream))
                        for(i in 0 until incoming.length()) incoming.optJSONObject(i)?.put("controlMask",0)
                        if(allowed() && DevicePairing.peers(app).any { it.id==peer.id }) RemotePlayback.put(peer,incoming,null)
                        if(allowed()) runCatching {
                            val output=DataOutputStream(secure.outputStream); val input=DataInputStream(secure.inputStream)
                            // Exchange consent first; either side may decline history independently.
                            output.writeBoolean(DeviceHistory.enabled(app)); output.flush()
                            val remoteConsent=input.readBoolean()
                            if(remoteConsent && DeviceHistory.enabled(app) && allowed()) {
                                if(role==0) { DeviceHistory.write(output,DeviceHistory.snapshot(app)); DeviceHistory.merge(app,peer.id,DeviceHistory.read(input)) }
                                else { val records=DeviceHistory.read(input); if(allowed()) DeviceHistory.merge(app,peer.id,records); DeviceHistory.write(output,DeviceHistory.snapshot(app)) }
                            }
                        }
                    }
                }
            } finally { sockets.remove(raw) }
        }
    }
    override fun close() {
        closed=true
        scheduler.shutdownNow(); workers.shutdownNow()
        sockets.forEach { runCatching { it.close() } }; sockets.clear()
    }
}
