package com.traktbridge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

object Artwork {
    private val worker=java.util.concurrent.ThreadPoolExecutor(4,4,30,java.util.concurrent.TimeUnit.SECONDS,java.util.concurrent.ArrayBlockingQueue<Runnable>(48),java.util.concurrent.ThreadPoolExecutor.AbortPolicy())
    private val main=Handler(Looper.getMainLooper())
    private data class Target(val view:java.lang.ref.WeakReference<ImageView>,val key:String)
    private val waiting=mutableMapOf<String,MutableList<Target>>()
    private val failures=mutableMapOf<String,Long>()
    private var memory:android.util.LruCache<String,Bitmap>?=null
    @Synchronized private fun cache(context:Context):android.util.LruCache<String,Bitmap> {
        memory?.let { return it }
        val am=context.getSystemService(android.app.ActivityManager::class.java)
        return object:android.util.LruCache<String,Bitmap>(MemoryPolicy.artworkKiB(am.memoryClass,am.isLowRamDevice)) { override fun sizeOf(key:String,value:Bitmap)=(value.allocationByteCount/1024).coerceAtLeast(1) }.also { memory=it }
    }
    @Synchronized fun trimMemory() { memory?.evictAll() }
    private fun decode(file:File):Bitmap? {
        if(!file.isFile) return null
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(file.path,bounds)
        if(bounds.outWidth !in 1..4096 || bounds.outHeight !in 1..4096 || bounds.outWidth.toLong()*bounds.outHeight>8000000) return null
        return BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=ArtworkSizing.sample(bounds.outWidth,bounds.outHeight) })
    }
    @Synchronized private fun prune(dir:File) {
        val files=dir.listFiles()?.filter { it.extension=="webp" }?.sortedByDescending { it.lastModified() }.orEmpty()
        val remove=ResourcePolicy.evictions(files.map { it.name to it.length() })
        files.filter { it.name in remove }.forEach { it.delete() }
    }
    fun clear(context:Context) {
        trimMemory()
        main.post { failures.clear() }
        val dir=File(context.cacheDir,"artwork")
        runCatching { worker.execute { synchronized(this) { dir.listFiles()?.filter { it.extension=="webp" }?.forEach { it.delete() } } } }
    }
    private fun placeholder(view:ImageView) {
        view.setImageResource(android.R.drawable.ic_menu_gallery)
        view.scaleType=ImageView.ScaleType.CENTER_INSIDE
        view.imageAlpha=90
    }
    private fun display(view:ImageView,bitmap:Bitmap) {
        view.scaleType=ImageView.ScaleType.CENTER_CROP
        view.imageAlpha=255
        view.setImageBitmap(bitmap)
    }
    fun load(context:Context,type:String,id:Long,view:ImageView) {
        val app=context.applicationContext
        val key=type+"-"+id
        if(view.tag!=key) { view.tag=key; placeholder(view) }
        if(id<=0 || type !in setOf("movie","show")) return
        cache(app).get(key)?.let { display(view,it); return }
        val ref=java.lang.ref.WeakReference(view)
        val next=failures[key] ?: 0
        if(next>android.os.SystemClock.elapsedRealtime()) {
            retry(app,type,id,key,ref,next-android.os.SystemClock.elapsedRealtime())
            return
        }
        waiting[key]?.let { targets ->
            targets.removeAll { it.view.get()==null }
            if(targets.none { it.view.get()===view }) targets.add(Target(ref,key))
            return
        }
        waiting[key]=mutableListOf(Target(ref,key))
        try {
            worker.execute {
                val result=runCatching { fetch(app,type,id,key) }
                val bitmap=result.getOrNull()
                if(bitmap!=null) cache(app).put(key,bitmap)
                main.post {
                    val targets=waiting.remove(key).orEmpty()
                    if(bitmap!=null) {
                        failures.remove(key)
                        targets.forEach { target -> target.view.get()?.takeIf { it.tag==key }?.let { display(it,bitmap) } }
                    } else {
                        if(failures.size>=1000) failures.clear()
                        failures[key]=android.os.SystemClock.elapsedRealtime()+60000
                        targets.forEach { target -> retry(app,type,id,key,target.view,60000) }
                        result.exceptionOrNull()?.let { DebugTrace.failure("artwork",it) }
                    }
                }
            }
        } catch(_:java.util.concurrent.RejectedExecutionException) {
            waiting.remove(key)
            retry(app,type,id,key,ref,2500)
        }
    }
    private fun retry(context:Context,type:String,id:Long,key:String,ref:java.lang.ref.WeakReference<ImageView>,delay:Long) {
        main.postDelayed({
            ref.get()?.takeIf { it.tag==key && it.isAttachedToWindow && it.isShown }?.let { load(context,type,id,it) }
        },delay.coerceAtLeast(1000))
    }
    private fun fetch(app:Context,type:String,id:Long,key:String):Bitmap? {
        val dir=File(app.cacheDir,"artwork").apply { mkdirs() }
        val file=File(dir,key+".webp")
        val existing=decode(file)
        if(existing!=null) { file.setLastModified(System.currentTimeMillis()); return existing }
        val cm=app.getSystemService(android.net.ConnectivityManager::class.java)
        if(app.getSharedPreferences("tracker",Context.MODE_PRIVATE).getBoolean("artwork_unmetered",false) && cm.isActiveNetworkMetered) return null
        val endpoint=if(type=="movie") "movies" else "shows"
        val connection=URL("https://api.trakt.tv/"+endpoint+"/"+id+"?extended=full,images").openConnection() as HttpsURLConnection
        val data=try {
            connection.connectTimeout=8000; connection.readTimeout=8000; connection.instanceFollowRedirects=false
            connection.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID); connection.setRequestProperty("trakt-api-version","2"); connection.setRequestProperty("Accept","application/json")
            if(connection.responseCode!=200) error("Artwork metadata HTTP "+connection.responseCode)
            JSONObject(connection.inputStream.bufferedReader().use { reader ->
                val out=StringBuilder(); val buf=CharArray(8192)
                while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=2_000_000); out.append(buf,0,n) }
                out.toString()
            })
        } finally { connection.disconnect() }
        val posters=data.optJSONObject("images")?.optJSONArray("poster")
        val raw=(0 until (posters?.length() ?: 0)).map { posters?.optString(it).orEmpty() }.firstOrNull { it.isNotBlank() } ?: error("No catalogue poster")
        var url=URL(if(raw.startsWith("https://")) raw else "https://"+raw)
        val temp=File.createTempFile("art",".tmp",dir)
        try {
            repeat(4) {
                require(url.protocol=="https" && (url.host=="trakt.tv" || url.host.endsWith(".trakt.tv")) && url.userInfo==null)
                val image=url.openConnection() as HttpsURLConnection
                try {
                    image.connectTimeout=8000; image.readTimeout=8000; image.instanceFollowRedirects=false
                    val code=image.responseCode
                    if(code in 300..399) {
                        url=URL(url,image.getHeaderField("Location") ?: error("Missing image redirect"))
                    } else {
                        if(code!=200) error("Poster HTTP "+code)
                        image.inputStream.use { input -> temp.outputStream().use { output ->
                            val buffer=ByteArray(8192); var total=0
                            while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=2*1024*1024); output.write(buffer,0,n) }
                        } }
                        val bitmap=decode(temp) ?: error("Invalid poster image")
                        if(file.exists()) file.delete()
                        check(temp.renameTo(file))
                        prune(dir)
                        return bitmap
                    }
                } finally { image.disconnect() }
            }
            error("Too many poster redirects")
        } finally { temp.delete() }
    }
}
