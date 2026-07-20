package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.NewPipe

object YouTubeInitializer {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(YouTubeDownloaderImpl.getInstance())
            initialized = true
        }
    }
}
