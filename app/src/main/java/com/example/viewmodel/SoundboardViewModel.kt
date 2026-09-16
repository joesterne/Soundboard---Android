package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.audio.AudioRecorder
import com.example.data.AppDatabase
import com.example.data.AppSettings
import com.example.data.SoundTile
import com.example.data.FavoriteTile
import com.example.data.Preset
import com.example.data.SoundboardRepository
import com.example.utils.ExportImportUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SoundboardViewModel(application: Application) : AndroidViewModel(application) {
    private val db = androidx.room.Room.databaseBuilder(
        application,
        AppDatabase::class.java, "soundboard-db"
    ).fallbackToDestructiveMigration().build()
    private val repository = SoundboardRepository(db.soundboardDao())
    
    val audioEngine = AudioEngine(application)
    val audioRecorder = AudioRecorder(application)
    
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
        
    val tiles: StateFlow<List<SoundTile>> = repository.allTiles
        .onEach { tileList ->
            // Preload audio into SoundPool proactively to eliminate play latency
            tileList.forEach { tile ->
                tile.audioPath?.let { audioEngine.loadSound(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteTile>> = repository.allFavorites
        .onEach { favList ->
            favList.forEach { fav ->
                fav.audioPath?.let { audioEngine.loadSound(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isRecording = MutableStateFlow(false)
    val currentlyEditingTile = MutableStateFlow<SoundTile?>(null)
    
    private val _playHistory = MutableStateFlow<List<SoundTile>>(emptyList())
    val playHistory: StateFlow<List<SoundTile>> = _playHistory

    private val presetsDir = File(application.filesDir, "presets").apply { mkdirs() }
    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    val presets: StateFlow<List<Preset>> = _presets

    init {
        loadPresets()
    }

    private fun loadPresets() {
        val files = presetsDir.listFiles { _, name -> name.endsWith(".zip") } ?: emptyArray()
        _presets.value = files.map {
            Preset(
                id = it.nameWithoutExtension,
                name = it.nameWithoutExtension.replace("_", " "),
                file = it
            )
        }.sortedBy { it.name }
    }

    fun saveCurrentBoardAsPreset(name: String) {
        viewModelScope.launch {
            val fileName = name.replace(" ", "_") + ".zip"
            val file = File(presetsDir, fileName)
            exportBoard(file)
            loadPresets()
        }
    }

    fun loadPreset(preset: Preset) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val data = ExportImportUtils.importFromZip(preset.file, app.filesDir)
            if (data != null) {
                repository.updateSettings(data.settings)
                
                val updatedTiles = data.tiles.map { t ->
                    if (t.audioPath != null) {
                        val originalFile = File(t.audioPath)
                        val newFile = File(app.filesDir, originalFile.name)
                        t.copy(audioPath = newFile.absolutePath)
                    } else {
                        t
                    }
                }
                repository.updateTiles(updatedTiles)
            }
        }
    }

    fun deletePreset(preset: Preset) {
        if (preset.file.exists()) {
            preset.file.delete()
        }
        loadPresets()
    }

    fun playFavorite(favorite: FavoriteTile) {
        favorite.audioPath?.let { path ->
            // We use hashCode of the path as a pseudo-ID for the audio engine for favorites
            val fakeId = 1000 + path.hashCode().rem(1000)
            if (favorite.isLooping && audioEngine.isLoopingActive(fakeId)) {
                audioEngine.stopSound(fakeId)
            } else {
                val masterVol = settings.value.masterVolume
                audioEngine.playSound(
                    id = fakeId,
                    path = path,
                    volume = favorite.volume * masterVol,
                    isLooping = favorite.isLooping,
                    trimStartMs = favorite.trimStartMs,
                    trimEndMs = favorite.trimEndMs
                )
            }
        }
    }

    fun toggleFavorite(tile: SoundTile) {
        viewModelScope.launch {
            val currentFavs = favorites.value
            val existing = currentFavs.find { it.audioPath == tile.audioPath && it.audioPath != null }
            if (existing != null) {
                repository.removeFavorite(existing)
            } else {
                if (tile.audioPath != null) {
                    // Copy audio file to ensure it's not overwritten/lost?
                    // Audio files are in filesDir with timestamp, so they persist until explicitly deleted.
                    val newFav = FavoriteTile(
                        id = UUID.randomUUID().toString(),
                        name = tile.name,
                        color = tile.color,
                        audioPath = tile.audioPath,
                        volume = tile.volume,
                        isLooping = tile.isLooping,
                        trimStartMs = tile.trimStartMs,
                        trimEndMs = tile.trimEndMs
                    )
                    repository.addFavorite(newFav)
                }
            }
        }
    }

    fun removeFavorite(favorite: FavoriteTile) {
        viewModelScope.launch {
            repository.removeFavorite(favorite)
        }
    }

    fun playTile(tile: SoundTile) {
        tile.audioPath?.let { path ->
            if (tile.isLooping && audioEngine.isLoopingActive(tile.index)) {
                audioEngine.stopSound(tile.index)
            } else {
                val masterVol = settings.value.masterVolume
                audioEngine.playSound(
                    id = tile.index,
                    path = path, 
                    volume = tile.volume * masterVol, 
                    isLooping = tile.isLooping,
                    trimStartMs = tile.trimStartMs,
                    trimEndMs = tile.trimEndMs
                )
                // Add to history (max 20 items)
                val newHistory = _playHistory.value.toMutableList()
                newHistory.removeAll { it.index == tile.index } // Remove duplicate if it was played recently
                newHistory.add(0, tile)
                if (newHistory.size > 20) {
                    newHistory.removeAt(newHistory.lastIndex)
                }
                _playHistory.value = newHistory
                
                // Increment playCount
                viewModelScope.launch {
                    repository.updateTile(tile.copy(playCount = tile.playCount + 1))
                }
            }
        }
    }

    fun updateSettings(rows: Int, cols: Int, bgColor: Int, fontSizeSp: Float, masterVolume: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(
                rows = rows, 
                cols = cols, 
                backgroundColor = bgColor, 
                fontSizeSp = fontSizeSp,
                masterVolume = masterVolume
            ))
            
            // Initialize missing tiles if grid size changed
            val totalNeeded = rows * cols
            val currentTiles = tiles.value
            val currentCount = currentTiles.size
            if (currentCount < totalNeeded) {
                val newTiles = (currentCount until totalNeeded).map { idx ->
                    SoundTile(
                        index = idx,
                        name = "Tile ${idx + 1}",
                        color = 0xFF424242.toInt(), // Default gray
                        audioPath = null,
                        volume = 1.0f
                    )
                }
                repository.updateTiles(newTiles)
            }
        }
    }

    fun updateBackgroundPhoto(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                val current = settings.value
                repository.updateSettings(current.copy(backgroundPhotoPath = null))
                return@launch
            }
            
            val app = getApplication<Application>()
            val file = File(app.filesDir, "background_${System.currentTimeMillis()}.jpg")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val current = settings.value
                repository.updateSettings(current.copy(backgroundPhotoPath = file.absolutePath))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveTile(tile: SoundTile) {
        viewModelScope.launch {
            repository.updateTile(tile)
            currentlyEditingTile.value = null
        }
    }

    fun startEditing(tile: SoundTile) {
        currentlyEditingTile.value = tile
    }

    fun cancelEditing() {
        currentlyEditingTile.value = null
    }

    fun autoArrangeTiles() {
        viewModelScope.launch {
            val currentTiles = tiles.value.toMutableList()
            if (currentTiles.isEmpty()) return@launch

            // Sort tiles by playCount descending, then alphabetically, but empty tiles (audioPath == null) go last
            val sortedContents = currentTiles.sortedWith(
                compareBy<SoundTile> { it.audioPath == null } // true (empty) is sorted after false (has audio)
                    .thenByDescending { it.playCount }
                    .thenBy { it.name }
            )

            // Re-assign indices while keeping the contents sorted
            val updatedTiles = sortedContents.mapIndexed { i, tile ->
                // The indices are supposed to match the grid positions. 
                // Grid has size settings.rows * settings.cols
                tile.copy(index = i)
            }
            
            // Delete old tiles and insert new ones
            repository.replaceTiles(updatedTiles)
        }
    }
    
    fun swapTiles(index1: Int, index2: Int) {
        viewModelScope.launch {
            val t1 = tiles.value.find { it.index == index1 }
            val t2 = tiles.value.find { it.index == index2 }
            if (t1 != null && t2 != null) {
                // We just swap properties but keep indices same, or swap indices
                val newT1 = t1.copy(index = index2)
                val newT2 = t2.copy(index = index1)
                repository.updateTiles(listOf(newT1, newT2))
            }
        }
    }

    fun startRecording(index: Int) {
        val file = File(getApplication<Application>().filesDir, "rec_$index.mp4")
        audioRecorder.startRecording(file)
        isRecording.value = true
    }

    fun stopRecording(tile: SoundTile) {
        val path = audioRecorder.stopRecording()
        isRecording.value = false
        if (path != null) {
            val updatedTile = tile.copy(audioPath = path)
            currentlyEditingTile.value = updatedTile
        }
    }

    fun importAudioFromUri(tile: SoundTile, uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = File(app.filesDir, "imported_${tile.index}_${System.currentTimeMillis()}.mp3")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val updatedTile = tile.copy(audioPath = file.absolutePath)
                currentlyEditingTile.value = updatedTile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importMultipleAudioFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            
            // Find empty tiles
            val allCurrentTiles = tiles.value.sortedBy { it.index }
            val emptyTiles = allCurrentTiles.filter { it.audioPath == null }
            
            if (emptyTiles.isEmpty() || uris.isEmpty()) return@launch

            val tilesToUpdate = mutableListOf<SoundTile>()
            val maxImports = minOf(uris.size, emptyTiles.size)
            
            for (i in 0 until maxImports) {
                val uri = uris[i]
                val tile = emptyTiles[i]
                val file = File(app.filesDir, "bulk_imported_${tile.index}_${System.currentTimeMillis()}.mp3")
                
                try {
                    // Try to get filename for tile name
                    var fileName = "Imported"
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                fileName = cursor.getString(nameIndex).substringBeforeLast('.')
                            }
                        }
                    }

                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // Select a random bright color for the newly imported tile
                    val newColor = listOf(
                        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
                        0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF00BCD4.toInt(),
                        0xFF4CAF50.toInt(), 0xFFFFC107.toInt(), 0xFFFF5722.toInt()
                    ).random()

                    tilesToUpdate.add(tile.copy(audioPath = file.absolutePath, name = fileName, color = newColor))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (tilesToUpdate.isNotEmpty()) {
                repository.updateTiles(tilesToUpdate)
            }
        }
    }

    fun exportBoard(outputFile: File): Boolean {
        return ExportImportUtils.exportToZip(getApplication(), settings.value, tiles.value, outputFile)
    }

    fun importBoard(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val data = ExportImportUtils.importFromZip(app, uri, app.filesDir)
            if (data != null) {
                repository.updateSettings(data.settings)
                
                // Fix paths to point to new local files
                val updatedTiles = data.tiles.map { t ->
                    if (t.audioPath != null) {
                        val originalFile = File(t.audioPath)
                        val newFile = File(app.filesDir, originalFile.name)
                        t.copy(audioPath = newFile.absolutePath)
                    } else {
                        t
                    }
                }
                repository.updateTiles(updatedTiles)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
