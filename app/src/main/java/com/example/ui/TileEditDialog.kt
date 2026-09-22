package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.AudioAnalyzer
import com.example.data.SoundTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileEditDialog(
    tile: SoundTile,
    isRecording: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onSave: (SoundTile) -> Unit,
    onToggleFavorite: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onImportAudio: (Uri) -> Unit,
    onGenerateAudio: () -> Unit = {}
) {
    var name by remember { mutableStateOf(tile.name) }
    var color by remember { mutableIntStateOf(tile.color) }
    var volume by remember { mutableFloatStateOf(tile.volume) }
    var isLooping by remember { mutableStateOf(tile.isLooping) }
    var playbackSpeed by remember { mutableFloatStateOf(tile.playbackSpeed) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var durationMs by remember { mutableLongStateOf(0L) }
    var trimStart by remember { mutableFloatStateOf(tile.trimStartMs?.toFloat() ?: 0f) }
    var trimEnd by remember { mutableFloatStateOf(tile.trimEndMs?.toFloat() ?: 0f) }
    var fadeInMs by remember { mutableFloatStateOf(tile.fadeInMs.toFloat()) }
    var fadeOutMs by remember { mutableFloatStateOf(tile.fadeOutMs.toFloat()) }

    var waveformAmplitudes by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isExtractingWaveform by remember { mutableStateOf(false) }

    // Preview playback state
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewCurrentPosMs by remember { mutableFloatStateOf(0f) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        previewPlayer = null
        isPreviewPlaying = false
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    fun startPreview(startPos: Float, endPos: Float) {
        stopPreview()
        val path = tile.audioPath ?: return
        val file = File(path)
        if (!file.exists() || !file.canRead()) return

        try {
            val mp = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                seekTo(startPos.toInt())
                try {
                    val safeSpeed = playbackSpeed.coerceIn(0.5f, 2.0f)
                    playbackParams = PlaybackParams().setSpeed(safeSpeed)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                start()
            }
            previewPlayer = mp
            previewCurrentPosMs = startPos
            isPreviewPlaying = true
        } catch (e: Exception) {
            e.printStackTrace()
            stopPreview()
        }
    }

    // Monitor preview progress and respect trim boundary
    LaunchedEffect(isPreviewPlaying) {
        if (isPreviewPlaying) {
            while (isPreviewPlaying) {
                val mp = previewPlayer
                if (mp != null) {
                    try {
                        if (mp.isPlaying) {
                            val cur = mp.currentPosition.toFloat()
                            previewCurrentPosMs = cur
                            if (cur >= trimEnd || cur >= durationMs) {
                                stopPreview()
                                previewCurrentPosMs = trimStart
                                break
                            }
                        } else {
                            stopPreview()
                            break
                        }
                    } catch (e: Exception) {
                        stopPreview()
                        break
                    }
                } else {
                    break
                }
                delay(40)
            }
        }
    }

    LaunchedEffect(tile.audioPath) {
        stopPreview()
        tile.audioPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(path)
                    val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val loadedDuration = durationStr?.toLongOrNull() ?: 0L
                    durationMs = loadedDuration
                    
                    if (trimEnd == 0f || trimEnd > loadedDuration) {
                        trimEnd = loadedDuration.toFloat()
                    }
                    if (trimStart > trimEnd) {
                        trimStart = 0f
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Extract waveform amplitudes asynchronously
                isExtractingWaveform = true
                coroutineScope.launch {
                    val amps = AudioAnalyzer.extractWaveformAmplitudes(path, samplePoints = 70)
                    waveformAmplitudes = amps
                    isExtractingWaveform = false
                }
            }
        }
    }

    val context = LocalContext.current

    val tileColors = listOf(
        0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
        0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF00BCD4.toInt(),
        0xFF4CAF50.toInt(), 0xFFFFC107.toInt(), 0xFFFF5722.toInt(),
        0xFF424242.toInt()
    )

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportAudio(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onRecordStart()
    }

    AlertDialog(
        onDismissRequest = {
            if (!isRecording) {
                stopPreview()
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Tile ${tile.index + 1}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (tile.audioPath != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                            contentDescription = "Toggle Favorite",
                            tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tile Name") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("tile_name_input")
                )

                Text("Tile Color", style = MaterialTheme.typography.labelLarge)
                // Color grid 5x2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        tileColors.take(5).forEach { c ->
                            ColorDot(c = c, selected = (c == color)) { color = c }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        tileColors.drop(5).forEach { c ->
                            ColorDot(c = c, selected = (c == color)) { color = c }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { audioPicker.launch("audio/*") },
                        modifier = Modifier.weight(1f).testTag("pick_audio_button")
                    ) {
                        Text("Pick Audio")
                    }

                    FilledTonalButton(
                        onClick = onGenerateAudio,
                        modifier = Modifier.weight(1f).testTag("generate_ai_sound_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(end = 4.dp)
                        )
                        Text("AI Sound")
                    }
                    
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                onRecordStop()
                            } else {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    onRecordStart()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("record_audio_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isRecording) "Stop Recording" else "Record Audio",
                            tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                if (tile.audioPath != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio file loaded", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                        
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = {
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        val targetVol = AudioAnalyzer.calculateNormalizationVolume(tile.audioPath)
                                        volume = targetVol.coerceIn(0f, 10f)
                                        isAnalyzing = false
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("normalize_audio_button")
                            ) {
                                Text("Normalize Audio", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    if (durationMs > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Audio Trimming & Waveform Section
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Audio Waveform Trimmer",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    // Reset trim button
                                    if (trimStart > 0f || trimEnd < durationMs) {
                                        TextButton(
                                            onClick = {
                                                trimStart = 0f
                                                trimEnd = durationMs.toFloat()
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("reset_trim_button")
                                        ) {
                                            Icon(
                                                Icons.Filled.RestartAlt,
                                                contentDescription = "Reset Trim",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Drag boundary handles, touch the waveform, or enter exact timestamps below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Interactive Waveform Visualizer
                                InteractiveWaveformVisualizer(
                                    durationMs = durationMs,
                                    trimStartMs = trimStart,
                                    trimEndMs = trimEnd,
                                    currentPlayheadMs = if (isPreviewPlaying) previewCurrentPosMs else null,
                                    waveformAmplitudes = waveformAmplitudes,
                                    isLoading = isExtractingWaveform,
                                    onTrimChange = { newStart, newEnd ->
                                        trimStart = newStart
                                        trimEnd = newEnd
                                    },
                                    onSeek = { seekTimeMs ->
                                        if (isPreviewPlaying) {
                                            startPreview(seekTimeMs, trimEnd)
                                        } else {
                                            previewCurrentPosMs = seekTimeMs
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Precision RangeSlider
                                RangeSlider(
                                    value = trimStart..trimEnd,
                                    onValueChange = { range ->
                                        val minGap = (durationMs * 0.01f).coerceAtLeast(100f)
                                        if (range.endInclusive - range.start >= minGap) {
                                            trimStart = range.start
                                            trimEnd = range.endInclusive
                                        }
                                    },
                                    valueRange = 0f..durationMs.toFloat(),
                                    modifier = Modifier.fillMaxWidth().testTag("trim_range_slider")
                                )

                                // Start & End Timestamp input controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimestampInputField(
                                        label = "Start Timestamp",
                                        timestampMs = trimStart,
                                        maxMs = durationMs.toFloat(),
                                        modifier = Modifier.weight(1f).testTag("trim_start_input"),
                                        onTimestampConfirmed = { newStart ->
                                            val validStart = newStart.coerceIn(0f, (trimEnd - 100f).coerceAtLeast(0f))
                                            trimStart = validStart
                                        }
                                    )

                                    TimestampInputField(
                                        label = "End Timestamp",
                                        timestampMs = trimEnd,
                                        maxMs = durationMs.toFloat(),
                                        modifier = Modifier.weight(1f).testTag("trim_end_input"),
                                        onTimestampConfirmed = { newEnd ->
                                            val validEnd = newEnd.coerceIn((trimStart + 100f).coerceAtMost(durationMs.toFloat()), durationMs.toFloat())
                                            trimEnd = validEnd
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Playback preview button and active duration display
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val trimmedDuration = ((trimEnd - trimStart) / 1000f).coerceAtLeast(0f)
                                    Column {
                                        Text(
                                            "Trimmed Duration: ${String.format(Locale.US, "%.2f", trimmedDuration)}s",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "Original: ${String.format(Locale.US, "%.2f", durationMs / 1000f)}s",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                stopPreview()
                                            } else {
                                                startPreview(trimStart, trimEnd)
                                            }
                                        },
                                        modifier = Modifier.testTag("preview_trimmed_audio_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPreviewPlaying) "Stop Preview" else "Preview Trimmed Section"
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isPreviewPlaying) "Stop" else "Preview Trim")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Fade In: ${String.format(Locale.US, "%.1f", fadeInMs/1000f)}s", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = fadeInMs,
                            onValueChange = { fadeInMs = it },
                            valueRange = 0f..5000f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Text("Fade Out: ${String.format(Locale.US, "%.1f", fadeOutMs/1000f)}s", style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = fadeOutMs,
                            onValueChange = { fadeOutMs = it },
                            valueRange = 0f..5000f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text("Volume: ${(volume * 100).toInt()}%")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..5f,
                    modifier = Modifier.fillMaxWidth().testTag("volume_slider")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Playback Speed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Playback Speed: ${String.format(Locale.US, "%.2f", playbackSpeed)}x",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (playbackSpeed != 1.0f) {
                        TextButton(
                            onClick = {
                                playbackSpeed = 1.0f
                                if (isPreviewPlaying && previewPlayer != null) {
                                    try {
                                        previewPlayer?.playbackParams = PlaybackParams().setSpeed(1.0f)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("reset_speed_button")
                        ) {
                            Text("Reset (1.0x)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Slider(
                    value = playbackSpeed,
                    onValueChange = { newSpeed ->
                        playbackSpeed = newSpeed
                        if (isPreviewPlaying && previewPlayer != null) {
                            try {
                                val safeSpeed = newSpeed.coerceIn(0.5f, 2.0f)
                                previewPlayer?.playbackParams = PlaybackParams().setSpeed(safeSpeed)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    valueRange = 0.5f..2.0f,
                    steps = 14, // 0.1x increments from 0.5 to 2.0
                    modifier = Modifier.fillMaxWidth().testTag("playback_speed_slider")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isLooping,
                        onCheckedChange = { isLooping = it }
                    )
                    Text("Loop Audio", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    stopPreview()
                    val finalTrimStart = if (trimStart > 0f) trimStart.toLong() else null
                    val finalTrimEnd = if (trimEnd > 0f && trimEnd < durationMs) trimEnd.toLong() else null
                    onSave(tile.copy(
                        name = name, 
                        color = color, 
                        volume = volume, 
                        isLooping = isLooping,
                        trimStartMs = finalTrimStart,
                        trimEndMs = finalTrimEnd,
                        fadeInMs = fadeInMs.toLong(),
                        fadeOutMs = fadeOutMs.toLong(),
                        playbackSpeed = playbackSpeed
                    )) 
                },
                enabled = !isRecording,
                modifier = Modifier.testTag("save_tile_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onDismiss()
                },
                enabled = !isRecording
            ) { Text("Cancel") }
        }
    )
}

/**
 * Custom Interactive Waveform Visualizer Composable supporting:
 * - Real/Synthesized waveform bars
 * - Distinct active trim range vs unselected dimmed sections
 * - Visual start & end draggable anchor handles
 * - Optional playhead indicator during preview
 * - Direct tap to set start/end points
 */
@Composable
fun InteractiveWaveformVisualizer(
    durationMs: Long,
    trimStartMs: Float,
    trimEndMs: Float,
    currentPlayheadMs: Float?,
    waveformAmplitudes: List<Float>,
    isLoading: Boolean,
    onTrimChange: (newStart: Float, newEnd: Float) -> Unit,
    onSeek: (seekTimeMs: Float) -> Unit
) {
    var componentWidthPx by remember { mutableFloatStateOf(1f) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val activeWaveformColor = Color(0xFF00BCD4)
    val inactiveWaveformColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val handleColor = MaterialTheme.colorScheme.primary

    // Track which handle is currently dragged: null, "START", or "END"
    var activeDraggingHandle by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .onGloballyPositioned { coordinates ->
                componentWidthPx = coordinates.size.width.toFloat().coerceAtLeast(1f)
            }
            .pointerInput(durationMs, trimStartMs, trimEndMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0 && componentWidthPx > 0) {
                        val tappedFraction = (offset.x / componentWidthPx).coerceIn(0f, 1f)
                        val tappedTimeMs = tappedFraction * durationMs
                        
                        val distToStart = Math.abs(tappedTimeMs - trimStartMs)
                        val distToEnd = Math.abs(tappedTimeMs - trimEndMs)

                        // If tap is close to start handle or before it, update start
                        if (tappedTimeMs < trimStartMs || distToStart < distToEnd) {
                            val newStart = tappedTimeMs.coerceAtMost(trimEndMs - 100f)
                            onTrimChange(newStart, trimEndMs)
                        } else {
                            val newEnd = tappedTimeMs.coerceAtLeast(trimStartMs + 100f)
                            onTrimChange(trimStartMs, newEnd)
                        }
                        onSeek(tappedTimeMs)
                    }
                }
            }
            .pointerInput(durationMs, trimStartMs, trimEndMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0 && componentWidthPx > 0) {
                            val startX = (trimStartMs / durationMs) * componentWidthPx
                            val endX = (trimEndMs / durationMs) * componentWidthPx
                            val touchRadiusPx = 36.dp.toPx()

                            activeDraggingHandle = when {
                                Math.abs(offset.x - startX) <= touchRadiusPx -> "START"
                                Math.abs(offset.x - endX) <= touchRadiusPx -> "END"
                                offset.x < (startX + endX) / 2 -> "START"
                                else -> "END"
                            }
                        }
                    },
                    onDragEnd = {
                        activeDraggingHandle = null
                    },
                    onDragCancel = {
                        activeDraggingHandle = null
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (durationMs > 0 && componentWidthPx > 0) {
                            val deltaFraction = dragAmount.x / componentWidthPx
                            val deltaTimeMs = deltaFraction * durationMs

                            if (activeDraggingHandle == "START") {
                                val updatedStart = (trimStartMs + deltaTimeMs).coerceIn(0f, trimEndMs - 100f)
                                onTrimChange(updatedStart, trimEndMs)
                            } else if (activeDraggingHandle == "END") {
                                val updatedEnd = (trimEndMs + deltaTimeMs).coerceIn(trimStartMs + 100f, durationMs.toFloat())
                                onTrimChange(trimStartMs, updatedEnd)
                            }
                        }
                    }
                )
            }
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        // Waveform & Boundary Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val barWidth = 3.dp.toPx()
            val barSpacing = 2.dp.toPx()
            val totalBars = (width / (barWidth + barSpacing)).toInt().coerceAtLeast(1)

            val startX = if (durationMs > 0) (trimStartMs / durationMs) * width else 0f
            val endX = if (durationMs > 0) (trimEndMs / durationMs) * width else width

            // 1. Draw dimmed unselected region overlays
            if (startX > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(0f, 0f),
                    size = Size(startX, height)
                )
            }
            if (endX < width) {
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(endX, 0f),
                    size = Size(width - endX, height)
                )
            }

            // 2. Draw Waveform bars
            for (i in 0 until totalBars) {
                val x = i * (barWidth + barSpacing)
                val fraction = i.toFloat() / totalBars
                val barTimeMs = fraction * durationMs

                // Resolve amplitude from extracted or synthesized list
                val amp = if (waveformAmplitudes.isNotEmpty()) {
                    val ampIdx = (fraction * waveformAmplitudes.size).toInt().coerceIn(0, waveformAmplitudes.size - 1)
                    waveformAmplitudes[ampIdx]
                } else {
                    // Fallback visual aesthetic variation
                    0.2f + 0.6f * Math.abs(Math.sin(i * 0.25)).toFloat()
                }

                val barHeight = (amp * (height - 16.dp.toPx())).coerceAtLeast(4.dp.toPx())
                val isInsideTrim = barTimeMs >= trimStartMs && barTimeMs <= trimEndMs

                drawRect(
                    color = if (isInsideTrim) activeWaveformColor else inactiveWaveformColor,
                    topLeft = Offset(x, (height - barHeight) / 2f),
                    size = Size(barWidth, barHeight)
                )
            }

            // 3. Draw Trim Boundary Lines and Handles
            // Start line
            drawLine(
                color = handleColor,
                start = Offset(startX, 0f),
                end = Offset(startX, height),
                strokeWidth = 3.dp.toPx()
            )
            // End line
            drawLine(
                color = handleColor,
                start = Offset(endX, 0f),
                end = Offset(endX, height),
                strokeWidth = 3.dp.toPx()
            )

            // Top and Bottom enclosing markers
            drawRect(
                color = handleColor.copy(alpha = 0.35f),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, 3.dp.toPx())
            )
            drawRect(
                color = handleColor.copy(alpha = 0.35f),
                topLeft = Offset(startX, height - 3.dp.toPx()),
                size = Size(endX - startX, 3.dp.toPx())
            )

            // 4. Draw Playhead if currently playing
            if (currentPlayheadMs != null && durationMs > 0) {
                val playheadX = (currentPlayheadMs / durationMs) * width
                drawLine(
                    color = Color(0xFFFF5252),
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // Overlay Interactive Draggable Handles (HTML/Audio editor style)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val startXDp = with(density) {
            if (durationMs > 0) ((trimStartMs / durationMs) * componentWidthPx).toDp() else 0.dp
        }
        val endXDp = with(density) {
            if (durationMs > 0) ((trimEndMs / durationMs) * componentWidthPx).toDp() else 0.dp
        }

        // Start Handle Badge
        Box(
            modifier = Modifier
                .offset(x = startXDp - 12.dp, y = 4.dp)
                .size(24.dp, 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(handleColor)
                .testTag("waveform_start_handle"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "S",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // End Handle Badge
        Box(
            modifier = Modifier
                .offset(x = endXDp - 12.dp, y = 60.dp)
                .size(24.dp, 20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(handleColor)
                .testTag("waveform_end_handle"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "E",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Text field for entering or editing exact timestamp in seconds (e.g. "1.25" or "0:04.5")
 */
@Composable
fun TimestampInputField(
    label: String,
    timestampMs: Float,
    maxMs: Float,
    modifier: Modifier = Modifier,
    onTimestampConfirmed: (Float) -> Unit
) {
    var textValue by remember(timestampMs) {
        mutableStateOf(String.format(Locale.US, "%.2f", timestampMs / 1000f))
    }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            textValue = input
            // Instant update if valid number
            val parsedSeconds = input.toFloatOrNull()
            if (parsedSeconds != null && parsedSeconds >= 0f) {
                onTimestampConfirmed(parsedSeconds * 1000f)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        suffix = { Text("s", style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                val parsed = textValue.toFloatOrNull()
                if (parsed != null) {
                    onTimestampConfirmed(parsed * 1000f)
                } else {
                    textValue = String.format(Locale.US, "%.2f", timestampMs / 1000f)
                }
                focusManager.clearFocus()
            }
        ),
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        ),
        modifier = modifier
    )
}

@Composable
fun ColorDot(c: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(c))
            .clickable(onClick = onClick)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

