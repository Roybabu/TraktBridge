package com.traktbridge.app

import android.content.res.Configuration
import android.view.KeyEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvUiRegressionTest {
 @Test fun televisionLauncherAndDpad() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  val context=instrumentation.targetContext
  val tv=context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
  val requested=InstrumentationRegistry.getArguments().getString("deviceProfile")
  if(requested=="tv") assertTrue("TV emulator must be in television UI mode",tv)
  if(!tv) return
  assertNotNull(context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName))
  val prefs=context.getSharedPreferences("tracker",0)
  val previous=prefs.getBoolean("setup_seen",false)
  prefs.edit().putBoolean("setup_seen",true).commit()
  try {
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    scenario.onActivity { activity ->
     val c=activity.resources.configuration
     assertEquals(AdaptiveUi.Mode.TV,AdaptiveUi.layout(c.screenWidthDp,c.screenHeightDp,c.fontScale,true).mode)
     assertFalse(activity.isFinishing)
    }
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
    instrumentation.waitForIdleSync()
    scenario.onActivity { activity -> assertFalse(activity.isFinishing); assertNotNull(activity.currentFocus) }
    scenario.recreate()
    scenario.onActivity { assertFalse(it.isFinishing) }
   }
  } finally { prefs.edit().putBoolean("setup_seen",previous).commit() }
 }
}
