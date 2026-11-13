package com.traktbridge.app
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @Test fun categoriesAndBackNavigation() {
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("tracker",0)
        val before=prefs.getBoolean("setup_seen",false)
        prefs.edit().putBoolean("setup_seen",true).commit()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("showTab",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(activity,4)
                    val overview=MainActivity::class.java.getDeclaredField("settingsOverview").apply { isAccessible=true }.get(activity) as LinearLayout
                    @Suppress("UNCHECKED_CAST")
                    val pages=MainActivity::class.java.getDeclaredField("settingsGroups").apply { isAccessible=true }.get(activity) as Map<String,LinearLayout>
                    assertEquals(8,pages.size)
                    assertEquals(View.VISIBLE,overview.visibility)
                    val select=MainActivity::class.java.getDeclaredMethod("showSettingsCategory",String::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
                    for(id in pages.keys) {
                        select.invoke(activity,id,true)
                        assertEquals(View.GONE,overview.visibility)
                        assertEquals(1,pages.values.count { it.visibility==View.VISIBLE })
                        assertEquals(View.VISIBLE,pages.getValue(id).visibility)
                        assertTrue(pages.getValue(id).childCount>3)
                    }
                    fun labels(view:View):List<String> = if(view is android.view.ViewGroup) (0 until view.childCount).flatMap { labels(view.getChildAt(it)) } else if(view is TextView) listOf(view.text.toString()) else emptyList()
                    val overviewLabels=labels(overview)
                    for(category in SettingsCatalog.categories) assertEquals(1,overviewLabels.count { it==category.title })
                    for(group in listOf("Account & playback","Personalization","Privacy & data","Help & support")) assertTrue(overviewLabels.contains(group))
                    assertFalse(overviewLabels.contains("Quick actions"))
                    assertFalse(overviewLabels.contains("Guided tracking setup"))
                    assertFalse(overviewLabels.contains("Now Playing • All devices"))
                    assertEquals(1,labels(pages.getValue("tracking")).count { it=="Guided tracking setup" })
                    val deviceLabels=labels(pages.getValue("devices"))
                    assertEquals(1,deviceLabels.count { it=="Pair a device" })
                    assertEquals(1,deviceLabels.count { it=="Live playback sharing" })
                    assertEquals(1,deviceLabels.count { it=="Viewing history" })
                    assertEquals(1,deviceLabels.count { it=="Remote playback controls" })
                    assertTrue("Maintenance should be nested",deviceLabels.none { it=="Show device pairing QR" || it=="Configure optional cloud relay" || it=="Clear received device history" })
                    val trackingLabels=labels(pages.getValue("tracking"))
                    assertEquals(1,trackingLabels.count { it=="Playback tracking" })
                    assertFalse(trackingLabels.contains("Start persistent tracking"))
                    assertFalse(trackingLabels.contains("Stop persistent tracking"))
                    activity.onBackPressedDispatcher.onBackPressed()
                    assertEquals(View.VISIBLE,overview.visibility)
                    assertTrue(pages.values.all { it.visibility==View.GONE })
                    select.invoke(activity,"appearance",true)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val selected=MainActivity::class.java.getDeclaredField("settingsCategory").apply { isAccessible=true }.get(activity)
                    assertEquals("appearance",selected)
                }
            }
        } finally { prefs.edit().putBoolean("setup_seen",before).commit() }
    }
}
