package com.yuukifst.orpheus.ui

/** Implied `isPlaying` after a play/pause transport command. */
internal fun optimisticIsPlayingAfterToggle(wasPlaying: Boolean): Boolean = !wasPlaying

/** Apply pending favorite overrides on top of the Room-backed favorite set. */
internal fun mergeFavoriteOverrides(
    dbFavoriteIds: Set<String>,
    overrides: Map<String, Boolean>,
): Set<String> {
    if (overrides.isEmpty()) return dbFavoriteIds
    val result = dbFavoriteIds.toMutableSet()
    for ((id, favorite) in overrides) {
        if (favorite) result.add(id) else result.remove(id)
    }
    return result
}

/** Drop overrides that already agree with the database. */
internal fun pruneAgreedFavoriteOverrides(
    dbFavoriteIds: Set<String>,
    overrides: Map<String, Boolean>,
): Map<String, Boolean> =
    overrides.filter { (id, favorite) -> dbFavoriteIds.contains(id) != favorite }
