package com.traktbridge.app

object LibraryDuplicates {
    fun candidates(records:List<WatchRecord>,target:WatchRecord):List<WatchRecord> {
        if(target.traktId<=0 || target.matchType !in setOf("movie","episode") || target.title.isBlank()) return emptyList()
        val title=RecognitionEngine.normalized(RecognitionEngine.query(target.title))
        val episode=RecognitionEngine.episodeOf(target.title+" "+target.subtitle)
        if(title.isBlank() || RecognitionEngine.ambiguousEpisode(target.title+" "+target.subtitle)) return emptyList()
        if(target.matchType=="episode" && (episode==null || episode.season!=target.season || episode.episode!=target.episode || target.showId<=0)) return emptyList()
        if(target.matchType=="movie" && (episode!=null || AnimeEpisodeMapping.absoluteNumber(target.title+" "+target.subtitle)!=null || AnimeEpisodeMapping.specialNumber(target.title+" "+target.subtitle)!=null)) return emptyList()
        return records.distinctBy { it.id }.filter { row ->
            row.id!=target.id && row.status in setOf("pending","reviewed") && row.traktId<=0 &&
            row.source==target.source && !RecognitionEngine.ambiguousEpisode(row.title+" "+row.subtitle) &&
            RecognitionEngine.normalized(RecognitionEngine.query(row.title))==title &&
            RecognitionEngine.episodeOf(row.title+" "+row.subtitle)==episode &&
            (target.matchType!="movie" || (AnimeEpisodeMapping.absoluteNumber(row.title+" "+row.subtitle)==null && AnimeEpisodeMapping.specialNumber(row.title+" "+row.subtitle)==null)) &&
            (row.duration<=0 || target.duration<=0 || kotlin.math.abs(row.duration-target.duration)<=maxOf(180000L,target.duration/10))
        }.sortedByDescending { it.ended }.take(100)
    }
}
