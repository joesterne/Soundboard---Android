package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GeminiHelper
import kotlinx.coroutines.launch
import java.io.File
import android.media.MediaPlayer

/**
 * Dialog to generate new sounds and music based on a prompt
 * using Google Lyria models (lyria-3-clip-preview for <= 30s clips, lyria-3-pro-preview for full tracks).
 */
@Composable
fun GenerateSoundDialog(
    initialPrompt: String = "",
    targetTileIndex: Int? = null,
    onDismiss: () -> Unit,
    onSoundGenerated: (file: File, suggestedName: String, prompt: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf(initialPrompt) }
    var isShortClip by remember { mutableStateOf(true) } // true: lyria-3-clip-preview (up to 30s), false: lyria-3-pro-preview
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val presetPromptSuggestions = listOf(
        "80s synthwave beat with heavy punchy bass",
        "Dramatic cinematic drum swell and brass sting",
        "Upbeat retro arcade 8-bit game victory fanfare",
        "Lo-fi chill hip-hop guitar chord with vinyl crackle",
        "Cartoon goofy boing sound with spring effect",
        "Heavy metal electric guitar power riff with distortion",
        "Futuristic sci-fi laser blaster beam",
        "Crowd cheering and applause stadium sound"
    )

    fun stopPreview() {
        try {
            previewPlayer?.stop()
            previewPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            previewPlayer = null
            isPreviewPlaying = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!isGenerating) {
                stopPreview()
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (targetTileIndex != null) "Generate Sound for Tile ${targetTileIndex + 1}" else "Generate New Sound",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Describe the sound effect, loop, beat, or musical melody you want to create with AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { 
                        prompt = it
                        errorMessage = null 
                    },
                    label = { Text("Prompt description") },
                    placeholder = { Text("e.g., Upbeat funk bass slap with brass hit") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_sound_prompt_input"),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isGenerating
                )

                // Quick Prompt Idea Chips
                Text(
                    text = "Quick Inspiration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presetPromptSuggestions) { suggestion ->
                        AssistChip(
                            onClick = {
                                prompt = suggestion
                                errorMessage = null
                            },
                            label = { Text(suggestion, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            enabled = !isGenerating
                        )
                    }
                }

                // Model Mode Selection: Short Clip vs Full Track
                Text(
                    text = "Clip Duration / Model",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isShortClip,
                        onClick = { isShortClip = true },
                        label = { Text("Short Clip (≤30s)") },
                        leadingIcon = {
                            if (isShortClip) {
                                Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isGenerating
                    )
                    FilterChip(
                        selected = !isShortClip,
                        onClick = { isShortClip = false },
                        label = { Text("Full Track") },
                        leadingIcon = {
                            if (!isShortClip) {
                                Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isGenerating
                    )
                }

                Text(
                    text = if (isShortClip) {
                        "Powered by lyria-3-clip-preview: Optimized for snappy sound clips, loops, and effects."
                    } else {
                        "Powered by lyria-3-pro-preview: Optimized for longer musical tracks and compositions."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (isGenerating) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                            Text(
                                text = "Synthesizing audio with Lyria AI...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "This may take 10-25 seconds depending on clip complexity.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // If sound was already generated, allow previewing before assigning
                generatedFile?.let { soundFile ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to assign!",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${soundFile.length() / 1024} KB audio generated",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (isPreviewPlaying) {
                                        stopPreview()
                                    } else {
                                        try {
                                            stopPreview()
                                            val player = MediaPlayer().apply {
                                                setDataSource(soundFile.absolutePath)
                                                prepare()
                                                setOnCompletionListener {
                                                    isPreviewPlaying = false
                                                }
                                                start()
                                            }
                                            previewPlayer = player
                                            isPreviewPlaying = true
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            errorMessage = "Preview error: ${e.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("preview_generated_audio_button")
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "Stop" else "Preview",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (generatedFile != null) {
                Button(
                    onClick = {
                        val file = generatedFile!!
                        stopPreview()
                        val suggested = prompt.trim()
                            .take(24)
                            .replace(Regex("[^a-zA-Z0-9 ]"), "")
                            .ifBlank { "AI Sound" }
                        onSoundGenerated(file, suggested, prompt)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("apply_generated_sound_button")
                ) {
                    Text("Apply to Tile")
                }
            } else {
                Button(
                    onClick = {
                        if (prompt.isBlank()) {
                            errorMessage = "Please enter a prompt describing the sound."
                            return@Button
                        }
                        stopPreview()
                        isGenerating = true
                        errorMessage = null
                        generatedFile = null

                        coroutineScope.launch {
                            val soundsDir = File(context.filesDir, "ai_sounds").apply { mkdirs() }
                            val targetFile = File(soundsDir, "ai_${System.currentTimeMillis()}.mp3")
                            val result = GeminiHelper.generateMusic(
                                prompt = prompt.trim(),
                                isShortClip = isShortClip,
                                targetDestinationFile = targetFile
                            )
                            isGenerating = false
                            result.onSuccess { file ->
                                generatedFile = file
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Failed to generate audio from prompt"
                            }
                        }
                    },
                    enabled = prompt.isNotBlank() && !isGenerating,
                    modifier = Modifier.testTag("generate_sound_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                    Text("Generate")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    stopPreview()
                    onDismiss()
                },
                enabled = !isGenerating
            ) {
                Text("Cancel")
            }
        }
    )
}
