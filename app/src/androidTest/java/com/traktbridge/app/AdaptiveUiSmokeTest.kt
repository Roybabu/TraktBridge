package com.traktbridge.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveUiSmokeTest {
    @Test fun shellAndConfigurationRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root=activity.findViewById<ViewGroup>(android.R.id.content)
                assertNotNull(root)
                assertTrue(root.childCount>0)
                val config=activity.resources.configuration
                val tv=config.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
                val layout=AdaptiveUi.layout(config.screenWidthDp,config.screenHeightDp,config.fontScale,tv)
                assertTrue(layout.touch>=48)
                assertTrue(layout.columns>=1)
                if(tv) assertEquals(AdaptiveUi.Mode.TV,layout.mode)
            }
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
    @Test fun tvFocusDrawableHasVisibleStates() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val drawable=TvFocus.background(context,0xff302a48.toInt(),0xffb6a0ff.toInt())
        drawable.state=intArrayOf(android.R.attr.state_focused)
        val focused=drawable.current
        drawable.state=intArrayOf()
        assertNotSame(focused,drawable.current)
    }
}
