package com.yuukifst.orpheus.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = false,
    val delayAlbumCarousel: Boolean = true,
    val delaySongMetadata: Boolean = true,
    val delayProgressBar: Boolean = true,
    val delayControls: Boolean = false,
    val showPlaceholders: Boolean = true,
    val transparentPlaceholders: Boolean = false,
    val applyPlaceholdersOnClose: Boolean = false,
    val switchOnDragRelease: Boolean = true,
    val contentAppearThresholdPercent: Int = 70,
    val contentCloseThresholdPercent: Int = 0
)
