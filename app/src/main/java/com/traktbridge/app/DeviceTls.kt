package com.traktbridge.app

import android.content.Context
import java.net.Socket
import java.security.Principal
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*

/** Mutual TLS with locally approved public-key pins; never falls back to plaintext or platform CAs. */
object DeviceTls {
    private const val ALIAS="local-device"
    fun approved(context: Context, cert: X509Certificate): DevicePairing.Peer {
        val key=cert.publicKey.encoded
        val id=DeviceIdentity.fingerprint(key)
        return DevicePairing.peers(context).firstOrNull { it.id==id && java.security.MessageDigest.isEqual(it.key,key) }
            ?: throw CertificateException("Unapproved device")
    }
    fun context(context: Context): SSLContext {
        val material=DeviceIdentity.tlsMaterial(context)
        val keys=object: X509ExtendedKeyManager() {
            private fun aliases(type: String?): Array<String>? = if(type?.startsWith("EC")==true) arrayOf(ALIAS) else null
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = aliases(keyType)
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = aliases(keyType)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = if(keyType?.any { aliases(it)!=null }==true) ALIAS else null
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = aliases(keyType)?.first()
            override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = chooseClientAlias(keyType,issuers,null)
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = chooseServerAlias(keyType,issuers,null)
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if(alias==ALIAS) material.second else null
            override fun getPrivateKey(alias: String?): java.security.PrivateKey? = if(alias==ALIAS) material.first else null
        }
        val trust=object: X509TrustManager {
            private fun check(chain: Array<out X509Certificate>?) {
                val cert=chain?.singleOrNull() ?: throw CertificateException("Invalid device certificate")
                approved(context,cert)
            }
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?)=check(chain)
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?)=check(chain)
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(arrayOf(keys),arrayOf(trust),java.security.SecureRandom()) }
    }
    fun configure(socket: SSLSocket, server: Boolean) {
        socket.enabledProtocols=socket.supportedProtocols.filter { it=="TLSv1.2" || it=="TLSv1.3" }.toTypedArray()
        socket.enabledCipherSuites=socket.supportedCipherSuites.filter { it.startsWith("TLS_ECDHE_ECDSA_WITH_AES_") && it.contains("_GCM_") || it.startsWith("TLS_AES_") || it=="TLS_CHACHA20_POLY1305_SHA256" }.toTypedArray()
        socket.useClientMode=!server
        if(server) socket.needClientAuth=true
        socket.soTimeout=3000
    }
    fun authenticate(context: Context, socket: SSLSocket): DevicePairing.Peer {
        socket.startHandshake()
        val cert=socket.session.peerCertificates.firstOrNull() as? X509Certificate ?: throw CertificateException("Missing device certificate")
        return approved(context,cert)
    }
}
