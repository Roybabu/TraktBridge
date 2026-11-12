package com.traktbridge.app

data class SeriesSeason(val number:Int,val episodes:List<ViewingTitle>) {
    val completed=episodes.count { it.confirmed }
    val approved=episodes.count { it.queued && !it.confirmed }
    val sampledMs=episodes.sumOf { it.sampledMs }
}
data class SeriesGroup(val id:Long,val title:String,val seasons:List<SeriesSeason>) {
    val episodes=seasons.flatMap { it.episodes }
    val latest=episodes.maxOf { it.latest.ended }
    val completed=episodes.count { it.confirmed }
    val approved=episodes.count { it.queued && !it.confirmed }
    val sampledMs=episodes.sumOf { it.sampledMs }
    val platforms=episodes.flatMap { it.platforms }.distinct()
    val resume=episodes.filter { !it.confirmed && !it.queued && (it.positionMs>0 || it.sampledMs>0) }.maxByOrNull { it.latest.ended }
}
data class SeriesEpisodeRow(val item:CatalogueItem,val local:ViewingTitle?)
object SeriesLibrary {
    fun build(groups:List<ViewingTitle>):List<SeriesGroup> = groups.filter {
        val r=it.representative
        r.matchType=="episode" && r.showId>0 && r.season>=0 && r.episode>0
    }.groupBy { it.representative.showId }.map { (id,rows) ->
        val representative=rows.maxBy { it.latest.ended }.representative
        // Catalogue episode labels are generated as Show — Episode • Sx Ey.
        val title=representative.matchLabel.substringBeforeLast(" • S").substringBefore(" — ").ifBlank { representative.title }
        val seasons=rows.groupBy { it.representative.season }.map { (n,episodes) ->
            SeriesSeason(n,episodes.sortedBy { it.representative.episode })
        }.sortedBy { it.number }
        SeriesGroup(id,title,seasons)
    }.sortedByDescending { it.latest }
    fun continueWatching(groups:List<ViewingTitle>):List<ViewingTitle> = groups.filter {
        !it.active && !it.confirmed && !it.queued && (it.positionMs>0 || it.sampledMs>0)
    }.sortedByDescending { it.latest.ended }
    fun seasonRows(series:SeriesGroup,season:Int,catalogue:List<CatalogueItem>):List<SeriesEpisodeRow> {
        val local=series.episodes.associateBy { it.representative.traktId }
        return catalogue.filter { it.type=="episode" && it.showId==series.id && it.season==season }
            .distinctBy { it.id }.sortedBy { it.episode }.map { SeriesEpisodeRow(it,local[it.id]) }
    }
    // Only suggest the first unconfirmed regular episode in the loaded catalogue.
    // Do not infer episodes beyond a season or treat a queued upload as completion.
    fun nextEpisode(rows:List<SeriesEpisodeRow>):SeriesEpisodeRow? =
        rows.filter { it.item.season>0 }.firstOrNull { it.local?.confirmed!=true && it.local?.queued!=true }
}
