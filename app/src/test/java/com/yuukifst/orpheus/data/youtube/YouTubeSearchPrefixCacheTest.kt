package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the key normalization step 5's synchronous cache fast path depends on.
 * (`android.util.LruCache` is inert under unit-test default stubs.)
 */
class YouTubeSearchPrefixCacheTest {

    @Test
    fun searchCachedOnlyKeyIsCaseAndWhitespaceInsensitive() {
        assertEquals(
            youtubeQueryCacheKey("Beatles"),
            youtubeQueryCacheKey("  beatles "),
        )
        assertEquals("beatles", youtubeQueryCacheKey("  Beatles "))
    }
}
