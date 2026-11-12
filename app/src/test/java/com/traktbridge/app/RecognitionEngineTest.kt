package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class RecognitionEngineTest {
 @Test fun agreesAcrossSources() {
  val r=RecognitionEngine.fuse(listOf(Evidence("The Last of Us","","media",85),Evidence("The Last of Us","S02 E03","accessibility",65)))!!
  assertEquals("S2 E3",r.detail); assertEquals(97,r.score); assertFalse(r.conflict)
 }
 @Test fun conflictingTitlesReduceScore() {
  val r=RecognitionEngine.fuse(listOf(Evidence("Interstellar","","media",85),Evidence("Inception","","accessibility",65)))!!
  assertTrue(r.conflict); assertEquals(49,r.score)
 }
 @Test fun distinctEpisodesNeverShareKey() {
  assertNull(RecognitionEngine.key("netflix","The Crown","","episode"))
  assertNotEquals(RecognitionEngine.key("netflix","The Crown","S01 E01","episode"),RecognitionEngine.key("netflix","The Crown","S01 E02","episode"))
 }
 @Test fun movieRankingUsesRuntimeAndYear() {
  val items=listOf(CatalogueItem("movie",1,"Dune",1984,runtimeMinutes=137),CatalogueItem("movie",2,"Dune",2021,runtimeMinutes=155))
  assertEquals(2L,RecognitionEngine.rank("Dune (2021)","",155*60000L,items).first().item.id)
 }
 @Test fun providerKeysScoped() { assertNotEquals(RecognitionEngine.providerKey("netflix","12345"),RecognitionEngine.providerKey("prime","12345")) }
 @Test fun ambiguousEpisodeIsRejected() {
  assertNull(RecognitionEngine.episodeOf("S01 E02 / S01 E03"))
  assertTrue(RecognitionEngine.ambiguousEpisode("S01 E02 / S01 E03"))
  assertNull(RecognitionEngine.key("netflix","Example","S01 E02 / S01 E03","movie"))
 }
 @Test fun exactEpisodeRequiresCorrectParentAndSeason() {
  val number=EpisodeNumber(2,3)
  val right=CatalogueItem("episode",9,"Example",showId=8,season=2,episode=3)
  assertEquals(right,RecognitionEngine.exactEpisode(8,number,listOf(right.copy(showId=7),right.copy(season=1),right)))
  assertNull(RecognitionEngine.exactEpisode(8,number,listOf(right,right.copy(id=10))))
 }
 @Test fun sameSourceConflictingTitlesAreNotTrusted() {
  val result=RecognitionEngine.fuse(listOf(Evidence("Interstellar","","media",85),Evidence("Inception","","media",85)))!!
  assertTrue(result.conflict); assertTrue(result.score<65)
 }
 @Test fun remakeYearsConflict() {
  val result=RecognitionEngine.fuse(listOf(Evidence("Dune (1984)","","media",85),Evidence("Dune (2021)","","notification",70)))!!
  assertTrue(result.conflict); assertTrue(result.score<65)
 }
 @Test fun repeatedSourceCannotIncreaseConfidence() {
  val evidence=Evidence("Interstellar","","media",85)
  assertEquals(85,RecognitionEngine.fuse(List(5) { evidence })!!.score)
 }
}
