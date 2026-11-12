package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
class ExternalHistoryImportTest {
 @Test fun interchangePreservesEpisodeAndDedupIdentity() {
  val csv="Title,Watched At,Season,Episode\nExample,2025-01-01,2,3"
  val a=ExternalHistoryImport.parseCsv(csv,"simkl",ZoneId.of("UTC"))
  assertEquals("Example S2E3",a.rows.single().title)
  assertEquals(a.rows.single().id,ExternalHistoryImport.parseCsv(csv,"simkl",ZoneId.of("UTC")).rows.single().id)
 }
 @Test fun ratingsAreNotViewingHistory() {
  assertTrue(runCatching { ExternalHistoryImport.parseCsv("Title,Rating\nExample,8","imdb") }.isFailure)
 }
}
