package com.yuukifst.orpheus.di

import com.yuukifst.orpheus.data.database.MusicDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InstrumentationEntryPoint {
    fun musicDao(): MusicDao
}
