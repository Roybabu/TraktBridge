package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class FranchiseTimelineTest {
 @Test fun exactYearAndIdentity() {
  val a=CatalogueItem("movie",1,"The Matrix",1999)
  assertEquals(a,FranchiseTimeline.exact(FranchiseEntry("The Matrix",1999),listOf(a,a.copy(id=2,year=2021))))
  assertNull(FranchiseTimeline.exact(FranchiseEntry("The Matrix",1999),listOf(a,a.copy(id=2))))
 }
 @Test fun missingUsesExactEpisodeId() {
  val a=CatalogueItem("episode",1,"A",showId=42,season=1,episode=1)
  val b=a.copy(id=2,episode=2)
  val r=WatchRecord("r","app","A","",1,2,1000,600000,0,"reviewed","episode",1,showId=42,season=1,episode=1)
  assertEquals(listOf(b),FranchiseTimeline.missing(42,1,listOf(a,b),listOf(r)))
 }
}
