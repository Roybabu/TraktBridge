package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class AppearancePolicyTest {
 @Test fun adaptiveColumnsAndFallback() {
  assertEquals("dark",AppearancePolicy.mode("unknown"))
  assertEquals("amoled",AppearancePolicy.mode("amoled"))
  assertEquals(3,AppearancePolicy.columns(1400,1f,false))
  assertEquals(1,AppearancePolicy.columns(1400,1.5f,false))
  assertEquals(2,AppearancePolicy.columns(1400,1f,true))
 }
}
