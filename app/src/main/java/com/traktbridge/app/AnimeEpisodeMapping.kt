package com.traktbridge.app

object AnimeEpisodeMapping {
    private val absolute=Regex("(?i)\\b(?:absolute(?: episode)?|episode|ep\\.?)\\s*#?\\s*(\\d{1,4})\\b")
    private val special=Regex("(?i)\\b(?:special|ova|oad)\\s*#?\\s*(\\d{1,3})\\b")
    private val range=Regex("(?i)\\b(?:episode|ep\\.?|absolute(?: episode)?)\\s*#?\\s*\\d{1,4}\\s*[-–/&+]\\s*\\d+")
    fun absoluteNumber(text:String):Int? {
        if(RecognitionEngine.episodeOf(text)!=null || RecognitionEngine.ambiguousEpisode(text) || range.containsMatchIn(text) || special.containsMatchIn(text)) return null
        return absolute.findAll(text).map { it.groupValues[1].toInt() }.distinct().toList().singleOrNull()?.takeIf { it>0 }
    }
    fun specialNumber(text:String):EpisodeNumber? {
        if(range.containsMatchIn(text) || RecognitionEngine.ambiguousEpisode(text)) return null
        val n=special.findAll(text).map { it.groupValues[1].toInt() }.distinct().toList().singleOrNull()?.takeIf { it>0 } ?: return null
        val explicit=RecognitionEngine.episodeOf(text)
        return EpisodeNumber(0,n).takeIf { explicit==null || explicit==it }
    }
    // Only a complete, contiguous catalogue is safe for ordinal mapping. Specials never count.
    fun ordinal(showId:Long,number:Int,seasons:List<List<CatalogueItem>>):CatalogueItem? {
        if(showId<=0 || number<=0 || seasons.isEmpty() || seasons.size>100) return null
        val rows=seasons.flatten()
        if(rows.size>10000 || rows.any { it.type!="episode" || it.id<=0 || it.showId!=showId || it.season<0 || it.episode<=0 }) return null
        val regular=rows.filter { it.season>0 }
        val grouped=regular.groupBy { it.season }.toSortedMap()
        if(grouped.keys.toList()!=(1..grouped.size).toList()) return null
        if(grouped.values.any { group -> group.map { it.episode }.sorted()!=(1..group.size).toList() } || regular.map { it.id }.distinct().size!=regular.size) return null
        return regular.sortedWith(compareBy<CatalogueItem> { it.season }.thenBy { it.episode }).getOrNull(number-1)
    }
}
