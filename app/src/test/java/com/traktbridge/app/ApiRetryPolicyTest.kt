package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ApiRetryPolicyTest {
 @Test fun boundedRetry() {
  assertEquals(3000L,ApiRetryPolicy.delay(429,0,3))
  assertNull(ApiRetryPolicy.delay(429,0,60))
  assertNull(ApiRetryPolicy.delay(401,0,null))
  assertNull(ApiRetryPolicy.delay(503,2,null))
 }
}
