package com.yuukifst.orpheus.data.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackClearGenerationTest {

    @Test
    fun bumpInvalidatesPriorDismissClearToken() {
        val tokenAtDismiss = PlaybackClearGeneration.current()
        assertTrue(PlaybackClearGeneration.matches(tokenAtDismiss))

        val afterNewPlay = PlaybackClearGeneration.bump()
        assertEquals(tokenAtDismiss + 1, afterNewPlay)
        assertFalse(PlaybackClearGeneration.matches(tokenAtDismiss))
        assertTrue(PlaybackClearGeneration.matches(afterNewPlay))
    }
}
