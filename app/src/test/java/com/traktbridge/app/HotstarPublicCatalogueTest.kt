package com.traktbridge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class HotstarPublicCatalogueTest {
    @Test fun extractsOnlyDelimitedNumericIds() {
        assertEquals(setOf("1971036132","1260023113"),HotstarPublicCatalogue.candidateIds(listOf("1971036132","asset-1260023113.mpd","abc1234567890","123","123456789012345")))
    }
    @Test fun doesNotGuessOpaqueIds() {
        assertEquals(emptySet<String>(),HotstarPublicCatalogue.candidateIds(listOf("abcdef1234567890abcdef","index.mpd")))
    }
}
