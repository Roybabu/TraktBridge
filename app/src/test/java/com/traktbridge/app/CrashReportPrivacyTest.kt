package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class CrashReportPrivacyTest {
    @Test fun stackContainsCauseAndFramesButNotMessages() {
        val inner=IllegalArgumentException("private title and token")
        val outer=IllegalStateException("private account",inner)
        val text=CrashRecovery.formatStack(outer)
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("Caused by: java.lang.IllegalArgumentException"))
        assertTrue(text.contains("CrashReportPrivacyTest"))
        assertFalse(text.contains("private"))
        assertFalse(text.contains("token"))
        assertTrue(text.length<=24000)
    }
    @Test fun rejectsUnsafeClassNames() {
        assertEquals("Unknown",CrashRecovery.safeClass("https://secret"))
    }
}
