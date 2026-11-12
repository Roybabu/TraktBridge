package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DashboardPreferencesTest {
 @Test fun invalidAndDuplicateSectionsAreHandled() {
  assertEquals("recent",DashboardPreferences.order("recent,recent,bad").first())
  assertEquals(7,DashboardPreferences.order("recent,recent,bad").size)
  assertEquals(setOf("now"),DashboardPreferences.visible("now,bad"))
  assertEquals("private",DashboardPreferences.widgetMode("invalid"))
 }
}
