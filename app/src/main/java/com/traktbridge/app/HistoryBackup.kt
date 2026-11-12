package com.traktbridge.app

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

object HistoryBackup {
    private val textFields = listOf("id","source","title","subtitle","status","match_type","match_label","position_source")
    private val numberFields = listOf("started","ended","watched","duration","position","trakt_id","show_id","season","episode","catalogue_runtime","paused_ms","buffering_ms","uncertain_ms","seeks","rewinds")
    private val fields = textFields + numberFields
    private const val MAX_ROWS = 50000
    const val MAX_CHARS = 24000000

    fun snapshot(history:WatchHistory):JSONObject {
        val rows=JSONArray()
        val db=history.readableDatabase
        db.query("viewing",fields.toTypedArray(),"status<>'active'",null,null,null,"ended DESC").use { c ->
            while(c.moveToNext()) {
                require(rows.length()<MAX_ROWS) { "Too many records for one backup" }
                val row=JSONObject()
                fields.forEachIndexed { i,k -> if(k in textFields) row.put(k,c.getString(i)) else row.put(k,c.getLong(i)) }
                rows.put(row)
            }
        }
        return JSONObject().put("format","traktbridge-history").put("version",1).put("records",rows)
    }
    fun csv(history:WatchHistory):String {
        val rows=snapshot(history).getJSONArray("records")
        fun cell(s:String):String {
            val safe=if(s.firstOrNull() in listOf('=','+','-','@','\t','\r')) "'"+s else s
            return "\""+safe.replace("\"","\"\"")+"\""
        }
        return buildString {
            append(fields.joinToString(",")).append("\r\n")
            for(i in 0 until rows.length()) {
                val r=rows.getJSONObject(i)
                append(fields.joinToString(",") { if(it in numberFields) r.getLong(it).toString() else cell(r.getString(it)) }).append("\r\n")
            }
        }
    }
    fun parse(text:String):List<ContentValues> {
        require(text.length<=MAX_CHARS) { "Backup too large" }
        val root=JSONObject(text)
        require(root.getString("format")=="traktbridge-history" && root.getInt("version")==1) { "Unsupported backup format" }
        val rows=root.getJSONArray("records")
        require(rows.length()<=MAX_ROWS)
        val ids=HashSet<String>()
        return (0 until rows.length()).map { i ->
            val r=rows.getJSONObject(i)
            val id=r.getString("id")
            require(id.isNotBlank() && id.length<=2048 && ids.add(id)) { "Invalid or duplicate ID" }
            require(r.getString("source").isNotBlank())
            require(r.getLong("started")>0 && r.getLong("ended")>=r.getLong("started"))
            require(listOf("watched","duration","catalogue_runtime","paused_ms","buffering_ms","uncertain_ms","seeks","rewinds","trakt_id","show_id").all { r.getLong(it)>=0 })
            require(r.getLong("position")>=-1 && r.getLong("season")>=-1 && r.getLong("episode")>=-1)
            val status=r.getString("status")
            val type=r.getString("match_type")
            require(type in setOf("","movie","episode"))
            val trakt=r.getLong("trakt_id")
            require((type.isBlank() && trakt==0L) || (type.isNotBlank() && trakt>0L)) { "Invalid catalogue identity" }
            require(type!="episode" || (r.getLong("show_id")>0 && r.getLong("season") in 0..Int.MAX_VALUE.toLong() && r.getLong("episode") in 1..Int.MAX_VALUE.toLong())) { "Invalid episode identity" }
            require(r.getLong("seeks")<=Int.MAX_VALUE && r.getLong("rewinds")<=Int.MAX_VALUE)
            require(r.getLong("position")<0 || (r.getLong("duration")>0 && r.getLong("position")<=r.getLong("duration"))) { "Invalid playback position" }
            require(status in setOf("pending","reviewed","queued","uncertain","blocked","synced","dismissed","reconciled","ignored","trashed","trashed"))
            ContentValues().apply {
                textFields.forEach { k -> val s=r.getString(k); require(s.length<=16384); put(k,s) }
                numberFields.forEach { k -> put(k,r.getLong(k)) }
                // Never restore authorization to upload, nor assert a sync for a different account.
                put("status",when(status) { "dismissed","reconciled","ignored" -> status; "pending" -> "pending"; else -> "reviewed" })
                put("sync_account",""); put("sync_message",""); put("notified",1)
            }
        }
    }
    fun merge(history:WatchHistory,rows:List<ContentValues>):Pair<Int,Int> {
        val db=history.writableDatabase
        var added=0
        db.beginTransaction()
        try {
            rows.forEach { if(db.insertWithOnConflict("viewing",null,it,SQLiteDatabase.CONFLICT_IGNORE)!=-1L) added++ }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added to (rows.size-added)
    }
    fun integrity(history:WatchHistory):Boolean =
        history.readableDatabase.rawQuery("PRAGMA quick_check",null).use { it.moveToFirst() && it.getString(0)=="ok" && !it.moveToNext() }
}
