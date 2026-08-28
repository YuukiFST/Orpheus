package com.yuukifst.orpheus.presentation.viewmodel

import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubePlaybackErrorMappingTest {

    @Test
    fun unknownHostWithoutCancelIsNoInternet() {
        val message = userFacingYouTubePlaybackError(UnknownHostException("Unable to resolve host youtube.com"))
        assertTrue(message.contains("No internet connection"))
    }

    @Test
    fun canceledIoDoesNotLookLikeOffline() {
        val canceled = java.io.IOException("Canceled")
        assertEquals("", userFacingYouTubePlaybackError(canceled))
    }

    @Test
    fun unknownHostCausedByCancelDoesNotLookLikeOffline() {
        val error = UnknownHostException("Unable to resolve host youtube.com").apply {
            initCause(java.io.IOException("Canceled"))
        }
        assertEquals("", userFacingYouTubePlaybackError(error))
    }

    @Test
    fun cancellationExceptionIsSilent() {
        assertEquals("", userFacingYouTubePlaybackError(CancellationException("stale")))
    }
}
