package com.traktbridge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {
    @Test fun movieFilename() { assertEquals("Interstellar 2014", TitleNormalizer.suggestedQuery("Interstellar.2014.1080p.BluRay.mkv")) }
    @Test fun episodeFilename() { assertEquals("Breaking Bad", TitleNormalizer.suggestedQuery("Breaking.Bad.S02E05.1080p.mkv")) }
    @Test fun seasonText() { assertEquals("Solo Leveling", TitleNormalizer.suggestedQuery("Solo Leveling Season 2 Episode 3")) }
    @Test fun plainTitle() { assertEquals("The Matrix", TitleNormalizer.suggestedQuery("The Matrix")) }
    @Test fun blankTitle() { assertEquals("", TitleNormalizer.suggestedQuery("  ")) }
    @Test fun episodeOnlyDoesNotDisappear() { assertEquals("Episode 12", TitleNormalizer.suggestedQuery("Episode 12")) }
    @Test fun movieLabel() { assertEquals("Interstellar (2014) • movie",CatalogueItem("movie",1,"Interstellar",2014).label()) }
    @Test fun episodeLabel() { assertEquals("One Piece — Episode 3 • S1 E3",CatalogueItem("episode",2,"One Piece — Episode 3",showId=1,season=1,episode=3).label()) }
}
