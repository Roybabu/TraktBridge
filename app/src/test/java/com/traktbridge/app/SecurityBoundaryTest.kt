package com.traktbridge.app
import org.junit.Assert.*
import org.junit.Test
class SecurityBoundaryTest {
 @Test fun rejectsUntrustedNavigation() {
  for(link in listOf("traktbridge://shortcut@evil/library","traktbridge://shortcut:80/library","traktbridge://shortcut/library#x","traktbridge://shortcut/%6cibrary")) assertNull(PlaybackShortcuts.shortcut(link))
  for(link in listOf("traktbridge://review/../secret","traktbridge://review/id?x=1","https://review/id")) assertNull(ComponentPolicy.reviewId(link))
 }
 @Test fun namespacesAndProviderIdsStaySeparate() {
  assertNotEquals(AccountScope.cacheKey("account-a",42),AccountScope.cacheKey("account-b",42))
  assertNull(PlaybackShortcuts.providerLink("com.google.android.youtube","../../bad"))
 }
}
