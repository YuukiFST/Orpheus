package com.yuukifst.orpheus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuukifst.orpheus.data.youtube.YouTubeInitializer
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrpheusSmokeTest {
    @Test
    fun appContext_hasOrpheusPackageId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.yuukifst.orpheus.debug", context.packageName)
    }

    @Test
    fun youTubeInitializer_runsOnDevice() {
        YouTubeInitializer.ensureInitialized()
        assertTrue(true)
    }

    @Test
    fun youTubeTrack_mediaIdFormat() {
        val track = YouTubeTrack("dQw4w9WgXcQ", "Title", "Channel", "", 1000)
        assertEquals("youtube_dQw4w9WgXcQ", track.mediaId)
    }
}
