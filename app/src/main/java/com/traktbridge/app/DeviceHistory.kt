package com.traktbridge.app

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Locale

/** Read-only remote observations. Never enters the local completion or Trakt upload queue. */
object DeviceHistory {
    const val LIMIT=100
    private const val MAX_BYTES=262144
    private const val MAX_DURATION=604800000L
    private const val RETENTION=90L*86400000
    data class Event(val id:String,val source:String,val title:String,val detail:String,val started:Long,val ended:Long,val watched:Long,val duration:Long,val position:Long)
    fun enabled(context:Context)=PlaybackSharing.enabled(context) &&
        context.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("device_history",false)
    fun valid(e:Event,now:Long):Boolean=e.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) &&
        e.source.length in 1..150 && e.title.length in 1..250 && e.title.isNotBlank() && e.detail.length<=200 &&
        e.started>0 && e.ended>=e.started && e.ended in (now-RETENTION)..(now+120000) &&
        e.ended-e.started<=MAX_DURATION && e.watched in 15000..(e.ended-e.started+12000) &&
        e.duration in 0..MAX_DURATION && (e.position==-1L || e.duration>0 && e.position in 0..e.duration)
    private fun norm(s:String)=s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
    fun duplicate(a:Event,b:Event):Boolean {
        if(a.id==b.id) return true
        if(PlaybackHandoff.canonical(a.source)!=PlaybackHandoff.canonical(b.source) || norm(a.title)!=norm(b.title) || norm(a.detail)!=norm(b.detail)) return false
        val overlap=minOf(a.ended,b.ended)-maxOf(a.started,b.started)
        val shorter=minOf(a.ended-a.started,b.ended-b.started)
        return shorter>0 && overlap>0 && overlap>=shorter*0.8
    }
    private fun fromRecord(r:WatchRecord)=Event(r.id,r.source.take(150),r.title.take(250),r.subtitle.take(200),r.started,r.ended,r.watched,r.duration,r.position)
    private fun json(e:Event)=JSONObject().put("id",e.id).put("source",e.source).put("title",e.title).put("detail",e.detail)
        .put("started",e.started).put("ended",e.ended).put("watched",e.watched).put("duration",e.duration).put("position",e.position)
    private fun parse(o:JSONObject)=Event(o.getString("id"),o.getString("source"),o.getString("title"),o.getString("detail"),o.getLong("started"),o.getLong("ended"),o.getLong("watched"),o.getLong("duration"),o.getLong("position"))
    fun snapshot(context:Context):JSONArray {
        if(!enabled(context)) return JSONArray()
        val db=WatchHistory(context)
        try {
            val now=System.currentTimeMillis()
            val out=JSONArray()
            db.records(500).asSequence().filter { it.status in setOf("pending","reviewed","queued","uncertain","blocked","synced") }
                .map(::fromRecord).filter { valid(it,now) }.take(LIMIT).forEach { out.put(json(it)) }
            return out
        } finally { db.close() }
    }
    fun write(output:DataOutputStream,rows:JSONArray) {
        val bytes=rows.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size in 2..MAX_BYTES)
        output.writeInt(bytes.size); output.write(bytes); output.flush()
    }
    fun read(input:DataInputStream):List<Event> {
        val size=input.readInt(); require(size in 2..MAX_BYTES)
        val bytes=ByteArray(size); input.readFully(bytes)
        val rows=JSONArray(bytes.toString(Charsets.UTF_8)); require(rows.length()<=LIMIT)
        val now=System.currentTimeMillis()
        return (0 until rows.length()).mapNotNull { i -> runCatching { parse(rows.getJSONObject(i)) }.getOrNull()?.takeIf { valid(it,now) } }.distinctBy { it.id }
    }
    @Synchronized fun merge(context:Context,peer:String,events:List<Event>) {
        if(!enabled(context) || DevicePairing.peers(context).none { it.id==peer }) return
        val helper=WatchHistory(context)
        try {
            val db=helper.writableDatabase
            val now=System.currentTimeMillis()
            db.beginTransaction()
            try {
                if(!enabled(context) || DevicePairing.peers(context).none { it.id==peer }) return
                for(e in events.take(LIMIT).filter { valid(it,now) }) {
                    val values=ContentValues().apply { put("peer",peer); put("event_id",e.id); put("source",e.source); put("title",e.title); put("detail",e.detail); put("started",e.started); put("ended",e.ended); put("watched",e.watched); put("duration",e.duration); put("position",e.position) }
                    db.insertWithOnConflict("device_history",null,values,SQLiteDatabase.CONFLICT_IGNORE)
                }
                db.delete("device_history","ended<?",arrayOf((now-RETENTION).toString()))
                db.execSQL("DELETE FROM device_history WHERE rowid IN (SELECT rowid FROM device_history ORDER BY ended DESC, rowid DESC LIMIT -1 OFFSET 5000)")
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        } finally { helper.close() }
    }
    data class Item(val peer:String,val event:Event)
    fun visible(context:Context):List<Item> {
        if(!enabled(context)) return emptyList()
        val allowed=DevicePairing.peers(context).map { it.id }.toSet()
        val helper=WatchHistory(context)
        try {
            val now=System.currentTimeMillis()
            val local=helper.records(2000).map(::fromRecord)
            val out=mutableListOf<Item>()
            helper.readableDatabase.rawQuery("SELECT peer,event_id,source,title,detail,started,ended,watched,duration,position FROM device_history WHERE ended>=? ORDER BY ended DESC LIMIT 5000",arrayOf((now-RETENTION).toString())).use { c ->
                while(c.moveToNext() && out.size<500) {
                    val peer=c.getString(0)
                    if(peer !in allowed) continue
                    val e=Event(c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getLong(9))
                    if(valid(e,now) && local.none { duplicate(it,e) } && out.none { duplicate(it.event,e) }) out.add(Item(peer,e))
                }
            }
            return out
        } finally { helper.close() }
    }
    @Synchronized fun clear(context:Context,peer:String?=null) {
        val db=WatchHistory(context)
        try { if(peer==null) db.writableDatabase.delete("device_history",null,null) else db.writableDatabase.delete("device_history","peer=?",arrayOf(peer)) } finally { db.close() }
    }
}
