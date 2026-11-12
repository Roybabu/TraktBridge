package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class LibraryOrganizationTest {
 @Test fun tagsAndFolderSearch() {
  val a=WatchRecord("a","app","Example Movie","",1,2,1000,600000,100000,"reviewed","movie",42)
  val groups=ViewingGroups.build(listOf(a))
  assertEquals(listOf("drama","classic"),LibraryOrganization.tags(" Drama,classic,DRAMA "))
  assertEquals(1,LibraryOrganization.search(groups,"movie drama","drama",setOf("movie:42"),mapOf("movie:42" to listOf("drama")),"recent").size)
  assertTrue(LibraryOrganization.search(groups,"movie","",emptySet(),emptyMap(),"recent").isEmpty())
 }
}
