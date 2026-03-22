package io.brokentooth.soma.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ── OpenRouter Wire types ────────────────────────────────────────────────────

@Serializable
private data class ORMessage(val role: String, val content: String)

@Serializable
private data class ORRequest(
    val model: String,
    val stream: Boolean,
    val messages: List<ORMessage>
)

@Serializable
private data class ORStreamChunk(
    val choices: List<ORChoice> = emptyList()
)

@Serializable
private data class ORChoice(
    val delta: ORDelta? = null
)

@Serializable
private data class ORDelta(
    val content: String? = null
)

// ── Client ───────────────────────────────────────────────────────────────────

/**
 * OpenRouter API client (OpenAI-compatible).
 * Actually streams via SSE, parsing tokens line-by-line as they arrive.
 */
class OpenRouterClient(
    private val apiKey: String,
    private val modelId: String
) : LlmProvider {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        // Prepend system prompt, then convert ChatMessage to OpenRouter format
        val orMessages = buildList {
            add(ORMessage("system", SYSTEM_PROMPT))
            addAll(messages.map { ORMessage(it.role, it.content) })
        }

        val body = json.encodeToString(
            ORRequest(
                model = modelId,
                stream = true,
                messages = orMessages
            )
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = response.body?.string() ?: "Unknown error"
            response.close()
            throw Exception("OpenRouter API Error: ${response.code} $error")
        }

        val source = response.body?.source()
            ?: throw Exception("Empty response body from OpenRouter")

        response.use {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue

                try {
                    val chunk = json.decodeFromString<ORStreamChunk>(data)
                    val token = chunk.choices.firstOrNull()?.delta?.content
                    if (!token.isNullOrEmpty()) {
                        emit(token)
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val SYSTEM_PROMPT = "You are SOMA, a helpful AI assistant running on an Android phone. " +
            "You can use tools to interact with the device when asked."
    }
}
