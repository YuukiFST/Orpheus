package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.Image

/** Minimum edge length for list thumbnails (matches [SmartImageYouTubeListTargetSize]). */
private const val MIN_LIST_THUMBNAIL_PX = 240

internal fun selectBestThumbnailUrl(thumbnails: List<Image>, videoId: String): String {
    val withUrl = thumbnails.filter { it.url?.isNotBlank() == true }
    if (withUrl.isEmpty()) {
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    val largeEnough = withUrl.filter { image ->
        val width = image.width.takeIf { it > 0 } ?: 0
        val height = image.height.takeIf { it > 0 } ?: 0
        width == 0 && height == 0 ||
            width >= MIN_LIST_THUMBNAIL_PX ||
            height >= MIN_LIST_THUMBNAIL_PX
    }

    val candidates = largeEnough.ifEmpty { withUrl }
    val best = candidates.minByOrNull { image ->
        val width = image.width.takeIf { it > 0 } ?: Int.MAX_VALUE / 2
        val height = image.height.takeIf { it > 0 } ?: Int.MAX_VALUE / 2
        width.toLong() * height
    }?.url?.takeIf { it.isNotBlank() }

    return best ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
