package com.traktbridge.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONArray
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.*
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

object RemotePlayback {
    data class Entry(val device: DevicePairing.Peer,val rows: JSONArray,val received: Long,val endpoint: InetSocketAddress?)
    private val entries=ConcurrentHashMap<String,Entry>()
    fun put(peer: DevicePairing.Peer,rows: JSONArray,endpoint: InetSocketAddress?) {
        val now=SystemClock.elapsedRealtime()
        entries.compute(peer.id) { _,old ->
            if(endpoint==null && old?.endpoint!=null && PlaybackSharing.fresh(now-old.received)) old
            else Entry(peer,rows,now,endpoint)
        }
    }
    fun visible(context: Context): List<Entry> {
        if(!PlaybackSharing.enabled(context)) return emptyList()
        val wifi=PlaybackSharing.localWifi(context)
        val relay=RelaySettings.configured(context)
        val ids=DevicePairing.peers(context).map { it.id }.toSet()
        val now=SystemClock.elapsedRealtime()
        entries.entries.removeIf { !PlaybackSharing.fresh(now-it.value.received) || it.key !in ids || (it.value.endpoint==null && !relay) || (it.value.endpoint!=null && !wifi) }
        return entries.values.sortedBy { it.device.label }
    }
    private val commands=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(4))
    fun control(context:Context,entry:Entry,session:String,action:String,done:(Boolean)->Unit) {
        val app=context.applicationContext
        val main=Handler(Looper.getMainLooper())
        val job=Runnable {
            val ok=runCatching {
                require(visible(app).any { it.device.id==entry.device.id } && DeviceControl.valid(session))
                val ssl=DeviceTls.context(app)
                (ssl.socketFactory.createSocket() as SSLSocket).use { socket ->
                    DeviceTls.configure(socket,false); socket.connect(requireNotNull(entry.endpoint),2000)
                    require(DeviceTls.authenticate(app,socket).id==entry.device.id)
                    val current=PlaybackSharing.read(DataInputStream(socket.inputStream))
                    require((0 until current.length()).any { i -> current.optJSONObject(i)?.let { it.optString("sessionId")==session && DeviceControl.permits(it.optInt("controlMask"),action) }==true })
                    val output=DataOutputStream(socket.outputStream); output.writeByte(1); output.writeUTF(session); output.writeUTF(action); output.flush()
                    DataInputStream(socket.inputStream).readBoolean()
                }
            }.getOrDefault(false)
            main.post { done(ok) }
        }
        try { commands.execute(job) } catch(_:RejectedExecutionException) { main.post { done(false) } }
    }
    fun remove(id:String) { entries.remove(id) }
    fun clearLocal() { entries.entries.removeIf { it.value.endpoint!=null } }
    fun clear()=entries.clear()
}

