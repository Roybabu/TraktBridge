package com.traktbridge.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

object PlaybackSharing {
    const val MAX_BYTES=32768
    const val TTL_MS=20000L
    internal fun fresh(age: Long)=age in 0..TTL_MS
    internal fun frameSize(size: Int)=size in 2..MAX_BYTES
    fun enabled(context: Context): Boolean {
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        return prefs.getBoolean("device_sharing",false) && prefs.getBoolean("enabled",false)
    }
    fun localWifi(context: Context): Boolean {
        val cm=context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps=cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    fun sanitize(rows: JSONArray, now: Long, checkAge: Boolean): JSONArray {
        val out=JSONArray()
        for(i in 0 until minOf(rows.length(),12)) {
            val row=rows.optJSONObject(i) ?: continue
            if(checkAge && !fresh(now-row.optLong("sampledAt",-1))) continue
            if(row.optBoolean("recognitionConflict") || row.optString("segment")=="ADVERTISEMENT") continue
            val title=row.optString("title").take(250)
            val state=row.optInt("state")
            if(title.isBlank() || state !in setOf(2,3,6,8)) continue
            val duration=row.optLong("durationMs",0).coerceIn(0,604800000)
            val position=row.optLong("positionMs",-1).takeIf { it in 0..duration && duration>0 } ?: -1
            out.put(JSONObject().put("title",title).put("subtitle",row.optString("subtitle").take(200))
                .put("package",row.optString("package").take(150)).put("state",state)
                .put("durationMs",duration).put("positionMs",position)
                .put("sessionId",row.optString("sessionId").takeIf { DeviceControl.valid(it) }.orEmpty())
                .put("controlMask",row.optInt("controlMask").coerceIn(0,3)))
        }
        return out
    }
    fun snapshot(context: Context): JSONArray {
        val prefs=context.getSharedPreferences("tracker",Context.MODE_PRIVATE)
        val now=System.currentTimeMillis()
        if(!enabled(context) || !prefs.getBoolean("listener_connected",false) || !fresh(now-prefs.getLong("last_sample",0))) return JSONArray()
        return runCatching { sanitize(JSONArray(prefs.getString("sessions","[]")),now,true) }.getOrDefault(JSONArray())
    }
    fun write(output: DataOutputStream, rows: JSONArray) {
        val data=rows.toString().toByteArray(Charsets.UTF_8)
        require(frameSize(data.size))
        output.writeInt(data.size); output.write(data); output.flush()
    }
    fun read(input: DataInputStream): JSONArray {
        val size=input.readInt()
        require(frameSize(size))
        val bytes=ByteArray(size); input.readFully(bytes)
        return sanitize(JSONArray(bytes.toString(Charsets.UTF_8)),0,false)
    }
}
