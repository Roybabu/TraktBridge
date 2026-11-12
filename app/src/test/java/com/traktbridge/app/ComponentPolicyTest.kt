package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class ComponentPolicyTest {
 @Test fun strictReviewIds() {
  assertEquals("abc-123",ComponentPolicy.reviewId("traktbridge://review/abc-123"))
  assertNull(ComponentPolicy.reviewId("traktbridge://review/x/y"))
  assertNull(ComponentPolicy.reviewId("traktbridge://review/abc?x=y"))
  assertNull(ComponentPolicy.reviewId("traktbridge://evil/abc"))
 }
 @Test fun largeFontCollapsesCards() {
  assertEquals(1,ComponentPolicy.columns(900,1.5f,false))
  assertEquals(2,ComponentPolicy.columns(900,1.0f,false))
  assertEquals(1,ComponentPolicy.columns(360,1.0f,false))
 }
}
