package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.LocalDate
class HistoryImportTest {
 @Test fun netflixCsv() {
  val p=HistoryImport.parse("\uFEFFTitle,Date\r\n\"The Crown: Season 1: Wolferton Splash\",11/12/26\r\n\"A, B\",11/11/2026","netflix",ZoneId.of("UTC"))
  assertEquals(2,p.rows.size); assertEquals(LocalDate.of(2026,11,12),p.rows[0].day); assertEquals("A, B",p.rows[1].title)
 }
 @Test fun stableIdsPreserveRepeatedRows() {
  val csv="Title,Date\nMovie,11/12/26\nMovie,11/12/26"
  val a=HistoryImport.parse(csv,"netflix"); val b=HistoryImport.parse(csv,"netflix")
  assertEquals(a.rows.map { it.id },b.rows.map { it.id }); assertNotEquals(a.rows[0].id,a.rows[1].id)
 }
 @Test fun invalidRowsSkipped() { assertEquals(1,HistoryImport.parse("Title,Date\nMovie,bad","netflix").skipped) }
 @Test fun timestampExport() { assertNotNull(HistoryImport.parse("Title,Start Time\nMovie,2026-11-12T10:00:00Z","netflix").rows.single().timestamp) }
}
