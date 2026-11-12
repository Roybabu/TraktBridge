package com.traktbridge.app

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.Executors
import org.json.JSONObject

object Artwork {
    private val worker=Executors.newFixedThreadPool(2)
    private val main=Handler(Looper.getMainLooper())
    private val attempted=mutableMapOf<String,Long>()
    fun load(context:Context,type:String,id:Long,view:ImageView) {
        if(id<=0 || type !in setOf("movie","show")) return
        val key=type+"-"+id
        val dir=File(context.applicationContext.cacheDir,"artwork")
        val file=File(dir,key+".webp")
        val target=java.lang.ref.WeakReference(view)
        worker.execute {
            val bitmap=runCatching {
                dir.mkdirs()
                if(!file.exists() || System.currentTimeMillis()-file.lastModified()>7*86400000L) {
                    val fetch=synchronized(attempted) {
                        val now=System.currentTimeMillis()
                        if(now-(attempted[key] ?: 0)<60000) false else { attempted[key]=now; true }
                    }
                    if(fetch) {
                        val endpoint=if(type=="movie") "movies" else "shows"
                        val connection=URL("https://api.trakt.tv/"+endpoint+"/"+id+"?extended=full").openConnection() as HttpsURLConnection
                        val data=try {
                            connection.connectTimeout=10000; connection.readTimeout=10000; connection.instanceFollowRedirects=false
                            connection.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID); connection.setRequestProperty("trakt-api-version","2")
                            if(connection.responseCode!=200) error("Artwork metadata unavailable")
                            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        } finally { connection.disconnect() }
                        val raw=data.optJSONObject("images")?.optJSONArray("poster")?.optString(0).orEmpty()
                        if(raw.isBlank()) error("No poster")
                        val url=URL(if(raw.startsWith("https://")) raw else "https://"+raw)
                        require(url.protocol=="https" && url.host.endsWith(".trakt.tv") && url.userInfo==null)
                        val image=url.openConnection() as HttpsURLConnection
                        val temp=File.createTempFile("art",".tmp",dir)
                        try {
                            image.connectTimeout=10000; image.readTimeout=10000; image.instanceFollowRedirects=false
                            if(image.responseCode!=200) error("Poster unavailable")
                            image.inputStream.use { input -> temp.outputStream().use { output ->
                                val buffer=ByteArray(8192); var total=0
                                while(true) { val n=input.read(buffer); if(n<0) break; total+=n; require(total<=2*1024*1024); output.write(buffer,0,n) }
                            } }
                            require(BitmapFactory.decodeFile(temp.path)!=null)
                            check(temp.renameTo(file))
                            dir.listFiles()?.filter { it.extension=="webp" }?.sortedByDescending { it.lastModified() }?.drop(100)?.forEach { it.delete() }
                        } finally { image.disconnect(); temp.delete() }
                    }
                }
                BitmapFactory.decodeFile(file.path)
            }.getOrNull() ?: BitmapFactory.decodeFile(file.path)
            if(bitmap!=null) main.post { target.get()?.setImageBitmap(bitmap) }
        }
    }
}
