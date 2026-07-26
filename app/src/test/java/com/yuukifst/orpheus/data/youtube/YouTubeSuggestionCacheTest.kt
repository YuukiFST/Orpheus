package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Cache seed/get cannot be asserted under `unitTests.isReturnDefaultValues = true`
 * (`android.util.LruCache` stubs return null). These cases pin the key contract
 * the ViewModel fast path depends on, plus miss/clear API smoke.
 */
class YouTubeSuggestionCacheTest {

    @Test
    fun suggestionCacheKeyIsCaseAndWhitespaceInsensitive() {
        assertEquals("bea", youtubeQueryCacheKey("BEA "))
        assertEquals(youtubeQueryCacheKey("bea"), youtubeQueryCacheKey("BEA "))
    }

    @Test
    fun suggestionsCachedOnlyMissReturnsNull() {
        val repo = YouTubeSuggestionRepository.createForTests()
        assertNull(repo.suggestionsCachedOnly("unseen"))
    }

    @Test
    fun clearSuggestionCacheEvictsSeededKeys() {
        val repo = YouTubeSuggestionRepository.createForTests()
        repo.seedSuggestionCacheForTests("bea", listOf("beatles"))
        repo.clearSuggestionCacheForTests()
        assertNull(repo.suggestionsCachedOnly("bea"))
    }
}
