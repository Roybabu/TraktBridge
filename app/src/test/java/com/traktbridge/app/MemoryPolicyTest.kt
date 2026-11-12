package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class MemoryPolicyTest {
 @Test fun boundedBudgets() {
  assertEquals(2048,MemoryPolicy.artworkKiB(128,true))
  assertEquals(16384,MemoryPolicy.artworkKiB(512,false))
  assertEquals(40,MemoryPolicy.pageSize(true))
  assertEquals(1000,MemoryPolicy.nextPage(980,100))
 }
}
