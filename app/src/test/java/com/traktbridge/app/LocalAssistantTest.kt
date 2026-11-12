package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class LocalAssistantTest {
 @Test fun timeBudget() { assertEquals(90,LocalAssistant.parse("Find something under 90 minutes").minutes); assertEquals(120,LocalAssistant.parse("I have 2 hours").minutes) }
 @Test fun localCommands() { assertEquals("subscriptions",LocalAssistant.parse("Which subscription is least used?").action); assertEquals("unfinished",LocalAssistant.parse("Show unfinished movies").action); assertEquals(30,LocalAssistant.parse("What I watched last month").days) }
 @Test fun unsupportedFallsBackToSearch() { assertEquals("search",LocalAssistant.parse("Interstellar").action) }
}
