package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * `android.util.LruCache` is inert under `unitTests.isReturnDefaultValues = true`,
 * so seed/hit assertions cannot run without a real network call. Cases 1–2 only
 * (plan 005 STOP guidance).
 */
class YouTubeStreamPrefetchTest {

    @Test
    fun isCachedUnknownIsFalse() {
        val extractor = YouTubeStreamExtractor.createForTests()
        assertFalse(extractor.isCached("unknown"))
    }

    @Test
    fun prefetchBlankVideoIdReturnsFalse() = runBlocking {
        val extractor = YouTubeStreamExtractor.createForTests()
        assertFalse(extractor.prefetchBestAudio(""))
        assertFalse(extractor.prefetchBestAudio("   "))
    }
}
