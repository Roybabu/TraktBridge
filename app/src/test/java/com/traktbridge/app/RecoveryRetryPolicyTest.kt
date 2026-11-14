package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class RecoveryRetryPolicyTest {
    @Test fun retriesImmediatelyThenBacksOff() {
        assertEquals(0L,RecoveryRetryPolicy.delay(0))
        assertEquals(15000L,RecoveryRetryPolicy.delay(1))
        assertEquals(30000L,RecoveryRetryPolicy.delay(2))
        assertEquals(60000L,RecoveryRetryPolicy.delay(3))
        assertEquals(120000L,RecoveryRetryPolicy.delay(20))
    }
}
