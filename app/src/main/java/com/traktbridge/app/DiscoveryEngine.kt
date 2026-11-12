package com.traktbridge.app

data class DiscoveryItem(val item:CatalogueItem,val genres:Set<String>,val rating:Double)
data class DiscoveryMood(val label:String,val genres:Set<String>)
object DiscoveryEngine {
    val moods=listOf(
        DiscoveryMood("Feel good",setOf("comedy","family","animation","romance")),
        DiscoveryMood("Thrilling",setOf("thriller","mystery","crime","action")),
        DiscoveryMood("Adventurous",setOf("adventure","action","fantasy","science-fiction")),
        DiscoveryMood("Thought provoking",setOf("documentary","drama","history","science-fiction")),
        DiscoveryMood("Spooky",setOf("horror","thriller","mystery")),
        DiscoveryMood("Any mood",emptySet())
    )
    fun filter(rows:List<DiscoveryItem>,mood:DiscoveryMood,maxMinutes:Int=0,minRating:Double=0.0,excluded:Set<String> = emptySet()):List<DiscoveryItem> =
        rows.filter { row ->
            val item=row.item
            item.id>0 && item.type in setOf("movie","show") && item.title.isNotBlank() &&
                item.type+":"+item.id !in excluded &&
                (mood.genres.isEmpty() || row.genres.any { it in mood.genres }) &&
                (maxMinutes<=0 || item.runtimeMinutes in 1..maxMinutes) &&
                (minRating<=0 || row.rating.isFinite() && row.rating in minRating..10.0)
        }.distinctBy { it.item.type+":"+it.item.id }.take(40)
}
object ArtworkSizing {
    fun sample(width:Int,height:Int):Int {
        require(width in 1..4096 && height in 1..4096 && width.toLong()*height<=8000000)
        var size=1
        while((width+size-1)/size>768 || (height+size-1)/size>1024) size*=2
        return size
    }
}
