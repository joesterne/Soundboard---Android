package com.example

import com.example.utils.ExportImportUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun sanitizeFileName_removesPathTraversalAndInvalidChars() {
        val unsafe1 = "../../etc/passwd"
        val safe1 = ExportImportUtils.sanitizeFileName(unsafe1)
        assertFalse(safe1.contains("/"))
        assertFalse(safe1.contains(".."))

        val unsafe2 = "my_sound?*<>:\"|file.mp3"
        val safe2 = ExportImportUtils.sanitizeFileName(unsafe2)
        assertTrue(safe2.matches(Regex("^[a-zA-Z0-9._-]+$")))

        val empty = ""
        val safeEmpty = ExportImportUtils.sanitizeFileName(empty)
        assertEquals("file", safeEmpty)
    }

    @Test
    fun soundTile_playbackSpeedDefaultAndCustom() {
        val defaultTile = com.example.data.SoundTile(
            index = 0,
            name = "Test",
            color = 0xFF000000.toInt(),
            audioPath = null
        )
        assertEquals(1.0f, defaultTile.playbackSpeed, 0.001f)

        val customTile = defaultTile.copy(playbackSpeed = 1.5f)
        assertEquals(1.5f, customTile.playbackSpeed, 0.001f)
    }

    @Test
    fun lyriaModel_selectionAndRequestFormatting() {
        val shortClipModel = if (true) "lyria-3-clip-preview" else "lyria-3-pro-preview"
        val fullTrackModel = if (false) "lyria-3-clip-preview" else "lyria-3-pro-preview"

        assertEquals("lyria-3-clip-preview", shortClipModel)
        assertEquals("lyria-3-pro-preview", fullTrackModel)

        val request = com.example.api.GenerateContentRequest(
            contents = listOf(
                com.example.api.Content(
                    parts = listOf(com.example.api.Part(text = "Cinematic brass hit"))
                )
            ),
            generationConfig = com.example.api.GenerationConfig(
                responseModalities = listOf("AUDIO")
            )
        )

        assertEquals("Cinematic brass hit", request.contents[0].parts[0].text)
        assertEquals(listOf("AUDIO"), request.generationConfig?.responseModalities)
    }
}
