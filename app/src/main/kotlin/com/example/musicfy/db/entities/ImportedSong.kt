// ImportedSong.kt

package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import java.time.LocalDateTime

/**
 * A song that came into the app by an import, and from where. A song imported from two services has
 * two rows.
 *
 * Deliberately no foreign key to the song: songs are re-inserted and replaced in places, and a
 * cascade would quietly drop the record. A row whose song is gone simply never joins.
 *
 * [source] is an `ImportSource.key`.
 */
@Immutable
@Entity(
    tableName = "imported_song",
    primaryKeys = ["songId", "source"],
    indices = [Index(value = ["source"])],
)
data class ImportedSong(
    val songId: String,
    val source: String,
    val importedAt: LocalDateTime,
)

/** How many songs each service brought in. */
data class ImportedSourceCount(
    val source: String,
    val songCount: Int,
)
