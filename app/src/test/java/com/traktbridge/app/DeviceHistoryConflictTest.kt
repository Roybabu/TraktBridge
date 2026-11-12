package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class DeviceHistoryConflictTest {
 @Test fun immutableOriginRecord() {
  val e=DeviceHistory.Event("id","source","title","episode",10,20,10,50,20)
  assertEquals(DeviceHistoryConflict.Decision.INSERT,DeviceHistoryConflict.decide(null,e))
  assertEquals(DeviceHistoryConflict.Decision.UNCHANGED,DeviceHistoryConflict.decide(e,e))
  assertEquals(DeviceHistoryConflict.Decision.CONFLICT,DeviceHistoryConflict.decide(e,e.copy(title="other")))
 }
}
