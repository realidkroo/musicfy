// LyricsHelperEntryPoint.kt
// this thing is part of lyrics helper entry point

package com.example.musicfy.di

import com.example.musicfy.lyrics.LyricsHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsHelperEntryPoint {
    fun lyricsHelper(): LyricsHelper
}
