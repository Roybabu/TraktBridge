package com.traktbridge.app

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import org.json.JSONArray

/** Derived encrypted, versioned snapshot. Original per-event cache remains authoritative.
 * Batches amortize Android Keystore operations without persisting plaintext or changing account data. */
object PersistentHistorySnapshot {
    private const val SCHEMA=1
    private fun file(c:Context)=AtomicFile(File(c.filesDir,"trakt-history-index-v1"))
    private fun scope(account:String,generation:Long,status:RemoteAccountCache.Status,index:Int)="history-index:"+AccountScope.key(account)+":"+generation+":"+status.updated+":"+status.count+":"+index
    fun clear(c:Context) { synchronized(RemoteAccountCache) { file(c).delete() } }
    fun read(c:Context,account:String,generation:Long,status:RemoteAccountCache.Status):List<RemoteItem>? = runCatching {
        val rows=ArrayList<RemoteItem>(status.count.coerceIn(0,250000))
        DataInputStream(file(c).openRead().buffered()).use { input ->
            require(input.readInt()==SCHEMA && input.readLong()==generation && input.readLong()==status.updated && input.readInt()==status.count)
            val chunks=input.readInt()
            require(chunks in 0..250000)
            repeat(chunks) { index ->
                if(Thread.currentThread().isInterrupted) throw InterruptedException()
                val size=input.readInt(); require(size in 1..100000)
                val bytes=ByteArray(size); input.readFully(bytes)
                val array=JSONArray(PrivateHistoryCache.open(String(bytes,Charsets.UTF_8),scope(account,generation,status,index)))
                for(i in 0 until array.length()) rows.add(RemoteAccountCache.decode(array.getString(i)))
                require(rows.size<=status.count)
            }
            require(input.read()==-1)
        }
        require(rows.size==status.count && RemoteAccountCache.current(c,account,generation) && RemoteAccountCache.status(c,account,TraktData.HISTORY)==status)
        rows
    }.getOrNull()
    fun write(c:Context,account:String,generation:Long,status:RemoteAccountCache.Status,rows:List<RemoteItem>) {
        require(rows.size==status.count && status.complete)
        val chunks=mutableListOf<String>()
        var batch=JSONArray()
        for(row in rows) {
            if(Thread.currentThread().isInterrupted) throw InterruptedException()
            val value=RemoteAccountCache.encode(row)
            val candidate=JSONArray(batch.toString()).put(value)
            if(candidate.toString().length>15000 && batch.length()>0) { chunks.add(batch.toString()); batch=JSONArray().put(value) } else batch=candidate
        }
        if(batch.length()>0) chunks.add(batch.toString())
        val encrypted=chunks.mapIndexed { index,value -> PrivateHistoryCache.seal(value,scope(account,generation,status,index)).toByteArray(Charsets.UTF_8) }
        synchronized(RemoteAccountCache) {
            if(!RemoteAccountCache.current(c,account,generation) || RemoteAccountCache.status(c,account,TraktData.HISTORY)!=status) return
            val target=file(c); val stream=target.startWrite()
            try {
                val output=DataOutputStream(stream.buffered())
                output.writeInt(SCHEMA); output.writeLong(generation); output.writeLong(status.updated); output.writeInt(status.count); output.writeInt(encrypted.size)
                for(bytes in encrypted) { output.writeInt(bytes.size); output.write(bytes) }
                output.flush(); target.finishWrite(stream)
            } catch(e:Throwable) { target.failWrite(stream); throw e }
        }
    }
}
