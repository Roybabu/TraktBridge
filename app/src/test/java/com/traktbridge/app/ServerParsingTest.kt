package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ServerParsingTest {
 @Test fun plexClientIsolation() {
  val c=ServerConfig("plex","https://example.org","token","client")
  ServerStore.validate(c)
  assertThrows(IllegalArgumentException::class.java) { ServerStore.validate(c.copy(url="http://example.org")) }
 }
}
