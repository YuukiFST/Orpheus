package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.Image

internal fun selectBestThumbnailUrl(thumbnails: List<Image>, videoId: String): String {
    val best = thumbnails.maxByOrNull { image ->
        val width = image.width.takeIf { it > 0 } ?: 0
        val height = image.height.takeIf { it > 0 } ?: 0
        width * height
    }?.url?.takeIf { it.isNotBlank() }

    return best ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
}
