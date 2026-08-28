package com.yuukifst.orpheus.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YouTubeQueueReorderMovesTest {

    @Test
    fun nextMoveFindsFirstMismatch() {
        assertEquals(2 to 0, nextMediaItemMove(listOf("a", "b", "c"), listOf("c", "a", "b")))
    }

    @Test
    fun applyMovesReachesDesiredOrder() {
        val current = mutableListOf("a", "b", "c", "d")
        val desired = listOf("d", "a", "c", "b")
        applyMediaItemMoves(current, desired) { _, _ -> }
        assertEquals(desired, current)
    }

    @Test
    fun differentSetsAreNotMoved() {
        assertNull(nextMediaItemMove(listOf("a", "b"), listOf("a", "c")))
    }
}
