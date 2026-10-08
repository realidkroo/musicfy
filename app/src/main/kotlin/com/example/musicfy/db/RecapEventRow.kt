// RecapEventRow.kt

package com.example.musicfy.db

/**
 * One play from the `event` table without its song attached. [timestamp] is the stored column as-is:
 * the local wall-clock time of the play, encoded as epoch millis in UTC (see Converters).
 */
data class RecapEventRow(
    val songId: String,
    val timestamp: Long,
    val playTime: Long,
)
