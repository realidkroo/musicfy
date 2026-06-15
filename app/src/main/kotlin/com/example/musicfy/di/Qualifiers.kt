// Qualifiers.kt
// this thing is part of qualifiers

package com.example.musicfy.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
