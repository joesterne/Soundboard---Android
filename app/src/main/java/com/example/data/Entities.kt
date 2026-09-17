package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tiles")
data class SoundTile(
    @PrimaryKey val index: Int,
    val name: String,
    val color: Int, // ARGB
    val audioPath: String?, // path to local file, null if empty
    val volume: Float = 1.0f,
    val isLooping: Boolean = false,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val playCount: Int = 0,
    val category: String = "",
    val playbackSpeed: Float = 1.0f
)

@Entity(tableName = "settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val rows: Int = 4,
    val cols: Int = 4,
    val backgroundColor: Int = 0xFF121212.toInt(),
    val fontSizeSp: Float = 14f,
    val backgroundPhotoPath: String? = null,
    val masterVolume: Float = 1.0f
)

@Entity(tableName = "favorites")
data class FavoriteTile(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int, // ARGB
    val audioPath: String?, // path to local file, null if empty
    val volume: Float = 1.0f,
    val isLooping: Boolean = false,
    val trimStartMs: Long? = null,
    val trimEndMs: Long? = null,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val playbackSpeed: Float = 1.0f
)
