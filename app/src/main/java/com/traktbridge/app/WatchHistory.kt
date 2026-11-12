package com.traktbridge.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class WatchSample(val key: String, val source: String, val title: String, val subtitle: String, val duration: Long, val position: Long, val playing: Boolean)
data class WatchRecord(val id: String, val source: String, val title: String, val subtitle: String, val started: Long, val ended: Long, val watched: Long, val duration: Long, val position: Long, val status: String, val matchType: String = "", val traktId: Long = 0, val matchLabel: String = "", val showId: Long = 0, val season: Int = -1, val episode: Int = -1, val catalogueRuntime: Long = 0)

class WatchHistory(context: Context): SQLiteOpenHelper(context.applicationContext, "watch_history.db", null, 5) {
    companion object {
        private const val MAX_GAP = 12000L
        private const val MIN_WATCH = 15000L
        private const val PAUSE_TIMEOUT = 120000L
    }
    private data class Active(val id: String, val sample: WatchSample, val lastTick: Long, val lastPlaying: Long, val watched: Long, val started: Long, val lastWall: Long)
    private val active = mutableMapOf<String, Active>()
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE viewing (id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL, watched INTEGER NOT NULL, duration INTEGER NOT NULL, position INTEGER NOT NULL, status TEXT NOT NULL, match_type TEXT NOT NULL DEFAULT '', trakt_id INTEGER NOT NULL DEFAULT 0, match_label TEXT NOT NULL DEFAULT '', show_id INTEGER NOT NULL DEFAULT 0, season INTEGER NOT NULL DEFAULT -1, episode INTEGER NOT NULL DEFAULT -1, notified INTEGER NOT NULL DEFAULT 0, sync_account TEXT NOT NULL DEFAULT '', sync_message TEXT NOT NULL DEFAULT '', catalogue_runtime INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX viewing_status ON viewing(status, ended)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE viewing ADD COLUMN match_type TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE viewing ADD COLUMN trakt_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE viewing ADD COLUMN match_label TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE viewing ADD COLUMN show_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE viewing ADD COLUMN season INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE viewing ADD COLUMN episode INTEGER NOT NULL DEFAULT -1")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE viewing ADD COLUMN notified INTEGER NOT NULL DEFAULT 0")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE viewing ADD COLUMN sync_account TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE viewing ADD COLUMN sync_message TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 5) db.execSQL("ALTER TABLE viewing ADD COLUMN catalogue_runtime INTEGER NOT NULL DEFAULT 0")
    }
    fun pendingNotifications(): List<WatchRecord> = records().filter { it.status == "pending" && !wasNotified(it.id) }
    private fun wasNotified(id: String): Boolean = readableDatabase.rawQuery("SELECT notified FROM viewing WHERE id=?",arrayOf(id)).use { !it.moveToFirst() || it.getInt(0) != 0 }
    fun markNotified(id: String) { writableDatabase.update("viewing",ContentValues().apply { put("notified",1) },"id=? AND status='pending'",arrayOf(id)) }
    fun record(id: String): WatchRecord? {
        readableDatabase.rawQuery("SELECT id,source,title,subtitle,started,ended,watched,duration,position,status,match_type,trakt_id,match_label,show_id,season,episode,catalogue_runtime FROM viewing WHERE id=?",arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            return WatchRecord(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getString(10),c.getLong(11),c.getString(12),c.getLong(13),c.getInt(14),c.getInt(15),c.getLong(16))
        }
    }
    @Synchronized fun recover() {
        val db = writableDatabase
        db.execSQL("DELETE FROM viewing WHERE status='active' AND watched < ?", arrayOf(MIN_WATCH))
        db.execSQL("UPDATE viewing SET status='pending' WHERE status='active'")
        active.clear()
    }
    private fun save(a: Active, status: String) {
        val v = ContentValues().apply {
            put("id", a.id); put("source", a.sample.source); put("title", a.sample.title); put("subtitle", a.sample.subtitle)
            put("started", a.started); put("ended", a.lastWall); put("watched", a.watched)
            put("duration", a.sample.duration); put("position", a.sample.position); put("status", status)
        }
        check(writableDatabase.insertWithOnConflict("viewing", null, v, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    private fun finish(key: String) {
        val a = active.remove(key) ?: return
        DebugTrace.event("history","session ended watchedMs="+a.watched+" retained="+(a.watched>=MIN_WATCH))
        if (a.watched >= MIN_WATCH) save(a, "pending")
        else writableDatabase.delete("viewing", "id=?", arrayOf(a.id))
    }
    @Synchronized fun ingest(samples: List<WatchSample>, elapsed: Long, wall: Long) {
        val current = samples.associateBy { it.key }
        for (key in active.keys.toList()) {
            val old = active[key] ?: continue
            val now = current[key]
            if (now == null || now.title != old.sample.title || now.subtitle != old.sample.subtitle || elapsed - old.lastTick !in 0..MAX_GAP ||
                (!now.playing && elapsed - old.lastPlaying >= PAUSE_TIMEOUT)) finish(key)
        }
        for (s in current.values) {
            val old = active[s.key]
            if (old == null) {
                if (!s.playing) continue
                val a = Active(UUID.randomUUID().toString(), s, elapsed, elapsed, 0, wall, wall)
                active[s.key] = a; save(a, "active")
            } else {
                // Count real elapsed time only when both surrounding samples report PLAYING.
                // Seeks and playback speed never inflate elapsed viewing time.
                val delta = if (old.sample.playing && s.playing) (elapsed - old.lastTick).coerceIn(0, MAX_GAP) else 0
                val merged = s.copy(duration = if (s.duration > 0) s.duration else old.sample.duration,
                    position = if (s.position >= 0) s.position else old.sample.position)
                val a = old.copy(sample = merged, lastTick = elapsed, lastPlaying = if (s.playing) elapsed else old.lastPlaying,
                    watched = old.watched + delta, lastWall = if (s.playing) wall else old.lastWall)
                active[s.key] = a; save(a, "active")
            }
        }
    }
    @Synchronized fun finishAll() { for (key in active.keys.toList()) finish(key) }
    fun records(limit: Int = 100): List<WatchRecord> {
        val result = mutableListOf<WatchRecord>()
        readableDatabase.rawQuery("SELECT id,source,title,subtitle,started,ended,watched,duration,position,status,match_type,trakt_id,match_label,show_id,season,episode,catalogue_runtime FROM viewing WHERE status IN ('pending','active','reviewed','queued','uncertain','blocked','synced') ORDER BY ended DESC"+(if(limit>0) " LIMIT "+limit else ""), null).use { c ->
            while(c.moveToNext()) result.add(WatchRecord(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getString(10),c.getLong(11),c.getString(12),c.getLong(13),c.getInt(14),c.getInt(15),c.getLong(16)))
        }
        return result
    }
    fun review(id: String, title: String) {
        require(title.isNotBlank())
        writableDatabase.update("viewing", ContentValues().apply { put("title",title); put("status","reviewed") }, "id=? AND status='pending'", arrayOf(id))
    }
    fun match(id: String, item: CatalogueItem) {
        require(item.id > 0 && (item.type == "movie" || item.type == "episode"))
        require(item.type != "episode" || (item.showId > 0 && item.season >= 0 && item.episode > 0))
        writableDatabase.update("viewing", ContentValues().apply {
            put("match_type",item.type); put("trakt_id",item.id); put("match_label",item.label())
            put("show_id",item.showId); put("season",item.season); put("episode",item.episode); put("catalogue_runtime",item.runtimeMinutes.coerceAtLeast(0)*60000L); put("status","reviewed")
        }, "id=? AND status IN ('pending','reviewed')", arrayOf(id))
    }
    fun clearMatch(id: String) {
        writableDatabase.update("viewing", ContentValues().apply {
            put("match_type",""); put("trakt_id",0); put("match_label",""); put("show_id",0); put("season",-1); put("episode",-1); put("catalogue_runtime",0L)
        }, "id=? AND status IN ('pending','reviewed')", arrayOf(id))
    }
    fun enqueue(id: String, account: String): Boolean {
        require(account.isNotBlank())
        val db=writableDatabase
        db.beginTransaction()
        try {
            val r=record(id) ?: return false
            if(r.status!="reviewed" || r.traktId<=0 || r.matchType !in setOf("movie","episode")) return false
            val exists=db.rawQuery("SELECT 1 FROM viewing WHERE id<>? AND match_type=? AND trakt_id=? AND status IN ('queued','uncertain','blocked','synced') AND sync_account=? LIMIT 1",arrayOf(id,r.matchType,r.traktId.toString(),account)).use { it.moveToFirst() }
            if(exists) return false
            val ok=db.update("viewing",ContentValues().apply { put("status","queued"); put("sync_account",account); put("sync_message","Approved; waiting for network") },"id=? AND status='reviewed'",arrayOf(id))==1
            db.setTransactionSuccessful()
            return ok
        } finally { db.endTransaction() }
    }
    fun approvedAccount(id: String): String = readableDatabase.rawQuery("SELECT sync_account FROM viewing WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else "" }
    fun syncMessage(id: String): String = readableDatabase.rawQuery("SELECT sync_message FROM viewing WHERE id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else "" }
    fun syncQueue(): List<WatchRecord> {
        val ids=mutableListOf<String>()
        readableDatabase.rawQuery("SELECT id FROM viewing WHERE status IN ('queued','uncertain') ORDER BY CASE WHEN status='queued' THEN 0 ELSE 1 END, ended",null).use { c -> while(c.moveToNext()) ids.add(c.getString(0)) }
        return ids.mapNotNull { record(it) }
    }
    fun beginUpload(id: String): Boolean = writableDatabase.update("viewing",ContentValues().apply { put("status","uncertain"); put("sync_message","Upload in progress or outcome unknown") },"id=? AND status='queued'",arrayOf(id))==1
    fun syncState(id: String,status: String,message: String) {
        require(status in setOf("queued","uncertain","blocked","synced"))
        writableDatabase.update("viewing",ContentValues().apply { put("status",status); put("sync_message",message) },"id=? AND status IN ('queued','uncertain')",arrayOf(id))
    }
    fun retryBlocked(id: String) {
        writableDatabase.update("viewing",ContentValues().apply { put("status","queued"); put("sync_message","Retry requested") },"id=? AND status='blocked'",arrayOf(id))
    }
    fun cancelQueued(id: String) {
        writableDatabase.update("viewing",ContentValues().apply { put("status","reviewed"); put("sync_account",""); put("sync_message","") },"id=? AND status IN ('queued','blocked')",arrayOf(id))
    }
    fun addManualActivity(source: String,title: String,started: Long,ended: Long): String {
        require(title.isNotBlank() && ended>=started && started>0)
        val id="usage:"+source+":"+started+":"+ended
        val values=ContentValues().apply {
            put("id",id); put("source",source); put("title",title); put("subtitle","Manually identified from app activity; playback time unknown")
            put("started",started); put("ended",ended); put("watched",0L); put("duration",0L); put("position",-1L); put("status","pending")
        }
        writableDatabase.insertWithOnConflict("viewing",null,values,SQLiteDatabase.CONFLICT_IGNORE)
        return id
    }
    fun addSharedCandidate(source:String,title:String,detail:String,runtime:Long):String {
        val now=System.currentTimeMillis()
        val id="shared:"+UUID.randomUUID()
        val values=ContentValues().apply {
            put("id",id); put("source",source); put("title",title); put("subtitle","Shared content; viewing not verified"+if(detail.isBlank()) "" else " • "+detail)
            put("started",now); put("ended",now); put("watched",0L); put("duration",runtime.coerceAtLeast(0)); put("position",-1L); put("status","pending")
        }
        check(writableDatabase.insertWithOnConflict("viewing",null,values,SQLiteDatabase.CONFLICT_ABORT)!=-1L)
        return id
    }
    fun attachSharedCandidate(id:String,title:String,detail:String,runtime:Long):Boolean {
        val r=record(id) ?: return false
        if(r.status!="pending" || r.traktId>0) return false
        return writableDatabase.update("viewing",ContentValues().apply {
            put("title",title); put("subtitle","Shared title suggestion"+if(detail.isBlank()) "" else " • "+detail)
            if(r.duration<=0 && runtime>0 && r.position<0) put("duration",runtime)
        },"id=? AND status='pending' AND trakt_id=0",arrayOf(id))==1
    }
    fun addSuggestedActivity(source: String,title: String,detail: String,started: Long,ended: Long): String {
        val id=addManualActivity(source,title,started,ended)
        writableDatabase.update("viewing",ContentValues().apply { put("subtitle","Unverified player title suggestion"+if(detail.isBlank()) "" else " • "+detail) },"id=? AND status='pending'",arrayOf(id))
        return id
    }
    fun overlapsActivity(source: String,started: Long,ended: Long): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM viewing WHERE source=? AND started<=? AND ended>=? LIMIT 1",arrayOf(source,ended.toString(),started.toString())).use { it.moveToFirst() }
    fun discard(id: String) { writableDatabase.delete("viewing", "id=? AND status IN ('pending','reviewed')", arrayOf(id)) }
}
