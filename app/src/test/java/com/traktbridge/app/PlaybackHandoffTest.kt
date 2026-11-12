package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PlaybackHandoffTest {
 @Test fun publicLinksOnly() {
    assertEquals("12345678901",PlaybackHandoff.extract("com.google.android.youtube.tv","https://youtu.be/12345678901?secret=private"))
    assertEquals("https://www.youtube.com/watch?v=12345678901&t=45s",PlaybackHandoff.link("com.google.android.youtube.tv","12345678901",45000))
    assertEquals("",PlaybackHandoff.extract("com.netflix.ninja","https://evil.example/watch/123456"))
    assertEquals("",PlaybackHandoff.extract("com.netflix.ninja","https://user@www.netflix.com/watch/123456"))
    assertNull(PlaybackHandoff.link("com.example","abc",0))
 }
}
