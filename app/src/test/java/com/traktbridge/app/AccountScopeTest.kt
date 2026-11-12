package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class AccountScopeTest {
 @Test fun namespacesAreDistinct() {
  assertNotEquals(AccountScope.cacheKey("a",1),AccountScope.cacheKey("a:b",1))
  assertEquals(AccountScope.cacheKey("a",1),AccountScope.cacheKey("a",1))
  assertEquals(64,AccountScope.key("a").length)
 }
}
