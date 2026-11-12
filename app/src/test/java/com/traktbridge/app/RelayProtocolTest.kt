package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class RelayProtocolTest {
 @Test fun routeIsSymmetricAndSecretRequired() {
    val a="a".repeat(64); val b="b".repeat(64)
    assertEquals(RelayProtocol.route(a,b),RelayProtocol.route(b,a))
    assertTrue(runCatching { RelayProtocol.route(a,a) }.isFailure)
    assertFalse(RelaySettings.valid(RelaySettings.Config("relay.example",8765,"short")))
 }
}
