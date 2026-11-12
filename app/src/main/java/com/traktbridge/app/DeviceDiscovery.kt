package com.traktbridge.app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.net.ServerSocket
import java.util.UUID

/** Opt-in DNS-SD rendezvous. Advertisements are not authenticated identities. */
class DeviceDiscovery(context: Context, private val changed: (List<String>) -> Unit) : java.io.Closeable {
    companion object {
        const val TYPE = "_traktbridge._tcp."
        internal fun validName(name: String) = name.startsWith("TB-") && name.length in 4..63
    }
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val names = linkedSetOf<String>()
    private val local = "TB-" + UUID.randomUUID().toString().take(12)
    private var actualName = local
    private var socket: ServerSocket? = null
    private var closed = false
    private var registered = false
    private var discovering = false
    private fun update() { changed(names.filter { it != actualName && it != local }.sorted()) }
    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { main.post { actualName=info.serviceName; if(closed) runCatching { nsd.unregisterService(this) }; update() } }
        override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { main.post { if(!closed) changed(emptyList()) } }
        override fun onServiceUnregistered(info: NsdServiceInfo) {}
        override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
    }
    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { if(!closed) changed(emptyList()) } }
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
        override fun onServiceFound(info: NsdServiceInfo) { main.post { if(!closed && validName(info.serviceName) && names.size < 100) { names.add(info.serviceName); update() } } }
        override fun onServiceLost(info: NsdServiceInfo) { main.post { if(!closed) { names.remove(info.serviceName); update() } } }
    }
    fun start() {
        check(!closed && socket==null)
        // Until pairing is implemented, accept no application data.
        val listener=ServerSocket(0)
        socket=listener
        Thread { while(!listener.isClosed) { try { listener.accept().close() } catch(_:java.io.IOException) { break } } }.apply { isDaemon=true; start() }
        try {
            nsd.registerService(NsdServiceInfo().apply { serviceName=local; serviceType=TYPE; port=listener.localPort },NsdManager.PROTOCOL_DNS_SD,registration)
            registered=true
            nsd.discoverServices(TYPE,NsdManager.PROTOCOL_DNS_SD,discovery)
            discovering=true
        } catch(e: Exception) { close(); throw e }
    }
    override fun close() {
        if(closed) return
        closed=true
        if(discovering) runCatching { nsd.stopServiceDiscovery(discovery) }
        if(registered) runCatching { nsd.unregisterService(registration) }
        runCatching { socket?.close() }
        names.clear()
    }
}
