package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class TvNavigationTest {
 @Test fun cacheBounds() {
  assertTrue(TvNavigation.validCache(1000,2000))
  assertFalse(TvNavigation.validCache(2000,1000))
  assertFalse(TvNavigation.validCache(1000,8*86400000L))
  assertEquals(2,TvNavigation.next(2,1,3))
 }
}
