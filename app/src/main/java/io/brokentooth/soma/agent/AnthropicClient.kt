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

// ── Wire types ─────────────────────────────────────────────────────────────

@Serializable
data class ApiMessage(val role: String, val content: String)

@Serializable
private data class ApiRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean,
    val messages: List<ApiMessage>
)

@Serializable
private data class StreamEnvelope(val type: String)

@Serializable
private data class ContentBlockDeltaEvent(
    val type: String,
    val index: Int,
    val delta: TextDelta
)

@Serializable
private data class TextDelta(
    val type: String,
    val text: String = ""
)

// ── Client ─────────────────────────────────────────────────────────────────

/**
 * Raw OkHttp client for the Anthropic Messages API with SSE streaming.
 *
 * Returns a Flow<String> that emits each text delta token as it arrives.
 * The Flow runs on Dispatchers.IO so callers can collect from any context.
 */
class AnthropicClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamMessages(messages: List<ApiMessage>): Flow<String> = flow {
        val body = json.encodeToString(
            ApiRequest(
                model = MODEL,
                maxTokens = 4096,
                stream = true,
                messages = messages
            )
        )

        val request = Request.Builder()
            .url(ENDPOINT)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = response.body?.string() ?: "Unknown error"
            response.close()
            throw AnthropicException(response.code, error)
        }

        val source = response.body?.source()
            ?: throw AnthropicException(0, "Empty response body")

        response.use {
            // SSE format: blank-line-separated events, each with optional
            // "event:" line and a "data:" line. We only need content_block_delta.
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break

                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ")
                if (data == "[DONE]") break

                try {
                    val envelope = json.decodeFromString<StreamEnvelope>(data)
                    if (envelope.type == "content_block_delta") {
                        val event = json.decodeFromString<ContentBlockDeltaEvent>(data)
                        if (event.delta.type == "text_delta" && event.delta.text.isNotEmpty()) {
                            emit(event.delta.text)
                        }
                    }
                } catch (_: Exception) {
                    // Skip malformed or unrecognised events gracefully
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        // Update this to whatever model you want to target.
        // See: https://docs.anthropic.com/en/api/models
        const val MODEL = "claude-sonnet-4-5"
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    }
}

class AnthropicException(val statusCode: Int, message: String) : Exception(
    if (statusCode > 0) "HTTP $statusCode: $message" else message
)
