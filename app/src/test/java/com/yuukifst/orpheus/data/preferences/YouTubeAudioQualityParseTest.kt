package com.yuukifst.orpheus.data.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeAudioQualityParseTest {

    @Test
    fun blankDefaultsToHigh() {
        assertEquals(YouTubeAudioQuality.HIGH, parseYouTubeAudioQuality(null))
        assertEquals(YouTubeAudioQuality.HIGH, parseYouTubeAudioQuality(""))
    }

    @Test
    fun unknownDefaultsToHigh() {
        assertEquals(YouTubeAudioQuality.HIGH, parseYouTubeAudioQuality("ULTRA"))
    }

    @Test
    fun debounceIsClamped() {
        assertEquals(MIN_YOUTUBE_SEARCH_DEBOUNCE_MS, sanitizeYouTubeSearchDebounceMs(0))
        assertEquals(MAX_YOUTUBE_SEARCH_DEBOUNCE_MS, sanitizeYouTubeSearchDebounceMs(9_000))
        assertEquals(400, sanitizeYouTubeSearchDebounceMs(400))
    }
}
