package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ExtendedReadinessTest {
 @Test fun letterboxdDiaryUsesWatchDate() {
    val csv="Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n2026-11-12,Interstellar,2014,https://letterboxd.com/film/interstellar/,5,Yes,,2026-11-10"
    val result=HistoryImport.parse(csv,"letterboxd",ZoneId.of("UTC"))
    assertEquals(1,result.rows.size)
    assertEquals("2026-11-10",result.rows.single().day.toString())
    assertEquals("Interstellar",result.rows.single().title)
    assertNull(result.rows.single().timestamp)
    assertEquals(result.rows.single().id,HistoryImport.parse(csv,"letterboxd",ZoneId.of("UTC")).rows.single().id)
 }
 @Test fun externalEpisodeEvidenceCannotBeMovieKey() {
    assertNull(RecognitionEngine.key("player","Show","S02 E03","movie"))
    assertNotEquals(RecognitionEngine.key("player","Show","S02 E03","episode"),RecognitionEngine.key("player","Show","S02 E04","episode"))
 }
 @Test fun versionOrderIsNumeric() {
    assertTrue(UpdateCheck.compare("0.57.0","0.9.9")>0)
    assertEquals(0,UpdateCheck.compare("0.57.0","0.57.0"))
 }
}
