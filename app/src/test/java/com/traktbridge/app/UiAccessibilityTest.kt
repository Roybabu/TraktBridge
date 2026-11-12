package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class UiAccessibilityTest {
    @Test fun scaleAndMotion() {
        assertEquals(1f,UiAccessibility.readingScale(0,false),0.001f)
        assertTrue(UiAccessibility.readingScale(2,true)>UiAccessibility.readingScale(2,false))
        assertFalse(UiAccessibility.animate(true,1f))
        assertFalse(UiAccessibility.animate(false,0f))
        assertTrue(UiAccessibility.animate(false,1f))
    }
}
