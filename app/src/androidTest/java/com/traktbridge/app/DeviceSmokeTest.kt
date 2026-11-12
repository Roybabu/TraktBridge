package com.traktbridge.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @Test fun launchAndRecreate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(it.isFinishing) }
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
    @Test fun databaseIntegrityAndRecoveryRoundTrip() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=WatchHistory(context)
        try {
            assertTrue(HistoryBackup.integrity(db))
            val id=db.addManualLog("instrumentation","Device smoke test",System.currentTimeMillis()-1000)
            try {
                assertNotNull(db.record(id))
                val snapshot=HistoryBackup.snapshot(db).toString()
                assertTrue(HistoryBackup.parse(snapshot).any { it.getAsString("id")==id })
                assertTrue(HistoryBackup.integrity(db))
            } finally {
                db.writableDatabase.delete("viewing","id=?",arrayOf(id))
            }
        } finally { db.close() }
    }
    @Test fun deviceIdentityPersists() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val first=DeviceIdentity.register(context)
        val second=DeviceIdentity.register(context)
        assertEquals(first.id,second.id)
        assertArrayEquals(first.publicKey,second.publicKey)
        assertEquals(64,first.id.length)
    }
}
