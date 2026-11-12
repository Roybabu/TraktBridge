package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DatabaseProtectionTest {
 @Test fun migrationGate() {
  assertTrue(DatabaseProtection.canPrepare(0,0,0,0,true))
  assertFalse(DatabaseProtection.canPrepare(0,0,1,0,true))
  assertFalse(DatabaseProtection.canPrepare(0,0,0,0,false))
  assertFalse(DatabaseProtection.LIVE_ENCRYPTED)
 }
}
