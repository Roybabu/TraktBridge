package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlaybackShortcutsTest {
 @Test fun strictLinks() {
  assertEquals("library",PlaybackShortcuts.shortcut("traktbridge://shortcut/library"))
  assertNull(PlaybackShortcuts.shortcut("traktbridge://shortcut/foo/library"))
  assertNull(PlaybackShortcuts.shortcut("traktbridge://shortcut/library?x=1"))
  assertNull(PlaybackShortcuts.providerLink("com.netflix.mediaclient","12345/evil"))
  assertEquals(30000L,PlaybackShortcuts.pollDelay(false,false))
 }
}
