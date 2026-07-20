package com.yuukifst.orpheus.utils

import com.yuukifst.orpheus.data.model.Song

fun String.isYouTubeMediaId(): Boolean = startsWith("youtube_")

fun Song.isYouTubeSource(): Boolean = id.isYouTubeMediaId()
