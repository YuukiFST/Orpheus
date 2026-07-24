package com.yuukifst.orpheus.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubePlayOnceIsolationTest {

    @Test
    fun searchTapAlwaysReplacesNotAppends() {
        assertTrue(
            shouldReplaceQueueForSearchPlay(
                currentIsYouTubeSession = true,
                explicitAddToQueue = false,
            ),
        )
        assertTrue(
            shouldReplaceQueueForSearchPlay(
                currentIsYouTubeSession = false,
                explicitAddToQueue = false,
            ),
        )
    }

    @Test
    fun explicitAddToQueueDoesNotReplace() {
        assertFalse(
            shouldReplaceQueueForSearchPlay(
                currentIsYouTubeSession = true,
                explicitAddToQueue = true,
            ),
        )
    }

    @Test
    fun isSearchQueueNameRecognizesSearchSessions() {
        assertTrue(isSearchQueueName("Search Results"))
        assertTrue(isSearchQueueName("Search: beatles"))
        assertTrue(isSearchQueueName("Search"))
        assertFalse(isSearchQueueName("Liked Songs"))
        assertFalse(isSearchQueueName("Library"))
    }
}
