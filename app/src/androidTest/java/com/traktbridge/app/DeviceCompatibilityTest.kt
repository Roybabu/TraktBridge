package com.traktbridge.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceCompatibilityTest {
 @Test fun diagnosticsAndProviderProfilesAreAvailable() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val checks=PermissionDiagnostics.read(context)
  assertEquals(3,checks.count { it.required })
  assertEquals("Netflix",DetectionProfiles.forPackage("com.netflix.ninja").label)
  assertEquals("YouTube",DetectionProfiles.forPackage("com.google.android.youtube.tv").label)
  val config=context.resources.configuration
  val tv=config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
  val kind=DeviceDetection.kind(tv,config.smallestScreenWidthDp)
  if(tv) assertEquals(DeviceDetection.Kind.TV,kind)
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { assertFalse(it.isFinishing) }
   scenario.recreate()
   scenario.onActivity { assertFalse(it.isFinishing) }
  }
 }
}
