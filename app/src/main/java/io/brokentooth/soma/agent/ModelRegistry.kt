package io.brokentooth.soma.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "ModelRegistry"

// ── Gemini models list API types ─────────────────────────────────────────────

@Serializable
private data class GeminiModelsResponse(
    val models: List<GeminiModelInfo> = emptyList()
)

@Serializable
private data class GeminiModelInfo(
    val name: String,                                    // "models/gemini-2.0-flash"
    val displayName: String = "",                        // "Gemini 2.0 Flash"
    val supportedGenerationMethods: List<String> = emptyList()
)

// ── Registry ─────────────────────────────────────────────────────────────────

object ModelRegistry {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Fetches available models from both Gemini and OpenRouter in parallel.
     * Returns a combined list sorted by provider then name.
     * Falls back to a minimal hardcoded list on failure.
     */
    suspend fun fetchAvailableModels(
        geminiApiKey: String,
        openRouterApiKey: String
    ): List<ModelOption> = coroutineScope {
        val geminiDeferred = async { fetchGeminiModels(geminiApiKey) }
        val openRouterDeferred = async { OpenRouterModels.fetchModels(openRouterApiKey, http) }

        val geminiModels = try {
            geminiDeferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Gemini models", e)
            fallbackGeminiModels()
        }

        val openRouterModels = try {
            openRouterDeferred.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch OpenRouter models", e)
            OpenRouterModels.getFallbackModels()
        }

        geminiModels + openRouterModels
    }

    // ── Gemini ───────────────────────────────────────────────────────────────

    private suspend fun fetchGeminiModels(apiKey: String): List<ModelOption> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                Log.w(TAG, "Gemini API key is blank, using fallback")
                return@withContext fallbackGeminiModels()
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get()
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Log.e(TAG, "Gemini models API ${response.code}: $body")
                return@withContext fallbackGeminiModels()
            }

            val body = response.body?.string() ?: return@withContext fallbackGeminiModels()
            val parsed = json.decodeFromString<GeminiModelsResponse>(body)

            Log.d(TAG, "Gemini API returned ${parsed.models.size} total models")

            // Log all raw model names for debugging
            parsed.models.forEach { model ->
                Log.d(TAG, "  Gemini raw: ${model.name} methods=${model.supportedGenerationMethods}")
            }

            val chatModels = parsed.models
                .filter { "generateContent" in it.supportedGenerationMethods }

            Log.d(TAG, "Gemini: ${chatModels.size} support generateContent")

            val filtered = chatModels
                .filter { !it.name.contains("embedding") }
                .filter { !it.name.contains("aqa") }
                .filter { !it.name.contains("tts") }
                .filter { !it.name.contains("imagen") }
                // Remove image-generation models (e.g. gemini-2.5-flash-image, gemini-3-pro-image-preview)
                // These are not chat models — keep multimodal chat models which don't have "-image" suffix
                .filter { !it.name.contains("-image") }
                // Remove domain-specific non-chat models
                .filter { !it.name.contains("robotics") }
                .filter { !it.name.contains("computer-use") }

            Log.d(TAG, "Gemini: ${filtered.size} after filtering non-chat models")

            val result = filtered
                .map { model ->
                    val modelId = model.name.removePrefix("models/")
                    ModelOption(
                        id = modelId,
                        displayName = model.displayName.ifBlank { modelId },
                        provider = "gemini"
                    )
                }
                .distinctBy { it.displayName }
                .sortedBy { it.displayName }

            Log.i(TAG, "Gemini: returning ${result.size} models")
            result.forEach { Log.d(TAG, "  Gemini model: ${it.id} → ${it.displayName}") }
            result
        }


    private fun fallbackGeminiModels() = listOf(
        ModelOption("gemini-2.5-flash-preview-04-17", "[Goo] Gemini 2.5 Flash Preview", "gemini"),
        ModelOption("gemini-2.5-pro-preview-05-06", "[Goo] Gemini 2.5 Pro Preview", "gemini"),
        ModelOption("gemini-1.5-pro", "[Goo] Gemini 1.5 Pro", "gemini"),
    )
}
