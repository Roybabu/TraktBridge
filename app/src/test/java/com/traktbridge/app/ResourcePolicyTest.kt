package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ResourcePolicyTest {
 @Test fun cacheBudgetAndPolling() {
  assertEquals(setOf("b"),ResourcePolicy.evictions(listOf("a" to ResourcePolicy.ARTWORK_BYTES,"b" to 1L)))
  assertEquals(5000L,ResourcePolicy.pollDelay(true,true,0,true))
  assertEquals(15000L,ResourcePolicy.pollDelay(true,false,0,true))
  assertEquals(1000,ResourcePolicy.nextPage(1000))
 }
}
