package com.traktbridge.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhoneUiRegressionTest {
 @Test fun compactNavigationAndRecreation() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val config=context.resources.configuration
  val tv=config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
  if(tv || config.smallestScreenWidthDp>=600) return
  val prefs=context.getSharedPreferences("tracker",0)
  val prior=prefs.getBoolean("setup_seen",false)
  prefs.edit().putBoolean("setup_seen",true).commit()
  try {
   ActivityScenario.launch(MainActivity::class.java).use { scenario ->
    scenario.onActivity { activity -> assertNotNull(activity.findViewById<android.view.View>(android.R.id.content)); assertFalse(activity.isFinishing) }
    scenario.recreate()
    onView(withId(android.R.id.content)).check(matches(isDisplayed()))
   }
  } finally { prefs.edit().putBoolean("setup_seen",prior).commit() }
 }
}
