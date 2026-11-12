package com.traktbridge.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

data class CatalogueItem(val type: String, val id: Long, val title: String, val year: Int = 0, val showId: Long = 0, val season: Int = -1, val episode: Int = -1) {
    fun label(): String = if (type == "episode") title + " • S" + season + " E" + episode else title + (if (year > 0) " (" + year + ")" else "") + " • " + type
}
data class CatalogueSeason(val number: Int, val title: String)

object TraktCatalogue {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private fun get(path: String): JSONArray {
        val c = URL("https://api.trakt.tv" + path).openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.setRequestProperty("trakt-api-key", TraktAuth.CLIENT_ID)
            c.setRequestProperty("trakt-api-version", "2")
            c.setRequestProperty("Accept", "application/json")
            val status = c.responseCode
            DebugTrace.event("catalogue","HTTP "+status)
            if (status !in 200..299) error(if (status == 429) "Trakt rate limit reached. Try again later." else "Trakt catalogue HTTP " + status)
            return JSONArray(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    }
    private fun <T> request(done: (Result<T>) -> Unit, work: () -> T) {
        worker.execute { val result = runCatching(work); main.post { done(result) } }
    }
    private fun objects(array: JSONArray): List<JSONObject> = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    private fun id(o: JSONObject): Long = o.optJSONObject("ids")?.optLong("trakt", 0) ?: 0
    fun search(query: String, type: String, done: (Result<List<CatalogueItem>>) -> Unit) = request(done) {
        require(type == "movie" || type == "show")
        require(query.isNotBlank())
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        objects(get("/search/" + type + "?query=" + q + "&limit=30")).mapNotNull { row ->
            val o = row.optJSONObject(type) ?: return@mapNotNull null
            val id = id(o); val title = o.optString("title")
            if (id <= 0 || title.isBlank()) null else CatalogueItem(type,id,title,o.optInt("year"))
        }.distinctBy { it.id }
    }
    fun seasons(show: CatalogueItem, done: (Result<List<CatalogueSeason>>) -> Unit) = request(done) {
        require(show.type == "show" && show.id > 0)
        objects(get("/shows/" + show.id + "/seasons")).mapNotNull { o ->
            val n = o.optInt("number",-1)
            if (n < 0) null else CatalogueSeason(n,o.optString("title"))
        }.distinctBy { it.number }.sortedBy { it.number }
    }
    fun episodes(show: CatalogueItem, season: Int, done: (Result<List<CatalogueItem>>) -> Unit) = request(done) {
        require(show.type == "show" && show.id > 0 && season >= 0)
        objects(get("/shows/" + show.id + "/seasons/" + season)).mapNotNull { o ->
            val n = o.optInt("number",-1); val eid = id(o)
            if (n <= 0 || eid <= 0) null else CatalogueItem("episode",eid,show.title + " — " + o.optString("title","Episode " + n),show.year,show.id,season,n)
        }.distinctBy { it.id }.sortedBy { it.episode }
    }
    fun suggestedQuery(title: String): String = TitleNormalizer.suggestedQuery(title)
}
