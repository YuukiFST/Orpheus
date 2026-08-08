package com.yuukifst.orpheus.presentation.viewmodel

import androidx.media3.common.Player
import com.yuukifst.orpheus.data.service.player.nextIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UserQueueInsertPolicyTest {

    @Test
    fun likedPlaylistFirstAddTrimsContinuationAndInsertsAfterCurrent() {
        val plan = planUserQueueInsert(
            mediaItemCount = 5,
            currentIndex = 1,
            userQueueTailActive = false,
        )

        assertEquals(2, plan.removeFromIndex)
        assertEquals(5, plan.removeToExclusive)
        assertEquals(2, plan.insertIndex)
        assertTrue(plan.forceRepeatOff)
        assertTrue(plan.enableStopOnEnd)
        assertTrue(plan.markUserQueueTailActive)
    }

    @Test
    fun subsequentUserQueueAddAppendsWithoutTrimming() {
        // After interrupt: [likedCurrent, yt1]
        val plan = planUserQueueInsert(
            mediaItemCount = 2,
            currentIndex = 0,
            userQueueTailActive = true,
        )

        assertNull(plan.removeFromIndex)
        assertNull(plan.removeToExclusive)
        assertEquals(2, plan.insertIndex)
        assertTrue(plan.forceRepeatOff)
        assertTrue(plan.enableStopOnEnd)
    }

    @Test
    fun afterUserQueueInsertNextTrackIsQueuedNotLikedContinuation() {
        // Liked timeline indices 0..4, playing 0. User queues one track.
        val plan = planUserQueueInsert(
            mediaItemCount = 5,
            currentIndex = 0,
            userQueueTailActive = false,
        )
        val sizeAfterTrim = plan.removeFromIndex?.let { from ->
            5 - ((plan.removeToExclusive ?: 5) - from)
        } ?: 5
        val sizeAfterInsert = sizeAfterTrim + 1

        assertEquals(1, plan.insertIndex)
        assertEquals(
            plan.insertIndex,
            nextIndex(0, sizeAfterInsert, Player.REPEAT_MODE_OFF),
        )
        // After the queued item (index 1), nothing else plays.
        assertNull(nextIndex(1, sizeAfterInsert, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun likedRepeatAllWouldSkipQueuedItemAtEnd_policyPreventsThatShape() {
        // Bug shape: append at end while REPEAT_ALL keeps serving Liked.
        val buggyAppendIndex = 5
        assertEquals(
            1,
            nextIndex(0, 6, Player.REPEAT_MODE_ALL),
            "with append-at-end, next is still Liked[1]",
        )
        assertEquals(buggyAppendIndex, 5)

        val plan = planUserQueueInsert(
            mediaItemCount = 5,
            currentIndex = 0,
            userQueueTailActive = false,
        )
        assertEquals(1, plan.insertIndex)
        assertTrue(plan.forceRepeatOff)
    }

    @Test
    fun lastPlaylistItemNeedsNoTrimStillStopsAfterQueue() {
        val plan = planUserQueueInsert(
            mediaItemCount = 3,
            currentIndex = 2,
            userQueueTailActive = false,
        )

        assertNull(plan.removeFromIndex)
        assertEquals(3, plan.insertIndex)
        assertTrue(plan.forceRepeatOff)
        assertTrue(plan.enableStopOnEnd)
    }
}
