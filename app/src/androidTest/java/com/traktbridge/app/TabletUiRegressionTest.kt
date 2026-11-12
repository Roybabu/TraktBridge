package com.traktbridge.app

import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabletUiRegressionTest {
 @Test fun wideWindowAndOrientationRecreation() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val c=context.resources.configuration
  val tv=c.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
  if(tv || c.smallestScreenWidthDp<600) return
  val prefs=context.getSharedPreferences("tracker",0)
  val previous=prefs.getBoolean("setup_seen",false)
  prefs.edit().putBoolean("setup_seen",true).commit()
  try {
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    scenario.onActivity { activity ->
     val config=activity.resources.configuration
     val layout=AdaptiveUi.layout(config.screenWidthDp,config.screenHeightDp,config.fontScale,false)
     assertTrue(layout.sidebar || config.screenHeightDp<400 || config.screenWidthDp<600)
     activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    scenario.recreate()
    scenario.onActivity { assertFalse(it.isFinishing) }
   }
  } finally { prefs.edit().putBoolean("setup_seen",previous).commit() }
 }
}
