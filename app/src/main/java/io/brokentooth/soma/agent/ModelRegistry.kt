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

            val filtered = chatModels
                .filter { !it.name.contains("embedding") }
                .filter { !it.name.contains("aqa") }
                .filter { !it.name.contains("tts") }
                .filter { !it.name.contains("imagen") }
                .filter { !it.name.contains("-image") }
                .filter { !it.name.contains("robotics") }
                .filter { !it.name.contains("computer-use") }
                // Only keep Gemini 3.x series — old 1.5/2.0/2.5 culled
                .filter { model ->
                    val id = model.name.removePrefix("models/")
                    id.startsWith("gemini-3.") || id.startsWith("gemini-3-")
                }

            Log.d(TAG, "Gemini: ${filtered.size} after filtering non-chat + old-gen")

            val result = filtered
                .map { model ->
                    val modelId = model.name.removePrefix("models/")
                    ModelOption(
                        id = modelId,
                        displayName = "[Goo] ${model.displayName.ifBlank { modelId }} (Direct)",
                        provider = "gemini",
                        isFree = false
                    )
                }
                .distinctBy { it.displayName }
                .sortedBy { it.displayName }

            Log.i(TAG, "Gemini: returning ${result.size} models")
            result.forEach { Log.d(TAG, "  Gemini model: ${it.id} → ${it.displayName}") }
            result
        }


    private fun fallbackGeminiModels() = listOf(
        ModelOption("gemini-3.1-flash-lite-preview", "[Goo] Gemini 3.1 Flash Lite (Direct)", "gemini", false),
        ModelOption("gemini-3.1-flash-preview", "[Goo] Gemini 3.1 Flash (Direct)", "gemini", false),
        ModelOption("gemini-3.1-pro-preview", "[Goo] Gemini 3.1 Pro (Direct)", "gemini", false),
    )
}
