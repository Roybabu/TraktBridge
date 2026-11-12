package com.traktbridge.app
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object UpdateCheck {
    fun compare(a:String,b:String):Int {
        fun parts(s:String)=s.removePrefix("v").split(".").map { it.toIntOrNull() ?: -1 }
        val x=parts(a); val y=parts(b)
        require(x.size==3 && y.size==3 && (x+y).all { it>=0 })
        for(i in 0..2) if(x[i]!=y[i]) return x[i].compareTo(y[i])
        return 0
    }
    fun check(done:(Result<String>)->Unit) {
        Thread {
            val result=runCatching {
                val c=URL("https://raw.githubusercontent.com/Roybabu/TraktBridge/main/version.properties").openConnection() as HttpsURLConnection
                try {
                    c.connectTimeout=10000; c.readTimeout=10000; c.instanceFollowRedirects=false
                    require(c.responseCode==200)
                    val lines=c.inputStream.bufferedReader().use { it.readText() }.lines()
                    val latest=lines.first { it.startsWith("VERSION_NAME=") }.substringAfter("=").trim()
                    require(latest.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
                    latest
                } finally { c.disconnect() }
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.start()
    }
}
