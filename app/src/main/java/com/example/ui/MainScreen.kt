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
import androidx.compose.material.icons.automirrored.filled.Sort
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
import kotlinx.coroutines.launch

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems

import android.view.HapticFeedbackConstants
import android.widget.Toast
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
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showPresetsSheet by remember { mutableStateOf(false) }
    var showWebSearch by remember { mutableStateOf(false) }
    var showFavoritesSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val view = LocalView.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { destUri ->
            val exportDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val tempFile = File(exportDir, "export_${System.currentTimeMillis()}.zip")
            if (viewModel.exportBoard(tempFile)) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (tempFile.exists()) tempFile.delete()
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Text(
                    text = "Soundboard Menu",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                
                NavigationDrawerItem(
                    label = { Text("Bulk Import Local Sounds") },
                    icon = { Icon(Icons.Filled.LibraryMusic, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        bulkImportLauncher.launch("audio/*")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Search Web for Sounds") },
                    icon = { Icon(Icons.Filled.Search, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showWebSearch = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Preset Library") },
                    icon = { Icon(Icons.Filled.CollectionsBookmark, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showPresetsSheet = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Recent Sounds") },
                    icon = { Icon(Icons.Filled.History, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showHistorySheet = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Favorites") },
                    icon = { Icon(Icons.Filled.Star, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showFavoritesSheet = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Share Soundboard") },
                    icon = { Icon(Icons.Filled.Share, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
                        sharedDir.listFiles()?.forEach { it.delete() }
                        val tempFile = File(sharedDir, "soundboard_share.zip")
                        if (viewModel.exportBoard(tempFile)) {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", tempFile)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Soundboard"))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Export Board") },
                    icon = { Icon(Icons.Filled.Download, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        exportLauncher.launch("soundboard.zip")
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Import Board") },
                    icon = { Icon(Icons.Filled.Upload, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        importLauncher.launch(arrayOf("application/zip"))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showSettings = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Soundboard", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { bulkImportLauncher.launch("audio/*") }
                        ) {
                            Icon(Icons.Filled.LibraryAdd, contentDescription = "Batch Import Sounds")
                        }
                        IconButton(onClick = { viewModel.autoArrangeTiles() }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Auto-Arrange")
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
                    viewModel.updateSettings(4, 4, settings.backgroundColor, settings.fontSizeSp, settings.masterVolume)
                }
            }

            var selectedForSwap by remember { mutableStateOf<Int?>(null) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.cols),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val displayTiles = tiles.filter { it.index < settings.rows * settings.cols }.sortedBy { it.index }
                items(
                    items = displayTiles,
                    key = { it.index }
                ) { tile ->
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
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { r, c, bg, fontSize, masterVol -> 
                viewModel.updateSettings(r, c, bg, fontSize, masterVol)
                showSettings = false
            },
            onBackgroundPhotoSelected = { uri ->
                viewModel.updateBackgroundPhoto(uri)
            }
        )
    }

    if (showWebSearch) {
        WebSearchDialog(
            onDismiss = { showWebSearch = false }
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
                        lazyColumnItems(
                            items = presets,
                            key = { it.id }
                        ) { preset ->
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
                        lazyColumnItems(
                            items = playHistory,
                            key = { it.index }
                        ) { tile ->
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

    if (showFavoritesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFavoritesSheet = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxHeight(0.9f)) {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (favorites.isEmpty()) {
                    Text("No pinned sounds yet. Edit a tile to favorite it.", modifier = Modifier.padding(vertical = 32.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        lazyColumnItems(
                            items = favorites,
                            key = { it.id }
                        ) { fav ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        viewModel.playFavorite(fav)
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(fav.color))
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = fav.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.removeFavorite(fav) }) {
                                    Icon(Icons.Filled.Star, contentDescription = "Unfavorite", tint = Color(0xFFFFC107))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    currentlyEditingTile?.let { tile ->
        val isFavorite = favorites.any { it.audioPath == tile.audioPath && it.audioPath != null }
        TileEditDialog(
            tile = tile,
            isRecording = isRecording,
            isFavorite = isFavorite,
            onDismiss = { viewModel.cancelEditing() },
            onSave = { updated -> viewModel.saveTile(updated) },
            onToggleFavorite = { viewModel.toggleFavorite(tile) },
            onRecordStart = { viewModel.startRecording(tile.index) },
            onRecordStop = { viewModel.stopRecording(tile) },
            onImportAudio = { uri -> viewModel.importAudioFromUri(tile, uri) }
        )
    }
}
