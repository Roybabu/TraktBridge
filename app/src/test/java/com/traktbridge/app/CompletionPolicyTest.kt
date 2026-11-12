package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class CompletionPolicyTest {
 private val row=WatchRecord("a","app","Movie","",1,2,480000,600000,550000,"reviewed","movie",42,positionSource="media")
 @Test fun thresholdIsConfigurableAndRequiresEvidence() {
  assertTrue(CompletionPolicy.assess(row,90).suggested)
  assertFalse(CompletionPolicy.assess(row,95).suggested)
  assertFalse(CompletionPolicy.assess(row.copy(watched=10000),90).suggested)
  assertFalse(CompletionPolicy.assess(row.copy(positionSource="unknown"),90).suggested)
  assertFalse(CompletionPolicy.assess(row.copy(traktId=0),90).suggested)
  assertFalse(CompletionPolicy.assess(row.copy(status="active"),90).suggested)
  assertFalse(CompletionPolicy.assess(row.copy(catalogueRuntime=3600000),90).suggested)
  assertEquals(75,CompletionPolicy.threshold(0))
 }
}
