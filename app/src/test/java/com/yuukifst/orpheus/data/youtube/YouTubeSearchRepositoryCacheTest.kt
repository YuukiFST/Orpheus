package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class YouTubeSearchRepositoryCacheTest {
    @Test
    fun searchCache_returnsSameListInstanceForSameQuery() {
        val repo = YouTubeSearchRepository()
        repo.clearSearchCacheForTests()
        val cached = listOf(
            YouTubeTrack(
                videoId = "x",
                title = "t",
                channelName = "c",
                thumbnailUrl = "u",
                durationMs = 1L,
            ),
        )
        repo.seedSearchCacheForTests("query", cached)

        val first = repo.searchCachedOnly("query")
        val second = repo.searchCachedOnly("query")

        assertSame(first, second)
    }
}
