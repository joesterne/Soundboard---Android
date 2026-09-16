package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.audio.AudioAnalyzer
import com.example.data.SoundTile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
    onImportAudio: (Uri) -> Unit
) {
    var name by remember { mutableStateOf(tile.name) }
    var color by remember { mutableIntStateOf(tile.color) }
    var volume by remember { mutableFloatStateOf(tile.volume) }
    var isLooping by remember { mutableStateOf(tile.isLooping) }
    var isAnalyzing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    var durationMs by remember { mutableLongStateOf(0L) }
    var trimStart by remember { mutableFloatStateOf(tile.trimStartMs?.toFloat() ?: 0f) }
    var trimEnd by remember { mutableFloatStateOf(tile.trimEndMs?.toFloat() ?: 0f) }

    LaunchedEffect(tile.audioPath) {
        tile.audioPath?.let { path ->
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(path)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L
                retriever.release()
                
                if (trimEnd == 0f || trimEnd > durationMs) {
                    trimEnd = durationMs.toFloat()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
        onDismissRequest = { if (!isRecording) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Edit Tile ${tile.index + 1}", modifier = Modifier.weight(1f))
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tile Name") },
                    singleLine = false,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Tile Color")
                // Simple color grid 5x2
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
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { audioPicker.launch("audio/*") }) {
                        Text("Pick Audio")
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
                        )
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
                        Text("Audio file set.", color = Color.Green, style = MaterialTheme.typography.bodySmall)
                        
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Normalize Audio", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    if (durationMs > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Trim Audio: ${String.format("%.1f", trimStart/1000f)}s - ${String.format("%.1f", trimEnd/1000f)}s", style = MaterialTheme.typography.bodySmall)
                        
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 4.dp)) {
                            val barWidth = 4.dp.toPx()
                            val spacing = 2.dp.toPx()
                            val count = (size.width / (barWidth + spacing)).toInt()
                            
                            val random = java.util.Random(tile.audioPath.hashCode().toLong())
                            
                            for(i in 0 until count) {
                                val h = (random.nextFloat() * 0.8f + 0.2f) * size.height
                                val x = i * (barWidth + spacing)
                                val fraction = i.toFloat() / count
                                val barTimeMs = fraction * durationMs
                                
                                val barColor = if (barTimeMs >= trimStart && barTimeMs <= trimEnd) 
                                    Color(0xFF00BCD4) else Color.Gray.copy(alpha = 0.4f)
                                    
                                drawRect(
                                    color = barColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - h)/2),
                                    size = androidx.compose.ui.geometry.Size(barWidth, h)
                                )
                            }
                        }
                        
                        RangeSlider(
                            value = trimStart..trimEnd,
                            onValueChange = { range ->
                                trimStart = range.start
                                trimEnd = range.endInclusive
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Text("Volume: ${(volume * 100).toInt()}%")
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0f..5f,
                    modifier = Modifier.fillMaxWidth()
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
                    val finalTrimStart = if (trimStart > 0f) trimStart.toLong() else null
                    val finalTrimEnd = if (trimEnd > 0f && trimEnd < durationMs) trimEnd.toLong() else null
                    onSave(tile.copy(
                        name = name, 
                        color = color, 
                        volume = volume, 
                        isLooping = isLooping,
                        trimStartMs = finalTrimStart,
                        trimEndMs = finalTrimEnd
                    )) 
                },
                enabled = !isRecording
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRecording
            ) { Text("Cancel") }
        }
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
