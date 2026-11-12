package com.traktbridge.app

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.HttpsURLConnection

class TraktAuth(context: Context) {
    companion object {
        const val CLIENT_ID = "0A3pV4EV2fwE7m_3RIwxvqxhjOCsg87IHl0bNIeUaY8"
        const val REDIRECT = "traktbridge://callback"
        private const val KEY = "traktbridge.oauth.v1"
        private val executor = Executors.newSingleThreadExecutor()
        private val lock = Any()
    }
    private val prefs = context.getSharedPreferences("oauth", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    private fun random() = b64(ByteArray(32).also { SecureRandom().nextBytes(it) })
    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey(KEY, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun store(name: String, value: JSONObject) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secret())
        val blob = b64(c.iv) + "." + b64(c.doFinal(value.toString().toByteArray(Charsets.UTF_8)))
        check(prefs.edit().putString(name, blob).commit())
    }
    private fun load(name: String): JSONObject? {
        val blob = prefs.getString(name, null) ?: return null
        val parts = blob.split(".")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.URL_SAFE)))
        return JSONObject(String(c.doFinal(Base64.decode(parts[1], Base64.URL_SAFE)), Charsets.UTF_8))
    }
    fun status(): String = if (prefs.contains("tokens")) "Trakt authorization saved — check connection" else "Trakt not connected"
    fun begin(): Uri = synchronized(lock) {
        val verifier = random()
        val state = random()
        store("pending", JSONObject().put("verifier", verifier).put("state", state).put("expires", System.currentTimeMillis() + 600000))
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        Uri.parse("https://auth.trakt.tv/oauth/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256").build()
    }
    private fun task(done: (String) -> Unit, work: () -> String) {
        executor.execute {
            val message = try { work() } catch (e: OAuthFailure) { if (e.invalidGrant) "Authorization expired or invalid. Connect Trakt again." else "Trakt request failed (HTTP " + e.status + ")." }
                catch (e: Exception) { DebugTrace.failure("oauth",e); "Trakt connection failed. Check your internet connection or reconnect." }
            main.post { done(message) }
        }
    }
    fun complete(uri: Uri, done: (String) -> Unit) = task(done) {
        if (uri.scheme != "traktbridge" || uri.host != "callback" || !uri.path.isNullOrEmpty()) return@task "Invalid callback."
        val pending = synchronized(lock) {
            val p = load("pending") ?: return@synchronized null
            if (p.optLong("expires") < System.currentTimeMillis() || p.optString("state") != uri.getQueryParameter("state")) return@synchronized null
            prefs.edit().remove("pending").commit()
            p
        } ?: return@task "Sign-in expired or state mismatch. Connect again."
        if (uri.getQueryParameter("error") != null) return@task "Trakt authorization cancelled or denied."
        val code = uri.getQueryParameter("code") ?: return@task "Missing authorization code."
        synchronized(lock) {
            if(prefs.contains("pending")) return@task "A newer sign-in is pending. Complete that sign-in instead."
            val response = token(mapOf("grant_type" to "authorization_code", "code" to code, "client_id" to CLIENT_ID, "redirect_uri" to REDIRECT, "code_verifier" to pending.getString("verifier")))
            saveTokens(response)
        }
        "Trakt connected. Use Check Trakt connection to verify."
    }
    private fun saveTokens(response: JSONObject) {
        require(response.getString("access_token").isNotBlank() && response.getString("refresh_token").isNotBlank())
        val seconds = response.getLong("expires_in")
        require(seconds > 0)
        response.put("expiresAt", System.currentTimeMillis() + seconds * 1000)
        store("tokens", response)
    }
    internal fun <T> authorized(work: (String) -> T): T = synchronized(lock) { work(accessToken()) }
    private fun accessToken(): String {
        val saved = synchronized(lock) { load("tokens") } ?: error("Not connected")
        if (saved.getLong("expiresAt") > System.currentTimeMillis() + 60000) return saved.getString("access_token")
        val fresh = try { token(mapOf("grant_type" to "refresh_token", "client_id" to CLIENT_ID, "refresh_token" to saved.getString("refresh_token"))) } catch (e: OAuthFailure) {
            if (e.invalidGrant) synchronized(lock) { prefs.edit().remove("tokens").commit() }
            throw e
        }
        synchronized(lock) { saveTokens(fresh) }
        return fresh.getString("access_token")
    }
    fun check(done: (String) -> Unit) = task(done) {
        if (!prefs.contains("tokens")) return@task "Trakt not connected"
        val c = URL("https://api.trakt.tv/users/settings").openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.setRequestProperty("Authorization", "Bearer " + accessToken())
            c.setRequestProperty("trakt-api-key", CLIENT_ID)
            c.setRequestProperty("trakt-api-version", "2")
            c.setRequestProperty("Accept", "application/json")
            val status = c.responseCode
            DebugTrace.event("oauth","HTTP "+status)
            if (status !in 200..299) throw OAuthFailure(status, status == 401)
            val user = JSONObject(boundedText(c.inputStream.bufferedReader())).optJSONObject("user")
            val name = user?.optString("username").orEmpty()
            if (name.isBlank()) "Trakt connection verified" else "Connected to Trakt as " + name
        } finally { c.disconnect() }
    }
    fun disconnect(done: (String) -> Unit) = task(done) {
        synchronized(lock) { prefs.edit().clear().commit() }
        "Trakt disconnected locally. Revoke access in Trakt settings if desired."
    }
    private class OAuthFailure(val status: Int, val invalidGrant: Boolean): Exception()
    private fun boundedText(reader:java.io.Reader):String = reader.use {
        val buffer=CharArray(4096); val out=StringBuilder()
        while(true) { val n=it.read(buffer); if(n<0) break; require(out.length+n<=1_000_000) { "OAuth response too large" }; out.append(buffer,0,n) }
        out.toString()
    }
    private fun token(fields: Map<String, String>): JSONObject {
        val c = URL("https://auth.trakt.tv/oauth/token").openConnection() as HttpsURLConnection
        try {
            c.requestMethod = "POST"; c.doOutput = true; c.instanceFollowRedirects = false
            c.connectTimeout = 15000; c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.setRequestProperty("Accept", "application/json")
            val body = fields.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            DebugTrace.event("oauth","HTTP "+status)
            if (status !in 200..299) {
                val error = c.errorStream?.bufferedReader()?.let { boundedText(it) }.orEmpty()
                val invalid = runCatching { JSONObject(error).optString("error") == "invalid_grant" }.getOrDefault(false)
                throw OAuthFailure(status, invalid)
            }
            return JSONObject(boundedText(c.inputStream.bufferedReader()))
        } finally { c.disconnect() }
    }
}
