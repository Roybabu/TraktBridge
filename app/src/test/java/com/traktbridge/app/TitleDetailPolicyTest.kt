package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class TitleDetailPolicyTest {
 @Test fun trustedTrailerLinksOnly() {
  assertEquals("https://www.youtube.com/watch?v=abcdefghijk",TitleDetailPolicy.trailer("https://youtu.be/abcdefghijk"))
  assertNull(TitleDetailPolicy.trailer("https://youtube.com.evil.test/watch?v=abcdefghijk"))
  assertNull(TitleDetailPolicy.trailer("https://www.youtube.com/watch?v=abcdefghijk&v=12345678901"))
  assertNull(TitleDetailPolicy.trailer("http://youtu.be/abcdefghijk"))
 }
}
