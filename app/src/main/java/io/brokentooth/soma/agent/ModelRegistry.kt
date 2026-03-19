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
            if (apiKey.isBlank()) return@withContext fallbackGeminiModels()

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

            parsed.models
                .filter { "generateContent" in it.supportedGenerationMethods }
                // Skip embedding, AQA, and TTS models
                .filter { !it.name.contains("embedding") }
                .filter { !it.name.contains("aqa") }
                .filter { !it.name.contains("tts") }
                .filter { !it.name.contains("imagen") }
                .map { model ->
                    // name is "models/gemini-2.0-flash" → extract "gemini-2.0-flash"
                    val modelId = model.name.removePrefix("models/")
                    ModelOption(
                        id = modelId,
                        displayName = model.displayName.ifBlank { modelId },
                        provider = "gemini"
                    )
                }
                // Deduplicate: keep the shorter/cleaner variant when there are aliases
                .distinctBy { it.displayName }
                .sortedBy { it.displayName }
        }

    // ── OpenRouter ───────────────────────────────────────────────────────────

    private suspend fun fetchOpenRouterModels(apiKey: String): List<ModelOption> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext fallbackOpenRouterModels()

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

            parsed.data
                .filter { model ->
                    val modality = model.architecture?.modality ?: ""
                    // Keep models that output text (chat models)
                    // text->text, text+image->text, etc.
                    modality.endsWith("->text") && model.contextLength > 0
                }
                // Filter out extended-thinking variants (not useful for chat)
                .filter { !it.id.contains(":extended") }
                .map { model ->
                    ModelOption(
                        id = model.id,
                        displayName = model.name.ifBlank { model.id },
                        provider = "openrouter"
                    )
                }
                .sortedBy { it.displayName.lowercase() }
        }

    // ── Fallbacks ────────────────────────────────────────────────────────────

    private fun fallbackGeminiModels() = listOf(
        ModelOption("gemini-2.0-flash", "Gemini 2.0 Flash", "gemini"),
        ModelOption("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", "gemini"),
        ModelOption("gemini-1.5-pro", "Gemini 1.5 Pro", "gemini"),
    )

    private fun fallbackOpenRouterModels() = listOf(
        ModelOption("anthropic/claude-sonnet-4", "Claude Sonnet 4", "openrouter"),
        ModelOption("anthropic/claude-haiku", "Claude Haiku", "openrouter"),
        ModelOption("openai/gpt-4o", "GPT-4o", "openrouter"),
        ModelOption("openai/gpt-4o-mini", "GPT-4o Mini", "openrouter"),
        ModelOption("deepseek/deepseek-r1", "DeepSeek R1", "openrouter"),
        ModelOption("deepseek/deepseek-chat-v3-0324", "DeepSeek V3", "openrouter"),
        ModelOption("google/gemini-2.5-pro-preview", "Gemini 2.5 Pro (OR)", "openrouter"),
        ModelOption("meta-llama/llama-4-maverick", "Llama 4 Maverick", "openrouter"),
    )
}
