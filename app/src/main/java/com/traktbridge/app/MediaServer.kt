package com.traktbridge.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import java.net.URL
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.Executors

data class ServerConfig(val kind:String,val url:String,val token:String,val client:String)
object ServerStore {
    private const val KEY="traktbridge-media-server-v1"
    private fun secret():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey(KEY,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    fun validate(c:ServerConfig) {
        require(c.kind in setOf("plex","jellyfin","kodi","emby") && c.client.isNotBlank() && c.client.length<=200)
        val u=URI(c.url)
        require(u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null && u.query==null && u.fragment==null) { "Use an HTTPS server base URL with no credentials or query" }
        require(c.token.length<=4096 && !c.token.contains('\n') && !c.token.contains('\r'))
        require(c.kind=="kodi" || c.token.isNotBlank()) { "An API token is required" }
    }
    @Synchronized fun save(context:Context,c:ServerConfig) {
        validate(c)
        val data=JSONObject().put("kind",c.kind).put("url",c.url.trimEnd('/')).put("token",c.token).put("client",c.client).toString()
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,secret())
        val value=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        context.getSharedPreferences("media_servers",Context.MODE_PRIVATE).edit().putString(c.kind,value).apply()
    }
    @Synchronized fun load(context:Context,kind:String):ServerConfig? = runCatching {
        val value=context.getSharedPreferences("media_servers",Context.MODE_PRIVATE).getString(kind,null) ?: return null
        val parts=value.split(':',limit=2)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        val o=JSONObject(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))
        ServerConfig(kind,o.getString("url"),o.getString("token"),o.getString("client"))
    }.getOrNull()
    fun remove(context:Context,kind:String) { context.getSharedPreferences("media_servers",Context.MODE_PRIVATE).edit().remove(kind).apply() }
}
object ServerParsing {
    private fun objects(a:JSONArray?)=(0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }
    fun plex(body:JSONObject,client:String):List<WatchSample> = objects(body.optJSONObject("MediaContainer")?.optJSONArray("Metadata")).mapNotNull { m ->
        val p=m.optJSONObject("Player") ?: return@mapNotNull null
        if(p.optString("machineIdentifier")!=client) return@mapNotNull null
        val type=m.optString("type")
        if(type !in setOf("movie","episode")) return@mapNotNull null
        val id=m.optString("ratingKey"); if(id.isBlank()) return@mapNotNull null
        val title=if(type=="episode") m.optString("grandparentTitle") else m.optString("title")
        if(title.isBlank()) return@mapNotNull null
        val detail=if(type=="episode") "S"+m.optInt("parentIndex")+" E"+m.optInt("index")+" • "+m.optString("title") else ""
        val state=p.optString("state")
        if(state !in setOf("playing","paused","buffering")) return@mapNotNull null
        val duration=m.optLong("duration",0); val position=PlaybackPosition.sanitize(m.optLong("viewOffset",-1),duration)
        WatchSample("server:plex:"+client+":"+id,"server.plex",title,detail,duration,position,state=="playing",when(state) { "playing"->PlaybackPhase.PLAYING; "buffering"->PlaybackPhase.BUFFERING; else->PlaybackPhase.PAUSED },"server")
    }
    fun jellyfin(body:JSONArray,client:String,kind:String):List<WatchSample> = objects(body).mapNotNull { s ->
        if(s.optString("DeviceId")!=client) return@mapNotNull null
        val m=s.optJSONObject("NowPlayingItem") ?: return@mapNotNull null
        val p=s.optJSONObject("PlayState") ?: return@mapNotNull null
        val type=m.optString("Type")
        if(type !in setOf("Movie","Episode") || !p.has("IsPaused")) return@mapNotNull null
        val id=m.optString("Id"); if(id.isBlank()) return@mapNotNull null
        val title=if(type=="Episode") m.optString("SeriesName") else m.optString("Name")
        if(title.isBlank()) return@mapNotNull null
        val detail=if(type=="Episode") "S"+m.optInt("ParentIndexNumber")+" E"+m.optInt("IndexNumber")+" • "+m.optString("Name") else ""
        val duration=m.optLong("RunTimeTicks",0)/10000
        val raw=p.optLong("PositionTicks",-1); val position=PlaybackPosition.sanitize(if(raw<0) -1 else raw/10000,duration)
        WatchSample("server:"+kind+":"+client+":"+id,"server."+kind,title,detail,duration,position,!p.optBoolean("IsPaused",true),if(p.optBoolean("IsPaused",true)) PlaybackPhase.PAUSED else PlaybackPhase.PLAYING,"server")
    }
    private fun time(o:JSONObject?):Long {
        if(o==null) return -1
        return ((o.optLong("hours")*60+o.optLong("minutes"))*60+o.optLong("seconds"))*1000+o.optLong("milliseconds")
    }
    fun kodi(item:JSONObject,properties:JSONObject,client:String):WatchSample? {
        val m=item.optJSONObject("item") ?: return null
        val type=m.optString("type")
        if(type !in setOf("movie","episode")) return null
        val title=if(type=="episode") m.optString("showtitle") else m.optString("title",m.optString("label"))
        if(title.isBlank()) return null
        val id=m.optLong("id",-1); if(id<0) return null
        val detail=if(type=="episode") "S"+m.optInt("season")+" E"+m.optInt("episode")+" • "+m.optString("title") else ""
        val duration=time(properties.optJSONObject("totaltime")).coerceAtLeast(0)
        return WatchSample("server:kodi:"+client+":"+type+":"+id,"server.kodi",title,detail,duration,PlaybackPosition.sanitize(time(properties.optJSONObject("time")),duration),properties.optInt("speed",0)>0,if(properties.optInt("speed",0)>0) PlaybackPhase.PLAYING else PlaybackPhase.PAUSED,"server",properties.optInt("speed",1).toFloat())
    }
}
object MediaServer {
    private fun request(c:ServerConfig,path:String,body:String?=null):String {
        ServerStore.validate(c)
        val connection=URL(c.url.trimEnd('/')+path).openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects=false; connection.connectTimeout=4000; connection.readTimeout=4000
            connection.setRequestProperty("Accept","application/json")
            when(c.kind) {
                "plex" -> connection.setRequestProperty("X-Plex-Token",c.token)
                "jellyfin","emby" -> connection.setRequestProperty("X-Emby-Token",c.token)
                "kodi" -> if(c.token.isNotBlank()) connection.setRequestProperty("Authorization","Basic "+Base64.encodeToString(c.token.toByteArray(Charsets.UTF_8),Base64.NO_WRAP))
            }
            if(body!=null) {
                connection.requestMethod="POST"; connection.doOutput=true; connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code=connection.responseCode
            if(code !in 200..299) error("Server HTTP "+code)
            return connection.inputStream.bufferedReader().use { reader ->
                val out=StringBuilder(); val buf=CharArray(4096)
                while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=2_000_000); out.append(buf,0,n) }; out.toString()
            }
        } finally { connection.disconnect() }
    }
    private fun rpc(c:ServerConfig,method:String,params:JSONObject=JSONObject()):Any {
        val body=JSONObject().put("jsonrpc","2.0").put("id",1).put("method",method).put("params",params)
        val result=JSONObject(request(c,"/jsonrpc",body.toString()))
        if(result.has("error")) error("Kodi RPC rejected")
        return result.get("result")
    }
    fun poll(c:ServerConfig):List<WatchSample> = when(c.kind) {
        "plex" -> ServerParsing.plex(JSONObject(request(c,"/status/sessions")),c.client)
        "jellyfin","emby" -> ServerParsing.jellyfin(JSONArray(request(c,"/Sessions")),c.client,c.kind)
        "kodi" -> {
            val players=rpc(c,"Player.GetActivePlayers") as JSONArray
            val result=mutableListOf<WatchSample>()
            for(i in 0 until players.length()) {
                val p=players.getJSONObject(i); if(p.optString("type")!="video") continue
                val id=p.getInt("playerid")
                val item=rpc(c,"Player.GetItem",JSONObject().put("playerid",id).put("properties",JSONArray(listOf("title","showtitle","season","episode")))) as JSONObject
                val props=rpc(c,"Player.GetProperties",JSONObject().put("playerid",id).put("properties",JSONArray(listOf("time","totaltime","speed")))) as JSONObject
                ServerParsing.kodi(item,props,c.client)?.let { result.add(it) }
            }; result
        }
        else -> emptyList()
    }
    fun discover(c:ServerConfig):List<Pair<String,String>> = when(c.kind) {
        "plex" -> {
            val a=JSONObject(request(c,"/status/sessions")).optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: JSONArray()
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optJSONObject("Player") }.mapNotNull { p -> p.optString("machineIdentifier").takeIf { it.isNotBlank() }?.let { it to p.optString("title","Plex client") } }.distinctBy { it.first }
        }
        "jellyfin","emby" -> {
            val a=JSONArray(request(c,"/Sessions"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { p -> p.optString("DeviceId").takeIf { it.isNotBlank() }?.let { it to p.optString("DeviceName","Media client") } }.distinctBy { it.first }
        }
        "kodi" -> { rpc(c,"Player.GetActivePlayers"); listOf("kodi" to "This Kodi instance") }
        else -> emptyList()
    }
}
class ServerSampler(private val context:Context) {
    private val executor=Executors.newFixedThreadPool(4)
    private val running=mutableSetOf<String>()
    private data class Snapshot(val time:Long,val samples:List<WatchSample>,val config:ServerConfig)
    private val snapshots=mutableMapOf<String,Snapshot>()
    private var generation=0L
    @Synchronized fun samples(now:Long):List<WatchSample> {
        val configs=listOf("plex","jellyfin","emby","kodi").mapNotNull { ServerStore.load(context,it) }
        val kinds=configs.map { it.kind }.toSet()
        snapshots.keys.retainAll(kinds)
        for(c in configs) {
            val old=snapshots[c.kind]
            if(old!=null && old.config!=c) snapshots.remove(c.kind)
            if(c.kind in running) continue
            running.add(c.kind)
            val currentGeneration=generation
            executor.execute {
                val result=runCatching { MediaServer.poll(c) }
                synchronized(this) {
                    running.remove(c.kind)
                    if(currentGeneration!=generation || ServerStore.load(context,c.kind)!=c) return@synchronized
                    result.fold({ rows ->
                        snapshots[c.kind]=Snapshot(System.currentTimeMillis(),rows,c)
                        DebugTrace.event("server","provider="+c.kind+" sessions="+rows.size)
                    },{ snapshots.remove(c.kind); DebugTrace.event("server","provider="+c.kind+" unavailable") })
                }
            }
        }
        return snapshots.values.filter { now-it.time in 0..10000 }.flatMap { it.samples }
    }
    @Synchronized fun clear() { generation++; snapshots.clear() }
    @Synchronized fun close() { clear(); executor.shutdownNow() }
}
