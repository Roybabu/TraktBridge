package com.traktbridge.app
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class AnimeResult(val id:Int,val english:String,val romaji:String,val native:String,val episodes:Int,val description:String)
object AnimeCatalogue {
    fun search(title:String,done:(Result<List<AnimeResult>>)->Unit) {
        Thread {
            val result=runCatching {
                require(title.isNotBlank() && title.length<=200)
                val body=JSONObject().put("query","query (\$search: String) { Page(page: 1, perPage: 15) { media(search: \$search, type: ANIME, isAdult: false) { id title { english romaji native } episodes description(asHtml: false) } } }").put("variables",JSONObject().put("search",title))
                val c=URL("https://graphql.anilist.co").openConnection() as HttpsURLConnection
                try {
                    c.requestMethod="POST"; c.doOutput=true; c.instanceFollowRedirects=false; c.connectTimeout=10000; c.readTimeout=10000
                    c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("Accept","application/json")
                    c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    require(c.responseCode in 200..299) { "AniList request failed" }
                    val o=JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    val a=o.getJSONObject("data").getJSONObject("Page").getJSONArray("media")
                    (0 until a.length()).mapNotNull { i ->
                        val row=a.optJSONObject(i) ?: return@mapNotNull null
                        val t=row.getJSONObject("title")
                        fun title(k:String)=if(t.isNull(k)) "" else t.optString(k)
                        AnimeResult(row.getInt("id"),title("english"),title("romaji"),title("native"),row.optInt("episodes",0),row.optString("description","").replace(Regex("<[^>]*>"),""))
                    }
                } finally { c.disconnect() }
            }
            Handler(Looper.getMainLooper()).post { done(result) }
        }.start()
    }
}
