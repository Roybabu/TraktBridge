package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class MigrationReadinessTest {
 @Test fun migrationMustNotLoseActiveOrApprovedRecords() {
  assertTrue(MigrationReadiness.eligible(0,0,0,0,true))
  assertFalse(MigrationReadiness.eligible(1,0,0,0,true))
  assertFalse(MigrationReadiness.eligible(0,0,1,0,true))
  assertFalse(MigrationReadiness.eligible(0,0,0,0,false))
 }
}
