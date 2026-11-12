package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SharedContentTest {
 @Test fun netflixLinkWithTitle() {
  val c=SharedContent.fromText("Watch The Crown on Netflix\nhttps://www.netflix.com/title/80025678")
  assertEquals("The Crown",c?.title); assertEquals("80025678",c?.providerId)
 }
 @Test fun opaqueLinkNeverBecomesTitle() {
  val c=SharedContent.fromText("https://www.netflix.com/watch/80025678?token=secret")
  assertEquals("",c?.title); assertEquals("80025678",c?.providerId)
 }
 @Test fun fileNameAndEpisode() {
  val c=SharedContent.fromFile("The.Last.of.Us.S02E03.1080p.WEB-DL.mkv","",3300000)
  assertEquals("The Last of Us",c?.title); assertEquals("S02 E03",c?.detail); assertEquals(3300000L,c?.runtime)
 }
 @Test fun lookalikeDomainNotProvider() { assertNull(SharedContent.fromText("https://netflix.com.example.org/title/80025678")) }
}
