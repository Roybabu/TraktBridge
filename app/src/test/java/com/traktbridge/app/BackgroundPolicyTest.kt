package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class BackgroundPolicyTest {
 @Test fun backoffIsBounded() {
  assertEquals(5000L,BackgroundPolicy.delay(true,true,0))
  assertEquals(10000L,BackgroundPolicy.delay(true,true,1))
  assertEquals(40000L,BackgroundPolicy.delay(true,true,100))
  assertEquals(30000L,BackgroundPolicy.delay(false,true,100))
 }
}
