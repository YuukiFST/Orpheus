package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.Image.ResolutionLevel

class YouTubeThumbnailsTest {
    @Test
    fun selectBestThumbnailUrl_picksHighestResolution() {
        val low = Image("https://example.com/low.jpg", 120, 90, ResolutionLevel.LOW)
        val high = Image("https://example.com/high.jpg", 480, 360, ResolutionLevel.MEDIUM)
        val best = Image("https://example.com/best.jpg", 1280, 720, ResolutionLevel.HIGH)

        assertEquals(
            "https://example.com/best.jpg",
            selectBestThumbnailUrl(listOf(low, best, high), "abc123"),
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
