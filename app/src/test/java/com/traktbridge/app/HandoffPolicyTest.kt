package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class HandoffPolicyTest {
 @Test fun sessionChangesRejected() {
  val id="12345678-1234-1234-1234-123456789abc"
  assertTrue(HandoffPolicy.sameSession(id,id,"Show","Show","E1","E1","pkg","pkg"))
  assertFalse(HandoffPolicy.sameSession(id,id,"Show","Show","E1","E2","pkg","pkg"))
  assertEquals(1000L,HandoffPolicy.estimatedPosition(1000,60000,3,500))
  assertEquals(-1L,HandoffPolicy.estimatedPosition(1000,60000,3,21000))
 }
}
