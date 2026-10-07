package com.yuukifst.orpheus.data.youtube

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeInitializer @Inject constructor(
    private val downloader: YouTubeDownloaderImpl,
) {
    @Volatile
    private var initialized = false

    /** ISO 3166 code from Settings; blank keeps NewPipe's default. */
    @Volatile
    private var contentCountryCode: String = ""

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader)
            applyContentCountry()
            initialized = true
        }
    }

    /** Sets the YouTube `gl` region hint used by search ranking. */
    fun setContentCountry(code: String) {
        synchronized(this) {
            contentCountryCode = code
            if (initialized) applyContentCountry()
        }
    }

    private fun applyContentCountry() {
        val code = contentCountryCode
        NewPipe.setPreferredContentCountry(
            if (code.isBlank()) ContentCountry.DEFAULT else ContentCountry(code),
        )
    }
}
