package com.traktbridge.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class WatchSample(val key: String, val source: String, val title: String, val subtitle: String, val duration: Long, val position: Long, val playing: Boolean, val phase: PlaybackPhase = if(playing) PlaybackPhase.PLAYING else PlaybackPhase.PAUSED, val positionSource:String = if(position>=0) "media" else "unknown", val speed:Float=1f)
data class WatchRecord(val id: String, val source: String, val title: String, val subtitle: String, val started: Long, val ended: Long, val watched: Long, val duration: Long, val position: Long, val status: String, val matchType: String = "", val traktId: Long = 0, val matchLabel: String = "", val showId: Long = 0, val season: Int = -1, val episode: Int = -1, val catalogueRuntime: Long = 0, val pausedMs:Long=0, val bufferingMs:Long=0, val uncertainMs:Long=0, val seeks:Int=0, val rewinds:Int=0, val positionSource:String="unknown")

class WatchHistory(context: Context): SQLiteOpenHelper(context.applicationContext, "watch_history.db", null, 7) {
    companion object {
        private const val MAX_GAP = 12000L
        private const val MIN_WATCH = 15000L
        private const val PAUSE_TIMEOUT = 120000L
    }
    private data class Active(val id: String, val sample: WatchSample, val lastTick: Long, val lastPlaying: Long, val watched: Long, val started: Long, val lastWall: Long, val paused:Long=0, val buffering:Long=0, val uncertain:Long=0, val seeks:Int=0, val rewinds:Int=0, val resumePosition:Long=-1, val resumeDuration:Long=0, val resumeSource:String="unknown")
    private val active = mutableMapOf<String, Active>()
    private fun createLearning(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS learned (key TEXT PRIMARY KEY, type TEXT NOT NULL, trakt_id INTEGER NOT NULL, label TEXT NOT NULL, show_id INTEGER NOT NULL, season INTEGER NOT NULL, episode INTEGER NOT NULL, runtime INTEGER NOT NULL, title TEXT NOT NULL, year INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS provider_refs (record_id TEXT PRIMARY KEY, provider_key TEXT NOT NULL)")
    }
    override fun onCreate(db: SQLiteDatabase) {
        createLearning(db)
        db.execSQL("CREATE TABLE viewing (id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT NOT NULL, subtitle TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL, watched INTEGER NOT NULL, duration INTEGER NOT NULL, position INTEGER NOT NULL, status TEXT NOT NULL, match_type TEXT NOT NULL DEFAULT '', trakt_id INTEGER NOT NULL DEFAULT 0, match_label TEXT NOT NULL DEFAULT '', show_id INTEGER NOT NULL DEFAULT 0, season INTEGER NOT NULL DEFAULT -1, episode INTEGER NOT NULL DEFAULT -1, notified INTEGER NOT NULL DEFAULT 0, sync_account TEXT NOT NULL DEFAULT '', sync_message TEXT NOT NULL DEFAULT '', catalogue_runtime INTEGER NOT NULL DEFAULT 0, paused_ms INTEGER NOT NULL DEFAULT 0, buffering_ms INTEGER NOT NULL DEFAULT 0, uncertain_ms INTEGER NOT NULL DEFAULT 0, seeks INTEGER NOT NULL DEFAULT 0, rewinds INTEGER NOT NULL DEFAULT 0, position_source TEXT NOT NULL DEFAULT 'unknown')")
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
        if (oldVersion < 7) {
            for(column in listOf("paused_ms","buffering_ms","uncertain_ms","seeks","rewinds")) db.execSQL("ALTER TABLE viewing ADD COLUMN "+column+" INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE viewing ADD COLUMN position_source TEXT NOT NULL DEFAULT 'unknown'")
        }
        if (oldVersion < 6) createLearning(db)
        if (oldVersion < 5) db.execSQL("ALTER TABLE viewing ADD COLUMN catalogue_runtime INTEGER NOT NULL DEFAULT 0")
    }
    fun pendingNotifications(): List<WatchRecord> = records().filter { it.status == "pending" && !wasNotified(it.id) }
    private fun wasNotified(id: String): Boolean = readableDatabase.rawQuery("SELECT notified FROM viewing WHERE id=?",arrayOf(id)).use { !it.moveToFirst() || it.getInt(0) != 0 }
    fun markNotified(id: String) { writableDatabase.update("viewing",ContentValues().apply { put("notified",1) },"id=? AND status='pending'",arrayOf(id)) }
    fun record(id: String): WatchRecord? {
        readableDatabase.rawQuery("SELECT id,source,title,subtitle,started,ended,watched,duration,position,status,match_type,trakt_id,match_label,show_id,season,episode,catalogue_runtime,paused_ms,buffering_ms,uncertain_ms,seeks,rewinds,position_source FROM viewing WHERE id=?",arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            return WatchRecord(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getString(10),c.getLong(11),c.getString(12),c.getLong(13),c.getInt(14),c.getInt(15),c.getLong(16),c.getLong(17),c.getLong(18),c.getLong(19),c.getInt(20),c.getInt(21),c.getString(22))
        }
    }
    @Synchronized fun recover() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM viewing WHERE status='active' AND watched < ?", arrayOf(MIN_WATCH))
            db.execSQL("UPDATE viewing SET status='pending' WHERE status='active'")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        active.clear()
    }
    private fun save(a: Active, status: String) {
        val v = ContentValues().apply {
            put("id", a.id); put("source", a.sample.source); put("title", a.sample.title); put("subtitle", a.sample.subtitle)
            put("started", a.started); put("ended", a.lastWall); put("watched", a.watched)
            put("duration", a.resumeDuration); put("position", a.resumePosition); put("status", status)
            put("paused_ms",a.paused); put("buffering_ms",a.buffering); put("uncertain_ms",a.uncertain); put("seeks",a.seeks); put("rewinds",a.rewinds); put("position_source",a.resumeSource)
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
            if (now == null || !PlaybackAccuracy.compatibleIdentity(old.sample,now) || now.phase==PlaybackPhase.STOPPED || elapsed - old.lastTick !in 0..MAX_GAP ||
                (!now.playing && elapsed - old.lastPlaying >= PAUSE_TIMEOUT)) finish(key)
        }
        for (s in current.values) {
            val old = active[s.key]
            if (old == null) {
                if (!s.playing) continue
                val valid=PlaybackPosition.valid(s.position,s.duration)
                val a = Active(UUID.randomUUID().toString(), s, elapsed, elapsed, 0, wall, wall,resumePosition=if(valid) s.position else -1,resumeDuration=s.duration.coerceAtLeast(0),resumeSource=if(valid) s.positionSource else "unknown")
                active[s.key] = a; save(a, "active")
            } else {
                val interval=PlaybackAccuracy.interval(old.sample,s,elapsed-old.lastTick)
                val merged=PlaybackAccuracy.mergeIdentity(old.sample,s)
                val valid=PlaybackPosition.valid(s.position,s.duration)
                val a = old.copy(sample = merged, lastTick = elapsed, lastPlaying = if (s.playing) elapsed else old.lastPlaying,
                    watched = old.watched + interval.watched, lastWall = if (s.playing) maxOf(old.lastWall,wall,old.started) else old.lastWall,
                    paused=old.paused+interval.paused,buffering=old.buffering+interval.buffering,uncertain=old.uncertain+interval.uncertain,seeks=old.seeks+interval.seeks,rewinds=old.rewinds+interval.replay,
                    resumePosition=if(valid) s.position else old.resumePosition,resumeDuration=if(valid) s.duration else old.resumeDuration.takeIf { it>0 } ?: s.duration.coerceAtLeast(0),resumeSource=if(valid) s.positionSource else old.resumeSource)
                if(interval.seeks>0) DebugTrace.event("progress","seek detected rewind="+(interval.replay>0))
                active[s.key] = a; save(a, "active")
            }
        }
    }
    @Synchronized fun finishAll() { for (key in active.keys.toList()) finish(key) }
    fun records(limit: Int = 100): List<WatchRecord> {
        val result = mutableListOf<WatchRecord>()
        readableDatabase.rawQuery("SELECT id,source,title,subtitle,started,ended,watched,duration,position,status,match_type,trakt_id,match_label,show_id,season,episode,catalogue_runtime,paused_ms,buffering_ms,uncertain_ms,seeks,rewinds,position_source FROM viewing WHERE status IN ('pending','active','reviewed','queued','uncertain','blocked','synced','restored_audit') ORDER BY ended DESC"+(if(limit>0) " LIMIT "+limit else ""), null).use { c ->
            while(c.moveToNext()) result.add(WatchRecord(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getLong(4),c.getLong(5),c.getLong(6),c.getLong(7),c.getLong(8),c.getString(9),c.getString(10),c.getLong(11),c.getString(12),c.getLong(13),c.getInt(14),c.getInt(15),c.getLong(16),c.getLong(17),c.getLong(18),c.getLong(19),c.getInt(20),c.getInt(21),c.getString(22)))
        }
        return result
    }
    fun review(id: String, title: String) {
        require(title.isNotBlank())
        writableDatabase.update("viewing", ContentValues().apply { put("title",title); put("status","reviewed") }, "id=? AND status='pending'", arrayOf(id))
    }
    fun match(id: String, item: CatalogueItem, learn:Boolean=true) {
        val before=record(id) ?: return
        require(item.id > 0 && (item.type == "movie" || item.type == "episode"))
        require(item.type != "episode" || (item.showId > 0 && item.season >= 0 && item.episode > 0))
        val changed=writableDatabase.update("viewing", ContentValues().apply {
            put("match_type",item.type); put("trakt_id",item.id); put("match_label",item.label())
            put("show_id",item.showId); put("season",item.season); put("episode",item.episode); put("catalogue_runtime",item.runtimeMinutes.coerceAtLeast(0)*60000L); put("status","reviewed")
        }, "id=? AND status IN ('pending','reviewed')", arrayOf(id))
        if(changed==1 && learn) remember(before,item)
    }
    fun clearMatch(id: String) {
        record(id)?.let { r -> forget(r) }
        writableDatabase.update("viewing", ContentValues().apply {
            put("match_type",""); put("trakt_id",0); put("match_label",""); put("show_id",0); put("season",-1); put("episode",-1); put("catalogue_runtime",0L)
        }, "id=? AND status IN ('pending','reviewed')", arrayOf(id))
    }
    fun providerRef(id:String,key:String?) {
        if(key==null) return
        writableDatabase.insertWithOnConflict("provider_refs",null,ContentValues().apply { put("record_id",id); put("provider_key",key) },SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun ref(id:String):String?=readableDatabase.rawQuery("SELECT provider_key FROM provider_refs WHERE record_id=?",arrayOf(id)).use { if(it.moveToFirst()) it.getString(0) else null }
    private fun remember(r:WatchRecord,item:CatalogueItem) {
        val keys=listOfNotNull(RecognitionEngine.key(r.source,r.title,r.subtitle,item.type),if(item.type=="movie") ref(r.id) else null).distinct()
        for(key in keys) {
            // A correction replaces a previously learned mapping for the exact same evidence.
            writableDatabase.insertWithOnConflict("learned",null,ContentValues().apply {
                put("key",key); put("type",item.type); put("trakt_id",item.id); put("label",item.label()); put("show_id",item.showId); put("season",item.season); put("episode",item.episode); put("runtime",item.runtimeMinutes); put("title",item.title); put("year",item.year)
            },SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
    private fun forget(r:WatchRecord) {
        val keys=listOfNotNull(RecognitionEngine.key(r.source,r.title,r.subtitle,"movie"),RecognitionEngine.key(r.source,r.title,r.subtitle,"episode"),ref(r.id))
        for(key in keys) writableDatabase.delete("learned","key=?",arrayOf(key))
    }
    fun clearLearning() { writableDatabase.delete("learned",null,null) }
    fun learned(r:WatchRecord):CatalogueItem? {
        val keys=listOfNotNull(ref(r.id),RecognitionEngine.key(r.source,r.title,r.subtitle,"episode"),RecognitionEngine.key(r.source,r.title,r.subtitle,"movie"))
        val matches=keys.mapNotNull { key -> readableDatabase.rawQuery("SELECT type,trakt_id,title,year,show_id,season,episode,runtime FROM learned WHERE key=?",arrayOf(key)).use { c ->
            if(!c.moveToFirst()) null else CatalogueItem(c.getString(0),c.getLong(1),c.getString(2),c.getInt(3),c.getLong(4),c.getInt(5),c.getInt(6),c.getInt(7))
        } }.filter { item ->
            val ep=RecognitionEngine.episodeOf(r.title+" "+r.subtitle)
            if(item.type=="episode") ep!=null && ep.season==item.season && ep.episode==item.episode else ep==null
        }.distinctBy { it.type+":"+it.id }
        return matches.singleOrNull()
    }
    fun applyLearned(limit:Int=20):Int {
        var count=0
        for(r in records(100).filter { it.status=="pending" && it.traktId==0L }) {
            if(count>=limit) break
            val item=learned(r) ?: continue
            match(r.id,item,false); count++
        }
        return count
    }
    fun enqueue(id: String, account: String, rewatch:Boolean=false, expectedTitle:Long=0, expectedType:String="", expectedEnd:Long=0): Boolean {
        require(account.isNotBlank())
        val db=writableDatabase
        db.beginTransaction()
        try {
            val r=record(id) ?: return false
            if(r.status!="reviewed" || r.traktId<=0 || r.matchType !in setOf("movie","episode")) return false
            if(expectedTitle>0 && (r.traktId!=expectedTitle || r.matchType!=expectedType || r.ended!=expectedEnd)) return false
            val sql="SELECT 1 FROM viewing WHERE id<>? AND match_type=? AND trakt_id=? AND sync_account=? AND "+if(rewatch) "(status IN ('queued','uncertain','blocked') OR (status='synced' AND ABS(ended-?)<120000)) LIMIT 1" else "status IN ('queued','uncertain','blocked','synced') LIMIT 1"
            val args=if(rewatch) arrayOf(id,r.matchType,r.traktId.toString(),account,r.ended.toString()) else arrayOf(id,r.matchType,r.traktId.toString(),account)
            val exists=db.rawQuery(sql,args).use { it.moveToFirst() }
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
    // Compare the exact approved snapshot so cancel/re-match/re-approve cannot race a worker.
    private fun approvalArgs(r: WatchRecord, account: String) = arrayOf(r.id,account,r.matchType,r.traktId.toString(),r.ended.toString())
    private val approvalWhere = "id=? AND sync_account=? AND match_type=? AND trakt_id=? AND ended=?"
    fun beginApprovedUpload(r: WatchRecord, account: String): Boolean =
        writableDatabase.update("viewing",ContentValues().apply {
            put("status","uncertain"); put("sync_message","Upload in progress or outcome unknown")
        },approvalWhere+" AND status='queued'",approvalArgs(r,account))==1
    fun updateApprovedState(r: WatchRecord, account: String, expected: String, status: String, message: String): Boolean {
        require(expected in setOf("queued","uncertain") && status in setOf("queued","uncertain","blocked","synced"))
        return writableDatabase.update("viewing",ContentValues().apply {
            put("status",status); put("sync_message",message)
        },approvalWhere+" AND status=?",approvalArgs(r,account)+expected)==1
    }
    fun retryBlocked(id: String) {
        writableDatabase.update("viewing",ContentValues().apply { put("status","queued"); put("sync_message","Retry requested") },"id=? AND status='blocked'",arrayOf(id))
    }
    fun cancelQueued(id: String) {
        writableDatabase.update("viewing",ContentValues().apply { put("status","reviewed"); put("sync_account",""); put("sync_message","") },"id=? AND status IN ('queued','blocked')",arrayOf(id))
    }
    fun addManualLog(source:String,title:String,ended:Long):String {
        require(title.isNotBlank() && ended>0 && ended<=System.currentTimeMillis())
        val id="manual:"+UUID.randomUUID()
        val values=ContentValues().apply {
            put("id",id); put("source",source); put("title",title); put("subtitle","Manual watch log; sampled time unknown")
            put("started",ended); put("ended",ended); put("watched",0L); put("duration",0L); put("position",-1L); put("status","pending"); put("notified",1)
        }
        check(writableDatabase.insertWithOnConflict("viewing",null,values,SQLiteDatabase.CONFLICT_ABORT)!=-1L)
        return id
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
    fun importViewing(rows:List<ImportedViewing>,zone:java.time.ZoneId=java.time.ZoneId.systemDefault()):Pair<Int,Int> {
        val db=writableDatabase
        var added=0
        var duplicates=0
        db.beginTransaction()
        try {
            for(row in rows) {
                // Date-only records use local midnight as a storage anchor, not an observed playback time.
                val anchor=row.timestamp ?: row.day.atStartOfDay(zone).toInstant().toEpochMilli()
                val values=ContentValues().apply {
                    put("id",row.id); put("source",row.source); put("title",row.title); put("subtitle",row.detail+" • "+row.day)
                    put("started",anchor); put("ended",anchor); put("watched",0L); put("duration",0L); put("position",-1L); put("status","pending"); put("notified",1)
                }
                if(db.insertWithOnConflict("viewing",null,values,SQLiteDatabase.CONFLICT_IGNORE)==-1L) duplicates++ else added++
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return added to duplicates
    }
    fun reconcileImport(importId:String,localId:String):Boolean {
        val db=writableDatabase
        db.beginTransaction()
        try {
            val imported=record(importId) ?: return false
            val local=record(localId) ?: return false
            if(!importId.startsWith("import:") || localId.startsWith("import:") || imported.status!="pending" || imported.traktId>0 || local.status=="active" || imported.source!=local.source) return false
            // Keep the import ID as a hidden tombstone so re-importing the file cannot recreate it.
            val ok=db.update("viewing",ContentValues().apply { put("status","reconciled"); put("sync_message","Reconciled to "+localId) },"id=? AND status='pending'",arrayOf(importId))==1
            db.setTransactionSuccessful()
            return ok
        } finally { db.endTransaction() }
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
    fun trash(id:String):Boolean = writableDatabase.update("viewing",ContentValues().apply { put("status","trashed"); put("sync_message","Deleted locally") },"id=? AND status IN ('pending','reviewed')",arrayOf(id))==1
    fun trashed():List<WatchRecord> { val ids=mutableListOf<String>(); readableDatabase.rawQuery("SELECT id FROM viewing WHERE status='trashed' ORDER BY ended DESC",null).use { c -> while(c.moveToNext()) ids.add(c.getString(0)) }; return ids.mapNotNull { record(it) } }
    fun restoreTrash(id:String):Boolean {
        val r=record(id) ?: return false
        return writableDatabase.update("viewing",ContentValues().apply { put("status",if(r.traktId>0) "reviewed" else "pending"); put("sync_message",""); put("notified",1) },"id=? AND status='trashed'",arrayOf(id))==1
    }
    fun discard(id: String) { writableDatabase.delete("viewing", "id=? AND status IN ('pending','reviewed')", arrayOf(id)) }
}
