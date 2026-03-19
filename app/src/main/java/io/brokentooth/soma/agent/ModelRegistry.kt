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

// ── OpenRouter models list API types ─────────────────────────────────────────

@Serializable
private data class ORModelsResponse(
    val data: List<ORModelInfo> = emptyList()
)

@Serializable
private data class ORModelInfo(
    val id: String,                                      // "anthropic/claude-sonnet-4"
    val name: String = "",                               // "Claude Sonnet 4"
    @SerialName("context_length") val contextLength: Int = 0,
    val architecture: ORArchitecture? = null
)

@Serializable
private data class ORArchitecture(
    val modality: String = ""                            // "text->text", "text+image->text"
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
        val openRouterDeferred = async { fetchOpenRouterModels(openRouterApiKey) }

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
            fallbackOpenRouterModels()
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

            Log.d(TAG, "Gemini: ${filtered.size} after filtering out embedding/aqa/tts/imagen")

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

    // ── OpenRouter ───────────────────────────────────────────────────────────

    private suspend fun fetchOpenRouterModels(apiKey: String): List<ModelOption> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                Log.w(TAG, "OpenRouter API key is blank, using fallback")
                return@withContext fallbackOpenRouterModels()
            }

            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Log.e(TAG, "OpenRouter models API ${response.code}: $body")
                return@withContext fallbackOpenRouterModels()
            }

            val body = response.body?.string() ?: return@withContext fallbackOpenRouterModels()
            val parsed = json.decodeFromString<ORModelsResponse>(body)

            Log.d(TAG, "OpenRouter API returned ${parsed.data.size} total models")

            // Log modality distribution for debugging
            val modalityCounts = parsed.data.groupBy { it.architecture?.modality ?: "null" }
                .mapValues { it.value.size }
            Log.d(TAG, "OpenRouter modality breakdown: $modalityCounts")

            // Keep any model that can output text — accept null/unknown modality too
            // Only exclude models that explicitly output non-text (audio, image)
            val textModels = parsed.data.filter { model ->
                val modality = model.architecture?.modality ?: ""
                // Exclude only if it explicitly outputs non-text formats
                !modality.endsWith("->audio") && !modality.endsWith("->image")
            }

            Log.d(TAG, "OpenRouter: ${textModels.size} after modality filter (kept null/unknown)")

            val filtered = textModels
                .filter { !it.id.contains(":extended") }

            Log.d(TAG, "OpenRouter: ${filtered.size} after removing :extended variants")

            val result = filtered
                .map { model ->
                    ModelOption(
                        id = model.id,
                        displayName = model.name.ifBlank { model.id },
                        provider = "openrouter"
                    )
                }
                .sortedBy { it.displayName.lowercase() }

            Log.i(TAG, "OpenRouter: returning ${result.size} models")
            // Log first 20 and last 5 to avoid log spam
            result.take(20).forEach { Log.d(TAG, "  OR model: ${it.id}") }
            if (result.size > 20) Log.d(TAG, "  ... and ${result.size - 20} more")
            result
        }

    // ── Fallbacks ────────────────────────────────────────────────────────────

    private fun fallbackGeminiModels() = listOf(
        ModelOption("gemini-2.5-flash-preview-04-17", "Gemini 2.5 Flash Preview", "gemini"),
        ModelOption("gemini-2.5-pro-preview-05-06", "Gemini 2.5 Pro Preview", "gemini"),
        ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", "gemini"),
    )

    private fun fallbackOpenRouterModels() = listOf(
        ModelOption("google/gemini-2.5-flash-preview:free", "Gemini 2.5 Flash (Free)", "openrouter"),
        ModelOption("anthropic/claude-sonnet-4", "Claude Sonnet 4", "openrouter"),
        ModelOption("anthropic/claude-haiku-3.5", "Claude 3.5 Haiku", "openrouter"),
        ModelOption("openai/gpt-4o", "GPT-4o", "openrouter"),
        ModelOption("openai/gpt-4o-mini", "GPT-4o Mini", "openrouter"),
        ModelOption("deepseek/deepseek-r1", "DeepSeek R1", "openrouter"),
        ModelOption("deepseek/deepseek-chat-v3-0324", "DeepSeek V3", "openrouter"),
        ModelOption("google/gemini-2.5-pro-preview", "Gemini 2.5 Pro (OR)", "openrouter"),
        ModelOption("meta-llama/llama-4-maverick", "Llama 4 Maverick", "openrouter"),
    )
}
