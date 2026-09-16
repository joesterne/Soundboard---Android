package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SoundboardRepository(private val dao: SoundboardDao) {
    val allTiles: Flow<List<SoundTile>> = dao.getAllTiles()
    
    val settings: Flow<AppSettings> = dao.getSettings().map { it ?: AppSettings() }

    suspend fun updateTile(tile: SoundTile) {
        dao.insertTile(tile)
    }
    
    suspend fun updateTiles(tiles: List<SoundTile>) {
        dao.insertTiles(tiles)
    }

    suspend fun updateSettings(settings: AppSettings) {
        dao.updateSettings(settings)
    }

    suspend fun initializeTilesIfNeeded(maxTiles: Int) {
        // Just create some default tiles if empty, we do this in ViewModel maybe.
    }
}
