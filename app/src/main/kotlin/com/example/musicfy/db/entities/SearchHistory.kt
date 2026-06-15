// SearchHistory.kt
// this thing is for search history

package com.example.musicfy.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "search_history",
    indices = [
        Index(
            value = ["query"],
            unique = true,
        ),
    ],
)
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
)
