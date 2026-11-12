package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class AnimeEpisodeMappingTest {
 @Test fun distinguishAbsoluteAndSpecial() {
  assertEquals(1097,AnimeEpisodeMapping.absoluteNumber("One Piece Episode 1097"))
  assertNull(AnimeEpisodeMapping.absoluteNumber("Episode 12-13"))
  assertNull(AnimeEpisodeMapping.absoluteNumber("S02E03 Episode 3"))
  assertEquals(EpisodeNumber(0,2),AnimeEpisodeMapping.specialNumber("OVA 2"))
  assertNull(AnimeEpisodeMapping.specialNumber("S01E02 OVA 2"))
  assertNull(RecognitionEngine.key("anime","One Piece","Episode 1097","movie"))
 }
 @Test fun ordinalRequiresCompleteCatalogue() {
  val a=CatalogueItem("episode",1,"Episode 1",showId=42,season=1,episode=1)
  val b=a.copy(id=2,season=2)
  assertEquals(b,AnimeEpisodeMapping.ordinal(42,2,listOf(listOf(a),listOf(b))))
  assertNull(AnimeEpisodeMapping.ordinal(42,2,listOf(listOf(a),listOf(b.copy(season=3)))))
 }
}
