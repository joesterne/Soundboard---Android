package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.AppSettings

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Float) -> Unit,
    onBackgroundPhotoSelected: (android.net.Uri?) -> Unit
) {
    var rows by remember { mutableIntStateOf(settings.rows) }
    var cols by remember { mutableIntStateOf(settings.cols) }
    var bgColor by remember { mutableIntStateOf(settings.backgroundColor) }
    var fontSizeSp by remember { mutableFloatStateOf(settings.fontSizeSp) }

    val bgColors = listOf(
        0xFF121212.toInt(), 0xFF1E1E1E.toInt(), 0xFF2C3E50.toInt(),
        0xFF34495E.toInt(), 0xFF5D4037.toInt(), 0xFF455A64.toInt()
    )

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onBackgroundPhotoSelected(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Grid Rows ($rows)")
                    Slider(
                        value = rows.toFloat(),
                        onValueChange = { rows = it.toInt() },
                        valueRange = 2f..8f,
                        steps = 5,
                        modifier = Modifier.width(150.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Grid Cols ($cols)")
                    Slider(
                        value = cols.toFloat(),
                        onValueChange = { cols = it.toInt() },
                        valueRange = 2f..8f,
                        steps = 5,
                        modifier = Modifier.width(150.dp)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Font Size (${fontSizeSp.toInt()}sp)")
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 8f..32f,
                        steps = 23,
                        modifier = Modifier.width(150.dp)
                    )
                }
                
                Text("Background Color")
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    bgColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .clickable { bgColor = c }
                        ) {
                            if (c == bgColor) {
                                Box(modifier = Modifier.align(Alignment.Center).size(16.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
                
                Text("Background Image")
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { 
                        photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Pick Image")
                    }
                    if (settings.backgroundPhotoPath != null) {
                        TextButton(onClick = { onBackgroundPhotoSelected(null) }) {
                            Text("Clear", color = Color.Red)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rows, cols, bgColor, fontSizeSp) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
