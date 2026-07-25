package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

class YouTubeThumbnailsTest {
    @Test
    fun selectBestThumbnailUrl_picksSmallestVariantAtOrAboveListSize() {
        val low = Image("https://example.com/low.jpg", 120, 90, ResolutionLevel.LOW)
        val medium = Image("https://example.com/medium.jpg", 320, 180, ResolutionLevel.MEDIUM)
        val high = Image("https://example.com/high.jpg", 1280, 720, ResolutionLevel.HIGH)

        assertEquals(
            "https://example.com/medium.jpg",
            selectBestThumbnailUrl(listOf(low, high, medium), "abc123"),
        )
    }

    @Test
    fun selectBestThumbnailUrl_fallsBackToHqDefaultWhenEmpty() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            selectBestThumbnailUrl(emptyList(), "abc123"),
        )
    }
}
