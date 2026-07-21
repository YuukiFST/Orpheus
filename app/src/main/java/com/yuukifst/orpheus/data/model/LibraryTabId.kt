package com.yuukifst.orpheus.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val title: String,
    val defaultSort: SortOption
) {
    SONGS("SONGS", "SONGS", SortOption.SongTitleAZ),
    ALBUMS("ALBUMS", "ALBUMS", SortOption.AlbumTitleAZ),
    ARTISTS("ARTIST", "ARTIST", SortOption.ArtistNameAZ),
    PLAYLISTS("PLAYLISTS", "PLAYLISTS", SortOption.PlaylistNameAZ),
    FOLDERS("FOLDERS", "FOLDERS", SortOption.FolderNameAZ),
    LIKED("LIKED", "LIKED", SortOption.LikedSongDateLiked);

    companion object {
        val DEFAULT_TAB_ORDER_KEYS: List<String> = listOf(
            LIKED.storageKey,
            SONGS.storageKey,
            ALBUMS.storageKey,
            ARTISTS.storageKey,
            PLAYLISTS.storageKey,
            FOLDERS.storageKey,
        )

        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }