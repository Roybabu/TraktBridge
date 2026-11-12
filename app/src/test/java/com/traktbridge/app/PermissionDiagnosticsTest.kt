package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class PermissionDiagnosticsTest {
 @Test fun optionalPermissionsNeverBecomeRequired() {
  val checks=PermissionDiagnostics.checks(true,1,true,false,true,false)
  assertTrue(checks.filter { it.required }.all { it.ready })
  assertTrue(checks.any { !it.required && !it.ready })
  assertFalse(PermissionDiagnostics.checks(false,0,false,false,false,false).filter { it.required }.any { it.ready })
 }
}
