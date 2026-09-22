package com.example.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import com.example.BuildConfig
import android.util.Base64
import java.io.File
import java.io.FileOutputStream

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val responseModalities: List<String>? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val error: ApiError? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

@Serializable
data class ApiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateWithModel(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { 
            ignoreUnknownKeys = true 
            isLenient = true
            encodeDefaults = true
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiHelper {
    suspend fun searchWeb(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val request = GenerateContentRequest(
            contents = listOf(Content(
                parts = listOf(Part(text = "Find royalty-free sound effects or audio clips for: $prompt. Provide links where they can be downloaded."))
            )),
            tools = listOf(buildJsonObject { putJsonObject("googleSearch") {} })
        )
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response text"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Generates a new sound or music clip using Google DeepMind's Lyria models.
     * Uses 'lyria-3-clip-preview' for short clips (up to 30s) or 'lyria-3-pro-preview' for full-length tracks.
     * Saves the resulting audio to targetDestinationFile.
     */
    suspend fun generateMusic(
        prompt: String,
        isShortClip: Boolean,
        targetDestinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalStateException("Gemini API key is not configured in Secrets panel."))
        }

        val modelName = if (isShortClip) "lyria-3-clip-preview" else "lyria-3-pro-preview"
        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseModalities = listOf("AUDIO")
            )
        )

        try {
            val response = RetrofitClient.service.generateWithModel(modelName, apiKey, request)
            if (response.error != null) {
                return@withContext Result.failure(Exception(response.error.message ?: "Unknown API error (${response.error.code})"))
            }

            val parts = response.candidates?.firstOrNull()?.content?.parts
            val inlineAudio = parts?.firstOrNull { it.inlineData != null }?.inlineData
                ?: return@withContext Result.failure(Exception("No audio data returned by $modelName. Candidate parts had no inline audio."))

            val audioBytes = Base64.decode(inlineAudio.data, Base64.DEFAULT)
            FileOutputStream(targetDestinationFile).use { fos ->
                fos.write(audioBytes)
                fos.flush()
            }

            Result.success(targetDestinationFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

