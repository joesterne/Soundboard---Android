package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SoundTile::class, AppSettings::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun soundboardDao(): SoundboardDao
}
