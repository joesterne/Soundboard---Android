package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.AppSettings
import com.example.data.SoundTile
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@JsonClass(generateAdapter = true)
data class SoundboardExportData(
    val settings: AppSettings,
    val tiles: List<SoundTile>
)

object ExportImportUtils {
    private const val MAX_ZIP_ENTRIES = 250
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 200 * 1024 * 1024L // 200 MB
    private const val MAX_ENTRY_UNCOMPRESSED_BYTES = 50 * 1024 * 1024L  // 50 MB
    private const val MAX_JSON_BYTES = 5 * 1024 * 1024L                // 5 MB

    private val SAFE_FILENAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SoundboardExportData::class.java)

    fun exportToZip(context: Context, settings: AppSettings, tiles: List<SoundTile>, outputFile: File): Boolean {
        return try {
            val safeTiles = tiles.map { tile ->
                // Clean tile audio path references to relative safe filenames
                if (tile.audioPath != null) {
                    val file = File(tile.audioPath)
                    if (file.exists() && isFileWithinAppStorage(context, file)) {
                        tile.copy(audioPath = sanitizeFileName(file.name))
                    } else {
                        tile.copy(audioPath = null)
                    }
                } else {
                    tile
                }
            }

            val exportData = SoundboardExportData(
                settings = settings.copy(backgroundPhotoPath = null), // Do not export local device-specific background image paths
                tiles = safeTiles
            )
            val json = adapter.toJson(exportData)

            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                // Add JSON
                val jsonEntry = ZipEntry("board.json")
                zos.putNextEntry(jsonEntry)
                zos.write(json.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Add audio files (only from app's internal storage)
                val exportedNames = mutableSetOf<String>()
                tiles.forEach { tile ->
                    tile.audioPath?.let { path ->
                        val file = File(path)
                        if (file.exists() && file.isFile && isFileWithinAppStorage(context, file)) {
                            val safeName = sanitizeFileName(file.name)
                            if (exportedNames.add(safeName)) {
                                val audioEntry = ZipEntry(safeName)
                                zos.putNextEntry(audioEntry)
                                FileInputStream(file).use { fis ->
                                    fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    fun importFromZip(context: Context, uri: Uri, outputDir: File): SoundboardExportData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                importFromStream(context, inputStream, outputDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun importFromZip(context: Context, file: File, outputDir: File): SoundboardExportData? {
        return try {
            if (!file.exists() || !file.isFile) return null
            FileInputStream(file).use { inputStream ->
                importFromStream(context, inputStream, outputDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun importFromStream(context: Context, inputStream: InputStream, outputDir: File): SoundboardExportData? {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val canonicalOutputDir = outputDir.canonicalPath
        val extractedFiles = mutableListOf<File>()
        val extractedAudioFileNames = mutableSetOf<String>()
        var jsonContent: String? = null

        var totalEntries = 0
        var totalBytesExtracted = 0L

        try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ZIP_ENTRIES) {
                        throw SecurityException("Exceeded maximum zip entries limit ($MAX_ZIP_ENTRIES)")
                    }

                    // Security: Ignore directories and validate entry name
                    if (!entry.isDirectory) {
                        val rawName = File(entry.name).name
                        val safeName = sanitizeFileName(rawName)

                        if (!SAFE_FILENAME_REGEX.matches(safeName)) {
                            throw SecurityException("Invalid characters in zip entry name: ${entry.name}")
                        }

                        if (safeName == "board.json") {
                            // Read JSON with size guard
                            val buffer = ByteArray(8192)
                            val byteOut = java.io.ByteArrayOutputStream()
                            var entryBytes = 0L
                            var read: Int
                            while (zis.read(buffer).also { read = it } != -1) {
                                entryBytes += read
                                totalBytesExtracted += read
                                if (entryBytes > MAX_JSON_BYTES || totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    throw SecurityException("JSON entry or total zip uncompressed size exceeded limit")
                                }
                                byteOut.write(buffer, 0, read)
                            }
                            jsonContent = byteOut.toString(Charsets.UTF_8.name())
                        } else {
                            // Extract audio file safely
                            val outFile = File(outputDir, safeName).canonicalFile
                            // Zip Slip protection: ensure output file is strictly within destination directory
                            if (!outFile.path.startsWith(canonicalOutputDir + File.separator)) {
                                throw SecurityException("Zip entry is outside of target directory: ${entry.name}")
                            }

                            var entryBytes = 0L
                            val buffer = ByteArray(8192)
                            FileOutputStream(outFile).use { fos ->
                                var read: Int
                                while (zis.read(buffer).also { read = it } != -1) {
                                    entryBytes += read
                                    totalBytesExtracted += read
                                    if (entryBytes > MAX_ENTRY_UNCOMPRESSED_BYTES || totalBytesExtracted > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                        throw SecurityException("Extracted file size exceeded allowed security limit")
                                    }
                                    fos.write(buffer, 0, read)
                                }
                            }
                            extractedFiles.add(outFile)
                            extractedAudioFileNames.add(safeName)
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (jsonContent == null) {
                cleanupFiles(extractedFiles)
                return null
            }

            val rawData = adapter.fromJson(jsonContent) ?: run {
                cleanupFiles(extractedFiles)
                return null
            }

            // Sanitize settings and ensure security bounds
            val sanitizedSettings = rawData.settings.copy(
                rows = rawData.settings.rows.coerceIn(2, 8),
                cols = rawData.settings.cols.coerceIn(2, 8),
                fontSizeSp = rawData.settings.fontSizeSp.coerceIn(8f, 32f),
                masterVolume = rawData.settings.masterVolume.coerceIn(0f, 1f),
                backgroundPhotoPath = null // Clear external/unverified image path for security
            )

            // Sanitize tiles: only link to audio files that were actually extracted in this operation
            val sanitizedTiles = rawData.tiles.map { tile ->
                val verifiedAudioPath = if (tile.audioPath != null) {
                    val audioFileName = sanitizeFileName(File(tile.audioPath).name)
                    if (extractedAudioFileNames.contains(audioFileName)) {
                        val verifiedFile = File(outputDir, audioFileName)
                        if (verifiedFile.exists()) verifiedFile.absolutePath else null
                    } else {
                        null
                    }
                } else {
                    null
                }

                tile.copy(
                    index = tile.index.coerceIn(0, 100),
                    volume = tile.volume.coerceIn(0f, 5f),
                    fadeInMs = tile.fadeInMs.coerceIn(0L, 30000L),
                    fadeOutMs = tile.fadeOutMs.coerceIn(0L, 30000L),
                    trimStartMs = tile.trimStartMs?.coerceAtLeast(0L),
                    trimEndMs = tile.trimEndMs?.coerceAtLeast(0L),
                    audioPath = verifiedAudioPath
                )
            }

            return SoundboardExportData(
                settings = sanitizedSettings,
                tiles = sanitizedTiles
            )
        } catch (e: Exception) {
            e.printStackTrace()
            cleanupFiles(extractedFiles)
            return null
        }
    }

    private fun cleanupFiles(files: List<File>) {
        files.forEach { file ->
            try {
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sanitizeFileName(name: String): String {
        val baseName = File(name).name
        val withoutTraversal = baseName.replace("..", "_")
        return withoutTraversal.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100).ifEmpty { "file" }
    }

    fun isFileWithinAppStorage(context: Context, file: File): Boolean {
        return try {
            val canonicalPath = file.canonicalPath
            val filesDirPath = context.filesDir.canonicalPath
            val cacheDirPath = context.cacheDir.canonicalPath
            canonicalPath.startsWith(filesDirPath + File.separator) ||
                    canonicalPath.startsWith(cacheDirPath + File.separator)
        } catch (e: Exception) {
            false
        }
    }
}

