package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SyncRecoveryPolicyTest {
 @Test fun uncertainNeverBecomesQueued() {
  assertEquals("uncertain",SyncRecoveryPolicy.afterLookup("uncertain",false,true))
  assertEquals("synced",SyncRecoveryPolicy.afterLookup("uncertain",true,true))
  assertEquals("queued",SyncRecoveryPolicy.afterLookup("queued",false,false))
 }
}
