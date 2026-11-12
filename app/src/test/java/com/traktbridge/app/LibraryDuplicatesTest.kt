package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class LibraryDuplicatesTest {
 @Test fun conservativeManualCandidates() {
  val a=WatchRecord("a","app","Example","",1,2,1000,600000,0,"reviewed","movie",42)
  val b=a.copy(id="b",traktId=0,matchType="",status="pending")
  assertEquals(listOf(b),LibraryDuplicates.candidates(listOf(a,b,b.copy(id="c",status="synced"),b.copy(id="d",source="other")),a))
  assertTrue(LibraryDuplicates.candidates(listOf(b.copy(subtitle="Episode 12")),a).isEmpty())
 }
}
