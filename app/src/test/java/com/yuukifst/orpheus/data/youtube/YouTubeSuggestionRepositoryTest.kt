package com.yuukifst.orpheus.data.youtube

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class YouTubeSuggestionRepositoryTest {
    @Test
    fun suggestions_blankQuery_returnsEmpty() = runBlocking {
        val repo = YouTubeSuggestionRepository()
        assertEquals(emptyList<String>(), repo.suggestions("  "))
    }
}
