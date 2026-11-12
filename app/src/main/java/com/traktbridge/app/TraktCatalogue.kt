package com.traktbridge.app

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

data class CatalogueItem(val type: String, val id: Long, val title: String, val year: Int = 0, val showId: Long = 0, val season: Int = -1, val episode: Int = -1, val runtimeMinutes: Int = 0) {
    fun label(): String = if (type == "episode") title + " • S" + season + " E" + episode else title + (if (year > 0) " (" + year + ")" else "") + " • " + type
}
data class TitleDetails(val title:String,val overview:String,val year:Int,val runtime:Int,val rating:Double,val genres:String,val trailer:String,val released:String)
data class CatalogueSeason(val number: Int, val title: String)

object TraktCatalogue {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private class ReadFailure(val status:Int,val retryAfter:Long?):Exception("Trakt catalogue HTTP "+status)
    private fun get(path:String):JSONArray {
        var attempt=0
        while(true) {
            try { return getOnce(path) } catch(e:ReadFailure) {
                val delay=ApiRetryPolicy.delay(e.status,attempt,e.retryAfter) ?: throw e
                attempt++; Thread.sleep(delay)
            }
        }
    }
    private fun getOnce(path: String): JSONArray {
        val c = URL("https://api.trakt.tv" + path).openConnection() as HttpsURLConnection
        try {
            c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = false
            c.setRequestProperty("trakt-api-key", TraktAuth.CLIENT_ID)
            c.setRequestProperty("trakt-api-version", "2")
            c.setRequestProperty("Accept", "application/json")
            val status = c.responseCode
            DebugTrace.event("catalogue","HTTP "+status)
            if (status !in 200..299) throw ReadFailure(status,c.getHeaderField("Retry-After")?.toLongOrNull())
            return JSONArray(c.inputStream.bufferedReader().use { reader -> val out=StringBuilder(); val buf=CharArray(8192); while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=8_000_000) { "Catalogue response too large" }; out.append(buf,0,n) }; out.toString() })
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
        objects(get("/search/" + type + "?query=" + q + "&limit=30&extended=full")).mapNotNull { row ->
            val o = row.optJSONObject(type) ?: return@mapNotNull null
            val id = id(o); val title = o.optString("title")
            if (id <= 0 || title.isBlank()) null else CatalogueItem(type,id,title,o.optInt("year"),runtimeMinutes=o.optInt("runtime"))
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
        objects(get("/shows/" + show.id + "/seasons/" + season + "?extended=full")).mapNotNull { o ->
            val n = o.optInt("number",-1); val eid = id(o)
            if (n <= 0 || eid <= 0) null else CatalogueItem("episode",eid,show.title + " — " + o.optString("title","Episode " + n),show.year,show.id,season,n,o.optInt("runtime"))
        }.distinctBy { it.id }.sortedBy { it.episode }
    }
    fun people(query:String,done:(Result<List<Pair<Long,String>>>)->Unit)=request(done) {
        require(query.isNotBlank() && query.length<=200)
        objects(get("/search/person?query="+URLEncoder.encode(query,"UTF-8")+"&limit=25")).mapNotNull { row ->
            val person=row.optJSONObject("person") ?: return@mapNotNull null
            val key=id(person); val name=person.optString("name")
            if(key<=0 || name.isBlank()) null else key to name
        }.distinctBy { it.first }
    }
    fun filmography(person:Long,done:(Result<List<CatalogueItem>>)->Unit)=request(done) {
        require(person>0)
        val c=URL("https://api.trakt.tv/people/"+person+"/movies?extended=full").openConnection() as HttpsURLConnection
        try {
            c.connectTimeout=15000; c.readTimeout=15000; c.instanceFollowRedirects=false
            c.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID); c.setRequestProperty("trakt-api-version","2")
            require(c.responseCode in 200..299)
            val o=JSONObject(c.inputStream.bufferedReader().use { reader -> val out=StringBuilder(); val buf=CharArray(8192); while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=8_000_000); out.append(buf,0,n) }; out.toString() })
            val rows=objects(o.optJSONArray("cast") ?: JSONArray())+objects(o.optJSONObject("crew")?.optJSONArray("directing") ?: JSONArray())
            rows.mapNotNull { row -> val movie=row.optJSONObject("movie") ?: return@mapNotNull null; val key=id(movie); if(key<=0) null else CatalogueItem("movie",key,movie.optString("title"),movie.optInt("year"),runtimeMinutes=movie.optInt("runtime")) }.distinctBy { it.id }.sortedByDescending { it.year }
        } finally { c.disconnect() }
    }
    fun searchExternal(id:String,namespace:String,done:(Result<List<CatalogueItem>>)->Unit)=request(done) {
        require(namespace in setOf("imdb","tmdb") && id.matches(if(namespace=="imdb") Regex("tt[0-9]{5,12}") else Regex("[0-9]{1,12}")))
        objects(get("/search/"+namespace+"/"+id+"?type=movie,show&extended=full")).mapNotNull { row ->
            val type=row.optString("type")
            if(type !in setOf("movie","show")) return@mapNotNull null
            val o=row.optJSONObject(type) ?: return@mapNotNull null
            val key=id(o); val title=o.optString("title")
            if(key<=0 || title.isBlank()) null else CatalogueItem(type,key,title,o.optInt("year"),runtimeMinutes=o.optInt("runtime"))
        }.distinctBy { it.type+":"+it.id }
    }
    private fun relatedItems(type:String,id:Long):List<CatalogueItem> {
        require(type in setOf("movie","show") && id>0)
        return objects(get("/"+RemoteRules.plural(type)+"/"+id+"/related?limit=30&extended=full")).mapNotNull { o ->
            val key=id(o); val title=o.optString("title")
            if(key<=0 || title.isBlank()) null else CatalogueItem(type,key,title,o.optInt("year"),runtimeMinutes=o.optInt("runtime"))
        }.distinctBy { it.id }
    }
    fun related(type:String,id:Long,done:(Result<List<CatalogueItem>>)->Unit)=request(done) { relatedItems(type,id) }
    fun personalized(seeds:List<RecommendationSeed>,done:(Result<List<RelatedBatch>>)->Unit)=request(done) {
        val batches=seeds.take(5).mapNotNull { seed -> try { RelatedBatch(seed,relatedItems(seed.type,seed.id)) } catch(e:Exception) { DebugTrace.failure("recommendation",e); null } }
        check(batches.isNotEmpty()) { "Recommendations unavailable. Try again later." }
        batches
    }
    fun discover(type:String,mode:String,done:(Result<List<CatalogueItem>>)->Unit)=request(done) {
        require(type in setOf("movie","show") && mode in setOf("trending","popular"))
        objects(get("/"+RemoteRules.plural(type)+"/"+mode+"?limit=30&extended=full")).mapNotNull { row ->
            val o=if(mode=="trending") row.optJSONObject(type) ?: return@mapNotNull null else row
            val id=id(o); val title=o.optString("title")
            if(id<=0 || title.isBlank()) null else CatalogueItem(type,id,title,o.optInt("year"),runtimeMinutes=o.optInt("runtime"))
        }.distinctBy { it.id }
    }
    fun moodDiscovery(type:String,mood:DiscoveryMood,done:(Result<List<DiscoveryItem>>)->Unit)=request(done) {
        require(type in setOf("movie","show") && mood in DiscoveryEngine.moods)
        val suffix=if(mood.genres.isEmpty()) "" else "&genres="+URLEncoder.encode(mood.genres.sorted().joinToString(","),"UTF-8")
        val rows=objects(get("/"+RemoteRules.plural(type)+"/popular?limit=50&extended=full"+suffix))
        rows.mapNotNull { o ->
            val key=id(o); val title=o.optString("title")
            if(key<=0 || title.isBlank()) null else {
                val genres=o.optJSONArray("genres")
                DiscoveryItem(CatalogueItem(type,key,title,o.optInt("year"),runtimeMinutes=o.optInt("runtime")), (0 until (genres?.length() ?: 0)).map { genres?.optString(it).orEmpty() }.toSet(),o.optDouble("rating",0.0))
            }
        }
    }
    fun credits(type:String,id:Long,done:(Result<List<String>>)->Unit)=request(done) {
        require(type in setOf("movie","show") && id>0)
        val c=URL("https://api.trakt.tv/"+RemoteRules.plural(type)+"/"+id+"/people").openConnection() as HttpsURLConnection
        try {
            c.connectTimeout=15000; c.readTimeout=15000; c.instanceFollowRedirects=false
            c.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID); c.setRequestProperty("trakt-api-version","2")
            require(c.responseCode in 200..299)
            val o=JSONObject(c.inputStream.bufferedReader().use { reader -> val out=StringBuilder(); val buf=CharArray(8192); while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=8_000_000); out.append(buf,0,n) }; out.toString() })
            val cast=objects(o.optJSONArray("cast") ?: JSONArray()).mapNotNull { row -> row.optJSONObject("person")?.optString("name")?.takeIf { it.isNotBlank() }?.let { name -> name+" • "+row.optString("character","Cast") } }
            val directors=objects(o.optJSONObject("crew")?.optJSONArray("directing") ?: JSONArray()).mapNotNull { row -> row.optJSONObject("person")?.optString("name")?.takeIf { it.isNotBlank() }?.let { name -> name+" • "+row.optString("job","Directing") } }
            (directors+cast).distinct().take(100)
        } finally { c.disconnect() }
    }
    fun details(type:String,id:Long,done:(Result<TitleDetails>)->Unit)=request(done) {
        require(id>0 && type in setOf("movie","show"))
        val c=URL("https://api.trakt.tv/"+RemoteRules.plural(type)+"/"+id+"?extended=full").openConnection() as HttpsURLConnection
        try {
            c.connectTimeout=15000; c.readTimeout=15000; c.instanceFollowRedirects=false
            c.setRequestProperty("trakt-api-key",TraktAuth.CLIENT_ID); c.setRequestProperty("trakt-api-version","2")
            require(c.responseCode in 200..299) { "Catalogue unavailable" }
            val o=JSONObject(c.inputStream.bufferedReader().use { reader -> val out=StringBuilder(); val buf=CharArray(8192); while(true) { val n=reader.read(buf); if(n<0) break; require(out.length+n<=8_000_000) { "Catalogue response too large" }; out.append(buf,0,n) }; out.toString() })
            TitleDetails(o.optString("title"),o.optString("overview"),o.optInt("year"),o.optInt("runtime"),o.optDouble("rating",0.0),objectsGenres(o.optJSONArray("genres")),o.optString("trailer"),o.optString("released",o.optString("first_aired")))
        } finally { c.disconnect() }
    }
    private fun objectsGenres(a:JSONArray?):String=(0 until (a?.length() ?: 0)).map { a?.optString(it).orEmpty() }.joinToString(" • ")
    fun suggestedQuery(title: String): String = TitleNormalizer.suggestedQuery(title)
}
