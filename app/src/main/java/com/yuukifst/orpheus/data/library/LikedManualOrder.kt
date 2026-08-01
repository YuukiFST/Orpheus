package com.yuukifst.orpheus.data.library

/**
 * Merges persisted manual order with the current favorite set.
 *
 * Keeps [orderedMediaIds] that are still favorites (in list order), then appends any
 * favorites missing from manual order sorted by date liked descending, then media id.
 */
fun mergeLikedManualOrder(
    favoriteMediaIds: Collection<String>,
    orderedMediaIds: List<String>,
    dateLikedById: Map<String, Long>,
): List<String> {
    if (favoriteMediaIds.isEmpty()) return emptyList()

    val favorites = favoriteMediaIds.toSet()
    val result = LinkedHashSet<String>()
    orderedMediaIds.forEach { mediaId ->
        if (mediaId in favorites) {
            result.add(mediaId)
        }
    }

    val missing = favorites - result
    if (missing.isNotEmpty()) {
        missing.sortedWith(
            compareByDescending<String> { dateLikedById[it] ?: 0L }
                .thenBy { it },
        ).forEach { result.add(it) }
    }

    return result.toList()
}
