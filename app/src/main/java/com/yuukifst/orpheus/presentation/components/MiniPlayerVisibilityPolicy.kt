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

    /**
     * Stale MediaController empties from dismiss clear can arrive after optimistic
     * [applyImmediatePlaybackUi]. Ignore those so the mini player can reappear.
     */
    fun shouldIgnoreStaleEmptyPlayerClear(
        preparingSongId: String?,
        currentSongId: String?,
        showDismissUndoBar: Boolean,
        dismissJustCommitted: Boolean,
    ): Boolean {
        if (showDismissUndoBar || dismissJustCommitted) return false
        return preparingSongId != null &&
            currentSongId != null &&
            preparingSongId == currentSongId
    }
}
