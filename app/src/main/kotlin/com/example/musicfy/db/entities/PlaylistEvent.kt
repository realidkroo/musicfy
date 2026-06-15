// PlaylistEvent.kt
// the file functioned as playlist event

package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "playlist_event",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(index = true) val playlistId: String,
    val timestamp: LocalDateTime,
)
