package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SmartWatchlistsTest {
 @Test fun rulesFilterAndUpdate() {
  val row=WatchRecord("a","app","Example","",1,2,1000,600000,100000,"reviewed","movie",42)
  val groups=ViewingGroups.build(listOf(row))
  assertEquals(1,SmartWatchlists.select(groups,SmartWatchlist("x","unfinished","movie","app"),2).size)
  assertTrue(SmartWatchlists.select(groups,SmartWatchlist("x","unfinished","episode"),2).isEmpty())
  assertTrue(SmartWatchlists.select(ViewingGroups.build(listOf(row.copy(status="synced"))),SmartWatchlist("x","unfinished"),2).isEmpty())
 }
}
