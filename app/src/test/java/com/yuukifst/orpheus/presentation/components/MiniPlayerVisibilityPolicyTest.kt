package com.yuukifst.orpheus.presentation.components

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiniPlayerVisibilityPolicyTest {

    @Test
    fun hideWhileUndoBarVisibleEvenIfSongStillPresent() {
        assertFalse(
            MiniPlayerVisibilityPolicy.shouldShowPlayerContent(
                currentSongId = "yt:1",
                showDismissUndoBar = true,
            )
        )
    }

    @Test
    fun hideAfterDismissCommittedEvenIfSongStillPresent() {
        assertFalse(
            MiniPlayerVisibilityPolicy.shouldShowPlayerContent(
                currentSongId = "yt:1",
                showDismissUndoBar = false,
                dismissJustCommitted = true,
            )
        )
    }

    @Test
    fun hideAfterUndoExpiresIfSongWasCleared() {
        assertFalse(
            MiniPlayerVisibilityPolicy.shouldShowPlayerContent(
                currentSongId = null,
                showDismissUndoBar = false,
            )
        )
    }

    @Test
    fun showOnlyWhenSongPresentAndUndoGone() {
        assertTrue(
            MiniPlayerVisibilityPolicy.shouldShowPlayerContent(
                currentSongId = "yt:1",
                showDismissUndoBar = false,
            )
        )
    }

    @Test
    fun doNotSnapOffsetBackJustBecauseUndoBarEnded() {
        assertFalse(
            MiniPlayerVisibilityPolicy.shouldSnapOffsetOnScreen(
                showPlayerContentArea = true,
                contentBecameVisible = true,
                dismissJustCommitted = true,
            )
        )
    }

    @Test
    fun snapOffsetWhenNewPlaybackShowsMiniPlayer() {
        assertTrue(
            MiniPlayerVisibilityPolicy.shouldSnapOffsetOnScreen(
                showPlayerContentArea = true,
                contentBecameVisible = true,
                dismissJustCommitted = false,
            )
        )
    }
}
