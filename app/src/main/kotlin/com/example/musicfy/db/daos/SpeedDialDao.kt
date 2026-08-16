// SpeedDialDao.kt

package com.example.musicfy.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicfy.db.entities.SpeedDialItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial_item ORDER BY createDate DESC")
    fun getAll(): Flow<List<SpeedDialItem>>

    @Query("SELECT * FROM speed_dial_item ORDER BY createDate ASC LIMIT 1")
    suspend fun oldest(): SpeedDialItem?

    @Query("SELECT COUNT(*) FROM speed_dial_item")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialItem)

    @Query("DELETE FROM speed_dial_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT * FROM speed_dial_item WHERE id = :id)")
    fun isPinned(id: String): Flow<Boolean>

    companion object {
        /**
         * Pinning is meant to surface a handful of things you reach for constantly, on a Library
         * home screen that only has room for a 3x3 grid — an unbounded list stops being a
         * shortcut and starts being a second, worse library. Enforced centrally in
         * [com.example.musicfy.db.MusicDatabase.pinToSpeedDial] rather than in each menu that
         * offers "Pin to Speed dial", so every call site gets the cap automatically.
         */
        const val MaxPinned = 9
    }
}
