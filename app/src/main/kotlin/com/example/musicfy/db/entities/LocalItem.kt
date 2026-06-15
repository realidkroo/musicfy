// LocalItem.kt
// this thing is for local item

package com.example.musicfy.db.entities

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}
