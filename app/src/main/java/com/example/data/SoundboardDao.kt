package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundboardDao {
    @Query("SELECT * FROM tiles ORDER BY `index` ASC")
    fun getAllTiles(): Flow<List<SoundTile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTile(tile: SoundTile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiles(tiles: List<SoundTile>)

    @Query("SELECT * FROM settings WHERE id = 1")
    fun getSettings(): Flow<AppSettings?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSettings(settings: AppSettings)
}
