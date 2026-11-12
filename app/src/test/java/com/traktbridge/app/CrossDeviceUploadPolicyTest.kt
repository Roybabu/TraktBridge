package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class CrossDeviceUploadPolicyTest {
 @Test fun nearbyOnlySameExactCatalogueId() {
  assertTrue(CrossDeviceUploadPolicy.nearby(1,1,-120))
  assertTrue(CrossDeviceUploadPolicy.nearby(1,1,120))
  assertFalse(CrossDeviceUploadPolicy.nearby(1,1,121))
  assertFalse(CrossDeviceUploadPolicy.nearby(2,1,0))
 }
}
