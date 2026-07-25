package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeUploaderLabelTest {
    @Test
    fun preferPrimaryYouTubeUploader_stripsCollaboratorByline() {
        assertEquals(
            "nikmouu",
            preferPrimaryYouTubeUploader("nikmouu and Novatroop"),
        )
        assertEquals(
            "nikmouu",
            preferPrimaryYouTubeUploader("nikmouu and Keetheweeb"),
        )
        assertEquals(
            "Artist",
            preferPrimaryYouTubeUploader("Artist & Other"),
        )
        assertEquals(
            "Solo",
            preferPrimaryYouTubeUploader("Solo"),
        )
    }
}
