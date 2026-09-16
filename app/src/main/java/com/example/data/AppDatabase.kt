package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SoundTile::class, AppSettings::class, FavoriteTile::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun soundboardDao(): SoundboardDao
}
