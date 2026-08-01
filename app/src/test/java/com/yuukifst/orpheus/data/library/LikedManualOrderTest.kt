package com.yuukifst.orpheus.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LikedManualOrderTest {

    @Test
    fun `preserves manual order for favorites and drops stale ids`() {
        val result = mergeLikedManualOrder(
            favoriteMediaIds = setOf("1", "2"),
            orderedMediaIds = listOf("3", "2", "1"),
            dateLikedById = mapOf("1" to 100L, "2" to 200L),
        )

        assertEquals(listOf("2", "1"), result)
    }

    @Test
    fun `appends missing favorites by date liked desc then id`() {
        val result = mergeLikedManualOrder(
            favoriteMediaIds = setOf("a", "b", "c"),
            orderedMediaIds = listOf("a"),
            dateLikedById = mapOf(
                "a" to 1L,
                "b" to 3L,
                "c" to 2L,
            ),
        )

        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `returns empty list when there are no favorites`() {
        val result = mergeLikedManualOrder(
            favoriteMediaIds = emptySet(),
            orderedMediaIds = listOf("1", "2"),
            dateLikedById = emptyMap(),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `orders all missing favorites when manual order is empty`() {
        val result = mergeLikedManualOrder(
            favoriteMediaIds = setOf("2", "10", "1"),
            orderedMediaIds = emptyList(),
            dateLikedById = mapOf(
                "1" to 100L,
                "2" to 100L,
                "10" to 200L,
            ),
        )

        assertEquals(listOf("10", "1", "2"), result)
    }
}
