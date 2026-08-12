package com.yuukifst.orpheus.presentation

/**
 * How to supply the overview/recents icon for [android.app.ActivityManager.TaskDescription].
 *
 * On API 33+, [android.app.ActivityManager.TaskDescription.Builder.setIcon] only accepts
 * [android.graphics.drawable.Icon] of type `TYPE_RESOURCE`. Passing `Icon.createWithBitmap`
 * throws [IllegalArgumentException] and aborts Activity launch.
 */
enum class RecentsTaskDescriptionIcon {
    /** Use [android.app.ActivityManager.TaskDescription.Builder.setIcon] with a drawable res id. */
    ResourceId,

    /** Use the pre-33 [android.app.ActivityManager.TaskDescription] Bitmap constructor. */
    DecodedBitmap,
}

internal fun chooseRecentsTaskDescriptionIcon(sdkInt: Int): RecentsTaskDescriptionIcon =
    if (sdkInt >= 33) {
        RecentsTaskDescriptionIcon.ResourceId
    } else {
        RecentsTaskDescriptionIcon.DecodedBitmap
    }
