package com.yuukifst.orpheus.presentation.components

/**
 * Mini-player show/snap policy after swipe-dismiss + undo bar.
 */
object MiniPlayerVisibilityPolicy {
    fun shouldShowPlayerContent(
        currentSongId: String?,
        showDismissUndoBar: Boolean,
        dismissJustCommitted: Boolean = false,
    ): Boolean = currentSongId != null && !showDismissUndoBar && !dismissJustCommitted

    fun shouldSnapOffsetOnScreen(
        showPlayerContentArea: Boolean,
        contentBecameVisible: Boolean,
        dismissJustCommitted: Boolean,
    ): Boolean = showPlayerContentArea && contentBecameVisible && !dismissJustCommitted
}
