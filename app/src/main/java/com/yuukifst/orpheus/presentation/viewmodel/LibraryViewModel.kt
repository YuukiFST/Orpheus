package com.yuukifst.orpheus.presentation.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryStateHolder: LibraryStateHolder
) : ViewModel() {

    val songsPagingFlow = libraryStateHolder.songsPagingFlow

    val albumsPagingFlow = libraryStateHolder.albumsPagingFlow

    val artistsPagingFlow = libraryStateHolder.artistsPagingFlow

    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow

    val favoriteSongCountFlow = libraryStateHolder.favoriteSongCountFlow

    val isLoadingLibrary = libraryStateHolder.isLoadingLibrary
}
