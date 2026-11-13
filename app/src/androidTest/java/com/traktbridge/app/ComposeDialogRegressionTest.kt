package com.traktbridge.app

import android.app.AlertDialog
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeDialogRegressionTest {
    @Test fun directAndNestedComposeViewsHaveWindowOwners() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=instrumentation.targetContext.getSharedPreferences("tracker",0)
        val previous=prefs.getBoolean("setup_seen",false)
        prefs.edit().putBoolean("setup_seen",true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                for(nested in listOf(false,true)) {
                    lateinit var dialog:AlertDialog
                    lateinit var compose:ComposeView
                    scenario.onActivity { activity ->
                        compose=ComposeView(activity).apply {
                            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                            setContent { Text("Compose dialog regression") }
                        }
                        val body=if(nested) ScrollView(activity).apply { addView(LinearLayout(activity).apply { addView(compose) }) } else compose
                        val builder=BridgeDialogBuilder(activity).setTitle("Dialog owners test").setView(body).setPositiveButton("Close",null)
                        dialog=if(nested) builder.show() else builder.create().also { it.show() }
                        val decor=requireNotNull(dialog.window).decorView
                        assertSame(activity,decor.findViewTreeLifecycleOwner())
                        assertSame(activity,decor.findViewTreeViewModelStoreOwner())
                        assertSame(activity,decor.findViewTreeSavedStateRegistryOwner())
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        assertTrue(compose.isAttachedToWindow)
                        assertTrue(compose.hasComposition)
                        assertSame(activity,compose.findViewTreeLifecycleOwner())
                        dialog.dismiss()
                    }
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { assertFalse(compose.hasComposition) }
                }
            }
        } finally { prefs.edit().putBoolean("setup_seen",previous).commit() }
    }

    @Test fun realPlaybackAndHistoryDialogsOpenAndClose() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val prefs=instrumentation.targetContext.getSharedPreferences("tracker",0)
        val previous=prefs.getBoolean("setup_seen",false)
        prefs.edit().putBoolean("setup_seen",true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("nowPlaying").apply { isAccessible=true }.invoke(activity)
                }
                instrumentation.waitForIdleSync()
                onView(isAssignableFrom(ComposeView::class.java)).inRoot(isDialog()).check(matches(isDisplayed()))
                pressBack()
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertFalse(it.isFinishing) }
                for((method,title) in listOf("showDeviceHistory" to "Cross-device history")) {
                    scenario.onActivity { activity ->
                        MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible=true }.invoke(activity)
                    }
                    instrumentation.waitForIdleSync()
                    onView(withText(title)).inRoot(isDialog()).check(matches(isDisplayed()))
                    onView(withText("Close")).inRoot(isDialog()).perform(click())
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { assertFalse(it.isFinishing) }
                }
            }
        } finally { prefs.edit().putBoolean("setup_seen",previous).commit() }
    }
}
