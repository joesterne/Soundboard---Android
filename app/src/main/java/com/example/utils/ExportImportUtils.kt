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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@JsonClass(generateAdapter = true)
data class SoundboardExportData(
    val settings: AppSettings,
    val tiles: List<SoundTile>
)

object ExportImportUtils {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SoundboardExportData::class.java)

    fun exportToZip(context: Context, settings: AppSettings, tiles: List<SoundTile>, outputFile: File): Boolean {
        return try {
            val exportData = SoundboardExportData(settings, tiles)
            val json = adapter.toJson(exportData)
            
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                // Add JSON
                val jsonEntry = ZipEntry("board.json")
                zos.putNextEntry(jsonEntry)
                zos.write(json.toByteArray())
                zos.closeEntry()
                
                // Add audio files
                tiles.forEach { tile ->
                    tile.audioPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            val audioEntry = ZipEntry(file.name)
                            zos.putNextEntry(audioEntry)
                            FileInputStream(file).use { fis ->
                                fis.copyTo(zos)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importFromZip(context: Context, uri: Uri, outputDir: File): SoundboardExportData? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                importFromStream(inputStream, outputDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun importFromZip(file: File, outputDir: File): SoundboardExportData? {
        return try {
            FileInputStream(file).use { inputStream ->
                importFromStream(inputStream, outputDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun importFromStream(inputStream: java.io.InputStream, outputDir: File): SoundboardExportData? {
        var json: String? = null
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "board.json") {
                    json = zis.reader().readText()
                } else {
                    val outFile = File(outputDir, entry.name)
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return json?.let { adapter.fromJson(it) }
    }
}
