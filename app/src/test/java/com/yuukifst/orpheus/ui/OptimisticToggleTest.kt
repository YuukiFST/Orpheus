package com.yuukifst.orpheus.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OptimisticToggleTest {

    @Test
    fun playPauseToggleImpliesOppositePlayingState() {
        assertFalse(optimisticIsPlayingAfterToggle(wasPlaying = true))
        assertTrue(optimisticIsPlayingAfterToggle(wasPlaying = false))
    }

    @Test
    fun pendingFavoriteOverrideDroppedOnceDbAgrees() {
        val pruned = pruneAgreedFavoriteOverrides(
            dbFavoriteIds = setOf("X"),
            overrides = mapOf("X" to true),
        )
        assertTrue(pruned.isEmpty())
    }

    @Test
    fun pendingFavoriteOverrideRetainedWhileDbDisagrees() {
        val pruned = pruneAgreedFavoriteOverrides(
            dbFavoriteIds = emptySet(),
            overrides = mapOf("X" to true),
        )
        assertEquals(mapOf("X" to true), pruned)

        val merged = mergeFavoriteOverrides(emptySet(), pruned)
        assertEquals(setOf("X"), merged)
    }
}
