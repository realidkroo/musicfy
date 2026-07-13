// SpeedDialDao.kt
// the file functioned as speed dial dao

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SpeedDialItem)

    @Query("DELETE FROM speed_dial_item WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT * FROM speed_dial_item WHERE id = :id)")
    fun isPinned(id: String): Flow<Boolean>
}