/** User-enabled foreground-service-scoped local synchronization. DNS-SD is only rendezvous. */
class LocalPlaybackSync(context: Context): java.io.Closeable {
    private val app=context.applicationContext
    private val main=Handler(Looper.getMainLooper())
    private val nsd=app.getSystemService(NsdManager::class.java)
    private val ssl=DeviceTls.context(app)
    private val server=ssl.serverSocketFactory.createServerSocket(0) as SSLServerSocket
    private val workers=ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,ArrayBlockingQueue(8))
    private val client=ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,ArrayBlockingQueue(2))
    private val sockets=ConcurrentHashMap.newKeySet<SSLSocket>()
    private val endpoints=linkedMapOf<String,InetSocketAddress>()
    private val pending=ArrayDeque<NsdServiceInfo>()
    private var resolving=false
    private var registered=false
    private var discovering=false
    @Volatile private var closed=false
    private val name="TB-"+UUID.randomUUID().toString().take(12)
    private var actual=name
    private val registration=object:NsdManager.RegistrationListener {
        override fun onServiceRegistered(info:NsdServiceInfo) { main.post { actual=info.serviceName; if(closed) runCatching { nsd.unregisterService(this) } } }
        override fun onRegistrationFailed(info:NsdServiceInfo,code:Int) { main.post { close() } }
        override fun onServiceUnregistered(info:NsdServiceInfo) {}
        override fun onUnregistrationFailed(info:NsdServiceInfo,code:Int) {}
    }
    private fun resolveNext() {
        if(closed || resolving || pending.isEmpty()) return
        val item=pending.removeFirst()
        resolving=true
        val listener=object:NsdManager.ResolveListener {
            override fun onResolveFailed(info:NsdServiceInfo,error:Int) { main.post { resolving=false; resolveNext() } }
            override fun onServiceResolved(info:NsdServiceInfo) { main.post {
                resolving=false
                if(!closed && info.serviceName!=actual && info.serviceName!=name && info.port in 1..65535) {
                    val host=info.host
                    if(host!=null && !host.isAnyLocalAddress && !host.isLoopbackAddress && !host.isMulticastAddress) endpoints[info.serviceName]=InetSocketAddress(host,info.port)
                }
                resolveNext()
            } }
        }
        runCatching { nsd.resolveService(item,listener) }.onFailure { resolving=false; resolveNext() }
    }
    private val discovery=object:NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type:String) {}
        override fun onDiscoveryStopped(type:String) {}
        override fun onStartDiscoveryFailed(type:String,code:Int) { main.post { close() } }
        override fun onStopDiscoveryFailed(type:String,code:Int) {}
        override fun onServiceFound(info:NsdServiceInfo) { main.post {
            if(!closed && DeviceDiscovery.validName(info.serviceName) && info.serviceName!=name && info.serviceName!=actual && pending.size<32 && endpoints.size<32) {
                pending.addLast(info); resolveNext()
            }
        } }
        override fun onServiceLost(info:NsdServiceInfo) { main.post { endpoints.remove(info.serviceName); pending.removeAll { it.serviceName==info.serviceName } } }
    }
    fun isClosed()=closed
    private fun allowed()=!closed && PlaybackSharing.enabled(app) && PlaybackSharing.localWifi(app)
    private val poll=object:Runnable {
        override fun run() {
            if(closed) return
            if(allowed() && DevicePairing.peers(app).isNotEmpty()) {
                val targets=endpoints.values.distinct().take(32)
                if(client.activeCount==0 && client.queue.isEmpty()) runCatching { client.execute {
                    for(target in targets) {
                        if(!allowed()) break
                        runCatching {
                            (ssl.socketFactory.createSocket() as SSLSocket).use { socket ->
                                sockets.add(socket)
                                try {
                                if(!allowed()) return@use
                                DeviceTls.configure(socket,false)
                                socket.connect(target,2000)
                                val peer=DeviceTls.authenticate(app,socket)
                                if(allowed()) {
                                    val rows=PlaybackSharing.read(DataInputStream(socket.inputStream))
                                    if(allowed() && DevicePairing.peers(app).any { it.id==peer.id }) RemotePlayback.put(peer,rows,target)
                                    if(allowed() && DeviceHistory.enabled(app) && DevicePairing.authorized(app,peer.id)) runCatching {
                                        val output=DataOutputStream(socket.outputStream); output.writeByte(2); output.flush()
                                        val history=DeviceHistory.read(DataInputStream(socket.inputStream))
                                        if(allowed()) DeviceHistory.merge(app,peer.id,history)
                                    }
                                }
                                } finally { sockets.remove(socket) }
                            }
                        }
                    }
                } }
            }
            main.postDelayed(this,5000)
        }
    }
    fun start() {
        check(allowed())
        server.needClientAuth=true
        Thread {
            while(!closed) {
                val socket=try { server.accept() as SSLSocket } catch(_:Exception) { break }
                sockets.add(socket)
                if(closed) { sockets.remove(socket); socket.close(); break }
                try { workers.execute {
                    try { socket.use {
                        runCatching {
                            if(!allowed()) return@runCatching
                            DeviceTls.configure(it,true)
                            val peer=DeviceTls.authenticate(app,it)
                            if(allowed() && DevicePairing.authorized(app,peer.id)) {
                                val output=DataOutputStream(it.outputStream)
                                val snapshot=PlaybackSharing.snapshot(app)
                                if(!allowed() || !DevicePairing.authorized(app,peer.id)) return@runCatching
                                PlaybackSharing.write(output,snapshot)
                                val input=DataInputStream(it.inputStream)
                                when(input.readUnsignedByte()) {
                                2 -> if(allowed() && DevicePairing.authorized(app,peer.id)) {
                                    val snapshot=DeviceHistory.snapshot(app)
                                    if(allowed() && DevicePairing.authorized(app,peer.id)) DeviceHistory.write(output,snapshot)
                                }
                                1 -> {
                                    val session=input.readUTF(); val action=input.readUTF()
                                    output.writeBoolean(DeviceControl.execute(app,peer.id,session,action)); output.flush()
                                }
                                }
                            }
                        }
                    } } finally { sockets.remove(socket) }
                } } catch(_:RejectedExecutionException) { sockets.remove(socket); runCatching { socket.close() } }
            }
        }.apply { isDaemon=true; start() }
        try {
            nsd.registerService(NsdServiceInfo().apply { serviceName=name; serviceType=DeviceDiscovery.TYPE; port=server.localPort },NsdManager.PROTOCOL_DNS_SD,registration)
            registered=true
            nsd.discoverServices(DeviceDiscovery.TYPE,NsdManager.PROTOCOL_DNS_SD,discovery)
            discovering=true
            main.post(poll)
        } catch(e:Exception) { close(); throw e }
    }
    override fun close() {
        if(closed) return
        closed=true
        main.removeCallbacks(poll)
        if(discovering) runCatching { nsd.stopServiceDiscovery(discovery) }
        if(registered) runCatching { nsd.unregisterService(registration) }
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }; sockets.clear()
        workers.shutdownNow(); client.shutdownNow()
        endpoints.clear(); pending.clear(); RemotePlayback.clearLocal()
    }
}
