package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class RemoteRulesTest {
 @Test fun accountScopedHistory() { assertNotEquals(RemoteRules.historyKey("a",123),RemoteRules.historyKey("b",123)) }
 @Test fun typeScopedIdentity() { assertNotEquals(RemoteRules.identity("movie",1),RemoteRules.identity("episode",1)) }
 @Test fun ratingsBounded() { assertEquals(10,RemoteRules.rating(10)); assertThrows(IllegalArgumentException::class.java) { RemoteRules.rating(11) } }
}
