package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class AdaptiveUiTest {
    @Test fun windowAndInputModes() {
        assertEquals(AdaptiveUi.Mode.COMPACT,AdaptiveUi.layout(390,800,1f,false).mode)
        assertEquals(AdaptiveUi.Mode.COMPACT,AdaptiveUi.layout(900,350,1f,false).mode)
        assertEquals(AdaptiveUi.Mode.MEDIUM,AdaptiveUi.layout(700,900,1f,false).mode)
        assertEquals(3,AdaptiveUi.layout(1280,800,1f,false).columns)
        assertEquals(1,AdaptiveUi.layout(1280,800,1.6f,false).columns)
        assertEquals(AdaptiveUi.Mode.TV,AdaptiveUi.layout(960,540,1f,true).mode)
    }
}
