package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppSettings
import com.example.data.SoundTile
import com.example.viewmodel.SoundboardViewModel
import android.Manifest
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.graphics.toArgb
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: SoundboardViewModel) {
    val tiles by viewModel.tiles.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentlyEditingTile by viewModel.currentlyEditingTile.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val playHistory by viewModel.playHistory.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showPresetsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    val view = LocalView.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { destUri ->
            // In a real app we'd copy the exported zip from app cache to destUri
            val tempFile = File(context.cacheDir, "export.zip")
            viewModel.exportBoard(tempFile)
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                tempFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importBoard(it) }
    }

    val bulkImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importMultipleAudioFiles(uris)
        }
    }

    var showAddMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Soundboard", fontWeight = FontWeight.Bold) },
                actions = {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Sounds")
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Bulk Import Local Sounds") },
                                onClick = {
                                    showAddMenu = false
                                    bulkImportLauncher.launch("audio/*")
                                },
                                leadingIcon = { Icon(Icons.Filled.LibraryMusic, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Search Web for Sounds") },
                                onClick = {
                                    showAddMenu = false
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pixabay.com/sound-effects/"))
                                    context.startActivity(intent)
                                },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
                            )
                        }
                    }
                    IconButton(onClick = { showPresetsSheet = true }) {
                        Icon(Icons.Filled.CollectionsBookmark, contentDescription = "Preset Library")
                    }
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Filled.History, contentDescription = "Recent Sounds")
                    }
                    IconButton(onClick = {
                        val tempFile = File(context.cacheDir, "share_board.zip")
                        if (viewModel.exportBoard(tempFile)) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Soundboard"))
                        }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    IconButton(onClick = { exportLauncher.launch("soundboard.zip") }) {
                        Icon(Icons.Filled.Download, contentDescription = "Export")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                        Icon(Icons.Filled.Upload, contentDescription = "Import")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(settings.backgroundColor).copy(alpha = 0.5f)
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(Color(settings.backgroundColor))) {
            if (settings.backgroundPhotoPath != null) {
                AsyncImage(
                    model = File(settings.backgroundPhotoPath!!),
                    contentDescription = "Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Setup default if empty
                LaunchedEffect(tiles.size) {
                if (tiles.isEmpty()) {
                    viewModel.updateSettings(4, 4, settings.backgroundColor, settings.fontSizeSp)
                }
            }

            var selectedForSwap by remember { mutableStateOf<Int?>(null) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.cols),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val displayTiles = tiles.filter { it.index < settings.rows * settings.cols }.sortedBy { it.index }
                items(displayTiles) { tile ->
                    TileItem(
                        tile = tile,
                        fontSizeSp = settings.fontSizeSp,
                        isSelectedForSwap = selectedForSwap == tile.index,
                        onTap = {
                            if (selectedForSwap != null) {
                                viewModel.swapTiles(selectedForSwap!!, tile.index)
                                selectedForSwap = null
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.playTile(tile)
                            }
                        },
                        onLongPress = {
                            if (selectedForSwap == null) {
                                viewModel.startEditing(tile)
                            }
                        },
                        onDoubleTap = {
                            if (selectedForSwap == tile.index) {
                                selectedForSwap = null
                            } else {
                                selectedForSwap = tile.index
                            }
                        }
                    )
                }
            }
        }
    }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { r, c, bg, fontSize -> 
                viewModel.updateSettings(r, c, bg, fontSize)
                showSettings = false
            },
            onBackgroundPhotoSelected = { uri ->
                viewModel.updateBackgroundPhoto(uri)
            }
        )
    }

    if (showPresetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPresetsSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.9f)) {
                Text(
                    text = "Preset Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                var newPresetName by remember { mutableStateOf("") }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        label = { Text("New Preset Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newPresetName.isNotBlank()) {
                                viewModel.saveCurrentBoardAsPreset(newPresetName.trim())
                                newPresetName = ""
                            }
                        },
                        enabled = newPresetName.isNotBlank()
                    ) {
                        Text("Save")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                if (presets.isEmpty()) {
                    Text("No presets saved yet.", modifier = Modifier.padding(vertical = 32.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        lazyColumnItems(presets) { preset ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                onClick = {
                                    viewModel.loadPreset(preset)
                                    showPresetsSheet = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", preset.file)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Preset"))
                                    }) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share Preset")
                                    }
                                    IconButton(onClick = { viewModel.deletePreset(preset) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Preset", tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recently Played",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (playHistory.isEmpty()) {
                    Text("No sounds played yet.", modifier = Modifier.padding(vertical = 32.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        lazyColumnItems(playHistory) { tile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        viewModel.playTile(tile)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(tile.color))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = tile.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    currentlyEditingTile?.let { tile ->
        TileEditDialog(
            tile = tile,
            isRecording = isRecording,
            onDismiss = { viewModel.cancelEditing() },
            onSave = { updated -> viewModel.saveTile(updated) },
            onRecordStart = { viewModel.startRecording(tile.index) },
            onRecordStop = { viewModel.stopRecording(tile) },
            onImportAudio = { uri -> viewModel.importAudioFromUri(tile, uri) }
        )
    }
}
