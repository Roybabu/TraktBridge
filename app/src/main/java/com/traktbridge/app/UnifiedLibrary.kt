package com.traktbridge.app

import java.util.Locale

// AniList and Trakt IDs have separate namespaces. Never infer equivalence from titles.
data class LocalAnimeEntry(val id:Int,val title:String,val progress:Int,val episodes:Int) {
    val valid get()=id>0 && title.isNotBlank() && title.length<=512 && episodes in 0..10000 && progress in 0..(if(episodes>0) episodes else 10000)
}
data class LibraryOverview(val movies:List<ViewingTitle>,val series:List<SeriesGroup>,val anime:List<LocalAnimeEntry>,val unidentified:List<ViewingTitle>)
object UnifiedLibrary {
    fun build(records:List<WatchRecord>,anime:List<LocalAnimeEntry>,query:String=""):LibraryOverview {
        val q=query.trim().lowercase(Locale.ROOT)
        fun matches(label:String)=q.isBlank() || label.lowercase(Locale.ROOT).contains(q)
        val groups=ViewingGroups.build(records)
        val series=SeriesLibrary.build(groups)
        return LibraryOverview(
            groups.filter { it.representative.matchType=="movie" && it.representative.traktId>0 && matches(it.representative.matchLabel.ifBlank { it.representative.title }) },
            series.filter { matches(it.title) || it.episodes.any { e -> matches(e.representative.matchLabel) } },
            anime.filter { it.valid && matches(it.title) }.distinctBy { it.id }.sortedBy { it.title.lowercase(Locale.ROOT) },
            groups.filter { g -> val r=g.representative; !(r.matchType=="movie" && r.traktId>0) && !(r.matchType=="episode" && r.traktId>0 && r.showId>0 && r.season>=0 && r.episode>0) && matches(r.title) }
        )
    }
}
