package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SeriesLibraryTest {
 private fun record(id:String,show:Long,season:Int,episode:Int,status:String="reviewed",trakt:Long=episode.toLong(),end:Long=10000L)=WatchRecord(id,"netflix","Show","S"+season+" E"+episode,1,end,5000,600000,10000,status,"episode",trakt,"Show — Episode • S"+season+" E"+episode,show,season,episode)
 @Test fun groupByShowAndSeason() {
  val groups=ViewingGroups.build(listOf(record("a",10,1,1,"synced",101),record("b",10,1,2,trakt=102),record("c",10,2,1,trakt=201),record("d",20,1,1,trakt=301)))
  val series=SeriesLibrary.build(groups).first { it.id==10L }
  assertEquals(2,series.seasons.size); assertEquals(3,series.episodes.size); assertEquals(1,series.completed)
 }
 @Test fun duplicateSessionsOneEpisode() {
  val series=SeriesLibrary.build(ViewingGroups.build(listOf(record("a",10,1,1,"synced",101),record("b",10,1,1,trakt=101)))).single()
  assertEquals(1,series.episodes.size); assertEquals(1,series.completed)
 }
 @Test fun nextEpisodeUsesCatalogueOrder() {
  val series=SeriesLibrary.build(ViewingGroups.build(listOf(record("a",10,1,1,"synced",101)))).single()
  val items=listOf(CatalogueItem("episode",102,"Show — Two",showId=10,season=1,episode=2),CatalogueItem("episode",101,"Show — One",showId=10,season=1,episode=1))
  assertEquals(102L,SeriesLibrary.nextEpisode(SeriesLibrary.seasonRows(series,1,items))?.item?.id)
 }
 @Test fun continueWatchingExcludesSyncedAndQueued() {
  val groups=ViewingGroups.build(listOf(record("a",10,1,1,"synced",101),record("b",10,1,2,"queued",102),record("c",10,1,3,trakt=103)))
  assertEquals("c",SeriesLibrary.continueWatching(groups).single().latest.id)
 }
}
