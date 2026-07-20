package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class YouTubeVideoIdExtractionTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun initNewPipe() {
            YouTubeInitializer.ensureInitialized()
        }
    }

    @Test
    fun extractYouTubeVideoId_watchUrl() {
        assertEquals(
            "dQw4w9WgXcQ",
            extractYouTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun extractYouTubeVideoId_shortUrl() {
        assertEquals(
            "dQw4w9WgXcQ",
            extractYouTubeVideoId("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun extractYouTubeVideoId_blankUrl() {
        assertNull(extractYouTubeVideoId(""))
        assertNull(extractYouTubeVideoId(null))
    }
}
