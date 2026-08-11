package com.yuukifst.orpheus.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeMixedQueueAttachTest {

    @Test
    fun `single playing item matching start uses add-around without rebuild`() {
        val mediaIds = listOf("a", "b", "c", "d")
        val plan = planMixedQueueAttach(
            currentMediaItemCount = 1,
            currentMediaId = "b",
            resolvedMediaIds = mediaIds,
            startIndex = 1,
        )

        assertTrue(plan is MixedQueueAttachPlan.AddAroundCurrent)
        val add = plan as MixedQueueAttachPlan.AddAroundCurrent
        assertEquals(listOf("a"), add.beforeMediaIds)
        assertEquals(listOf("c", "d"), add.afterMediaIds)
    }

    @Test
    fun `mismatching single item falls back to replace`() {
        val plan = planMixedQueueAttach(
            currentMediaItemCount = 1,
            currentMediaId = "z",
            resolvedMediaIds = listOf("a", "b"),
            startIndex = 0,
        )

        assertTrue(plan is MixedQueueAttachPlan.ReplaceAll)
        val replace = plan as MixedQueueAttachPlan.ReplaceAll
        assertEquals(0, replace.startIndex)
        assertEquals(listOf("a", "b"), replace.mediaIds)
    }

    @Test
    fun `empty or multi-item timeline skips attach`() {
        val empty = planMixedQueueAttach(
            currentMediaItemCount = 0,
            currentMediaId = null,
            resolvedMediaIds = listOf("a"),
            startIndex = 0,
        )
        assertTrue(empty is MixedQueueAttachPlan.Skip)

        val multi = planMixedQueueAttach(
            currentMediaItemCount = 3,
            currentMediaId = "b",
            resolvedMediaIds = listOf("a", "b", "c"),
            startIndex = 1,
        )
        assertTrue(multi is MixedQueueAttachPlan.Skip)
    }
}
