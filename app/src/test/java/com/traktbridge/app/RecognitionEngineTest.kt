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
  assertTrue(r.conflict); assertEquals(60,r.score)
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
}
