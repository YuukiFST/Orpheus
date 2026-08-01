package com.yuukifst.orpheus.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.yuukifst.orpheus.data.model.Song
import com.yuukifst.orpheus.data.model.SortOption
import com.yuukifst.orpheus.data.model.StorageFilter
import com.yuukifst.orpheus.data.preferences.UserPreferencesRepository
import com.yuukifst.orpheus.data.repository.MusicRepository
import com.yuukifst.orpheus.MainCoroutineExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class LibraryStateHolderLikedReorderTest {

    private val song1 = Song(
        id = "1",
        title = "Song 1",
        artist = "Artist A",
        artistId = 1L,
        albumId = 1L,
        album = "Album",
        path = "path",
        contentUriString = "content://dummy/1",
        albumArtUriString = "cover1.png",
        duration = 180000L,
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
    )
    private val song2 = song1.copy(id = "2", title = "Song 2")
    private val song3 = song1.copy(id = "3", title = "Song 3")

    @Test
    fun reorderLikedSongs_setsManualSortAndPersistsOrder() = runTest {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        coEvery { userPreferencesRepository.hideLocalMediaFlow } returns flowOf(false)
        coEvery { userPreferencesRepository.songsSortOptionFlow } returns flowOf(SortOption.SongDefaultOrder.storageKey)
        coEvery { userPreferencesRepository.albumsSortOptionFlow } returns flowOf(SortOption.AlbumTitleAZ.storageKey)
        coEvery { userPreferencesRepository.artistsSortOptionFlow } returns flowOf(SortOption.ArtistNameAZ.storageKey)
        coEvery { userPreferencesRepository.foldersSortOptionFlow } returns flowOf(SortOption.FolderNameAZ.storageKey)
        coEvery { userPreferencesRepository.likedSongsSortOptionFlow } returns flowOf(SortOption.LikedSongDateLiked.storageKey)
        coEvery { userPreferencesRepository.lastStorageFilterFlow } returns flowOf(StorageFilter.ALL)
        coEvery { musicRepository.getFavoriteSongsOnce(StorageFilter.ALL) } returns listOf(song1, song2, song3)
        coEvery { musicRepository.reorderLikedSongs(any()) } returns Unit
        coEvery { userPreferencesRepository.setLikedSongsSortOption(any()) } returns Unit

        val holder = LibraryStateHolder(musicRepository, userPreferencesRepository)
        holder.initialize(this)
        advanceUntilIdle()
        holder.setLikedReorderMode(true)
        holder.refreshLikedSongsFullList()
        advanceUntilIdle()

        holder.reorderLikedSongs(listOf(song2.id, song1.id, song3.id))

        assertThat(holder.currentFavoriteSortOption.value).isEqualTo(SortOption.LikedSongManual)
        assertThat(holder.likedSongsFullList.value.map { it.id }).containsExactly(song2.id, song1.id, song3.id).inOrder()

        advanceUntilIdle()
        coVerify(exactly = 1) { musicRepository.reorderLikedSongs(listOf(song2.id, song1.id, song3.id)) }
        coVerify(exactly = 1) {
            userPreferencesRepository.setLikedSongsSortOption(SortOption.LikedSongManual.storageKey)
        }
    }
}
