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
    ).fallbackToDestructiveMigration(true).build()
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
    
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage

    fun clearToastMessage() {
        _toastMessage.value = null
    }

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
            val safeName = ExportImportUtils.sanitizeFileName(name.replace(" ", "_")).removeSuffix(".zip")
            if (safeName.isNotBlank()) {
                val fileName = "$safeName.zip"
                val file = File(presetsDir, fileName).canonicalFile
                if (file.path.startsWith(presetsDir.canonicalPath + File.separator)) {
                    exportBoard(file)
                    loadPresets()
                }
            }
        }
    }

    fun loadPreset(preset: Preset) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val canonicalPresetPath = preset.file.canonicalPath
            val canonicalPresetsDirPath = presetsDir.canonicalPath
            if (canonicalPresetPath.startsWith(canonicalPresetsDirPath + File.separator)) {
                val data = ExportImportUtils.importFromZip(app, preset.file, app.filesDir)
                if (data != null) {
                    repository.updateSettings(data.settings)
                    repository.updateTiles(data.tiles)
                }
            }
        }
    }

    fun deletePreset(preset: Preset) {
        try {
            val canonicalPresetPath = preset.file.canonicalPath
            val canonicalPresetsDirPath = presetsDir.canonicalPath
            if (canonicalPresetPath.startsWith(canonicalPresetsDirPath + File.separator) && preset.file.exists()) {
                preset.file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                    trimEndMs = favorite.trimEndMs,
                    fadeInMs = favorite.fadeInMs,
                    fadeOutMs = favorite.fadeOutMs,
                    playbackSpeed = favorite.playbackSpeed
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
                        trimEndMs = tile.trimEndMs,
                        fadeInMs = tile.fadeInMs,
                        fadeOutMs = tile.fadeOutMs,
                        playbackSpeed = tile.playbackSpeed
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
                    trimEndMs = tile.trimEndMs,
                    fadeInMs = tile.fadeInMs,
                    fadeOutMs = tile.fadeOutMs,
                    playbackSpeed = tile.playbackSpeed
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
            val safeRows = rows.coerceIn(2, 8)
            val safeCols = cols.coerceIn(2, 8)
            val safeFontSize = fontSizeSp.coerceIn(8f, 32f)
            val safeVolume = masterVolume.coerceIn(0f, 1f)

            val current = settings.value
            repository.updateSettings(current.copy(
                rows = safeRows, 
                cols = safeCols, 
                backgroundColor = bgColor, 
                fontSizeSp = safeFontSize,
                masterVolume = safeVolume
            ))
            
            // Initialize missing tiles if grid size changed
            val totalNeeded = safeRows * safeCols
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
            val app = getApplication<Application>()
            val current = settings.value
            val oldPhotoPath = current.backgroundPhotoPath
            if (oldPhotoPath != null) {
                try {
                    val oldFile = File(oldPhotoPath)
                    if (oldFile.exists() && ExportImportUtils.isFileWithinAppStorage(app, oldFile)) {
                        oldFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (uri == null) {
                repository.updateSettings(current.copy(backgroundPhotoPath = null))
                return@launch
            }
            
            val file = File(app.filesDir, "background_${System.currentTimeMillis()}.jpg")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            totalBytes += read
                            if (totalBytes > 25 * 1024 * 1024L) { // Max 25 MB
                                throw SecurityException("Background photo exceeds maximum allowed size")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                repository.updateSettings(current.copy(backgroundPhotoPath = file.absolutePath))
            } catch (e: Exception) {
                e.printStackTrace()
                if (file.exists()) file.delete()
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
        val app = getApplication<Application>()
        val recordingsDir = File(app.filesDir, "recordings").apply { mkdirs() }
        val safeIndex = index.coerceIn(0, 100)
        val file = File(recordingsDir, "rec_${safeIndex}_${System.currentTimeMillis()}.mp4")
        audioRecorder.startRecording(file)
        isRecording.value = true
    }

    fun stopRecording(tile: SoundTile) {
        val path = audioRecorder.stopRecording()
        isRecording.value = false
        if (path != null) {
            val app = getApplication<Application>()
            // Clean up previous audio file if it was a recorded file
            tile.audioPath?.let { oldPath ->
                try {
                    val oldFile = File(oldPath)
                    if (oldFile.exists() && ExportImportUtils.isFileWithinAppStorage(app, oldFile)) {
                        oldFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val updatedTile = tile.copy(audioPath = path)
            currentlyEditingTile.value = updatedTile
        }
    }

    fun importAudioFromUri(tile: SoundTile, uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val safeIndex = tile.index.coerceIn(0, 100)
            val file = File(app.filesDir, "imported_${safeIndex}_${System.currentTimeMillis()}.mp3")
            try {
                app.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var totalBytes = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            totalBytes += read
                            if (totalBytes > 50 * 1024 * 1024L) { // Max 50 MB
                                throw SecurityException("Imported audio file exceeds 50MB limit")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                
                // Remove previous local file if it existed
                tile.audioPath?.let { oldPath ->
                    try {
                        val oldFile = File(oldPath)
                        if (oldFile.exists() && ExportImportUtils.isFileWithinAppStorage(app, oldFile)) {
                            oldFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                val updatedTile = tile.copy(audioPath = file.absolutePath)
                currentlyEditingTile.value = updatedTile
            } catch (e: Exception) {
                e.printStackTrace()
                if (file.exists()) file.delete()
            }
        }
    }

    fun assignGeneratedAudio(tile: SoundTile, generatedFile: File, suggestedName: String? = null) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            // Clean up old audio file if it was within app storage
            tile.audioPath?.let { oldPath ->
                try {
                    val oldFile = File(oldPath)
                    if (oldFile.exists() && ExportImportUtils.isFileWithinAppStorage(app, oldFile)) {
                        oldFile.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val updatedTile = tile.copy(
                audioPath = generatedFile.absolutePath,
                name = if (suggestNameOrKeep(tile.name, suggestedName)) suggestedName ?: tile.name else tile.name
            )
            currentlyEditingTile.value = updatedTile
            repository.updateTile(updatedTile)
            _toastMessage.value = "Assigned generated sound to Tile ${tile.index + 1}!"
        }
    }

    /**
     * Assigns a newly generated audio file to the next available empty tile.
     * If no tiles are empty, automatically expands the grid to accommodate it.
     */
    fun addGeneratedAudioToNextAvailableTile(generatedFile: File, suggestedName: String? = null) {
        viewModelScope.launch {
            var currentTiles = tiles.value
            var emptyTile = currentTiles.firstOrNull { it.audioPath == null }

            if (emptyTile == null) {
                // Auto-expand grid
                val currentSettings = settings.value
                var rows = currentSettings.rows
                var cols = currentSettings.cols
                if (cols < 8) {
                    cols++
                } else if (rows < 8) {
                    rows++
                } else {
                    _toastMessage.value = "Soundboard is full (8x8 limit reached)"
                    return@launch
                }

                repository.updateSettings(currentSettings.copy(rows = rows, cols = cols))
                val totalNeeded = rows * cols
                val newTilesList = currentTiles.toMutableList()
                val currentCount = newTilesList.size
                for (idx in currentCount until totalNeeded) {
                    newTilesList.add(
                        SoundTile(
                            index = idx,
                            name = "Tile ${idx + 1}",
                            color = 0xFF424242.toInt(),
                            audioPath = null,
                            volume = 1.0f
                        )
                    )
                }
                repository.replaceTiles(newTilesList)
                currentTiles = newTilesList
                emptyTile = currentTiles.firstOrNull { it.audioPath == null }
            }

            if (emptyTile != null) {
                val finalName = if (!suggestedName.isNullOrBlank()) suggestedName else "AI Sound"
                // Pick a vibrant color
                val vibrantColors = listOf(
                    0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF3F51B5.toInt(),
                    0xFF2196F3.toInt(), 0xFF00BCD4.toInt(), 0xFF4CAF50.toInt(),
                    0xFFFF9800.toInt(), 0xFFFF5722.toInt()
                )
                val assignedColor = vibrantColors[emptyTile.index % vibrantColors.size]
                val updatedTile = emptyTile.copy(
                    audioPath = generatedFile.absolutePath,
                    name = finalName,
                    color = assignedColor
                )
                repository.updateTile(updatedTile)
                _toastMessage.value = "Saved \"$finalName\" to Tile ${emptyTile.index + 1}!"
            }
        }
    }

    private fun suggestNameOrKeep(currentName: String, suggested: String?): Boolean {
        if (suggested.isNullOrBlank()) return false
        // If current name is just a generic "Tile X", replace it with the prompt-based name
        return currentName.startsWith("Tile ") || currentName.isBlank()
    }

    fun importMultipleAudioFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val currentSettings = settings.value
            val currentGridCapacity = currentSettings.rows * currentSettings.cols
            
            // Current visible tiles sorted by index
            var allCurrentTiles = tiles.value.sortedBy { it.index }
            var emptyTiles = allCurrentTiles.filter { it.index < currentGridCapacity && it.audioPath == null }

            // If we have more URIs than empty tiles, and grid can expand, expand the grid
            val neededExtra = uris.size - emptyTiles.size
            if (neededExtra > 0) {
                var newRows = currentSettings.rows
                var newCols = currentSettings.cols
                while ((newRows * newCols - (currentGridCapacity - emptyTiles.size)) < uris.size && (newRows < 8 || newCols < 8)) {
                    if (newRows <= newCols && newRows < 8) {
                        newRows++
                    } else if (newCols < 8) {
                        newCols++
                    } else {
                        break
                    }
                }

                if (newRows != currentSettings.rows || newCols != currentSettings.cols) {
                    val expandedCapacity = newRows * newCols
                    repository.updateSettings(currentSettings.copy(rows = newRows, cols = newCols))
                    // Ensure tiles exist up to expandedCapacity
                    if (allCurrentTiles.size < expandedCapacity) {
                        val additional = (allCurrentTiles.size until expandedCapacity).map { idx ->
                            SoundTile(
                                index = idx,
                                name = "Tile ${idx + 1}",
                                color = 0xFF424242.toInt(),
                                audioPath = null,
                                volume = 1.0f
                            )
                        }
                        repository.updateTiles(additional)
                        allCurrentTiles = (allCurrentTiles + additional).sortedBy { it.index }
                    }
                    emptyTiles = allCurrentTiles.filter { it.index < expandedCapacity && it.audioPath == null }
                }
            }

            if (emptyTiles.isEmpty()) {
                _toastMessage.value = "No empty tiles available for import"
                return@launch
            }

            val tilesToUpdate = mutableListOf<SoundTile>()
            val maxImports = minOf(uris.size, emptyTiles.size)
            var successCount = 0
            
            for (i in 0 until maxImports) {
                val uri = uris[i]
                val tile = emptyTiles[i]
                val file = File(app.filesDir, "bulk_imported_${tile.index}_${System.currentTimeMillis()}_$i.mp3")
                
                try {
                    // Try to get filename for tile name
                    var fileName = "Sound ${tile.index + 1}"
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                val rawName = cursor.getString(nameIndex)
                                if (!rawName.isNullOrBlank()) {
                                    val cleaned = ExportImportUtils.sanitizeFileName(rawName.substringBeforeLast('.'))
                                    if (cleaned.isNotBlank()) {
                                        fileName = cleaned
                                    }
                                }
                            }
                        }
                    }

                    app.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var totalBytes = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                totalBytes += read
                                if (totalBytes > 50 * 1024 * 1024L) { // Max 50 MB
                                    throw SecurityException("Audio file exceeds 50MB limit")
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    // Select a distinct bright color for the newly imported tile
                    val newColor = listOf(
                        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
                        0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF00BCD4.toInt(),
                        0xFF4CAF50.toInt(), 0xFFFFC107.toInt(), 0xFFFF5722.toInt(),
                        0xFF009688.toInt(), 0xFF673AB7.toInt()
                    )[i % 11]

                    tilesToUpdate.add(tile.copy(audioPath = file.absolutePath, name = fileName.take(25), color = newColor))
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (file.exists()) file.delete()
                }
            }
            
            if (tilesToUpdate.isNotEmpty()) {
                repository.updateTiles(tilesToUpdate)
                _toastMessage.value = "Imported $successCount sound${if (successCount > 1) "s" else ""} into grid"
            } else {
                _toastMessage.value = "Failed to import selected sounds"
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
                repository.updateTiles(data.tiles)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
