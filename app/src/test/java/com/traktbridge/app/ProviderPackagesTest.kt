package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ProviderPackagesTest {
    @Test fun aliasesUseSameProfileWithoutGrantingConsent() {
        assertEquals("com.netflix.mediaclient",ProviderPackages.canonical("com.netflix.ninja"))
        assertEquals(DetectionProfiles.forPackage("org.jellyfin.mobile"),DetectionProfiles.forPackage("org.jellyfin.androidtv"))
        assertTrue("com.google.android.youtube.tv" in PlayerRules.apps.values)
        assertEquals(listOf("com.netflix.ninja","com.netflix.mediaclient"),PlaybackHandoff.packages("com.netflix.ninja"))
        assertTrue(PlaybackHandoff.packages("unknown.app").isEmpty())
    }
}
