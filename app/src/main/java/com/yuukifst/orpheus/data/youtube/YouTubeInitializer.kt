package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeInitializer @Inject constructor(
    private val downloader: YouTubeDownloaderImpl,
) {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader)
            initialized = true
        }
    }
}
