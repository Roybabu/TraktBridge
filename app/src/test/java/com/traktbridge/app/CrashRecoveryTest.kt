package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class CrashRecoveryTest {
 @Test fun classNamesOnly() {
  assertEquals("java.lang.IllegalStateException",CrashRecovery.safeClass("java.lang.IllegalStateException"))
  assertEquals("Unknown",CrashRecovery.safeClass("https://example.com/token"))
  assertEquals("Unknown",CrashRecovery.safeClass("title with spaces"))
 }
}
