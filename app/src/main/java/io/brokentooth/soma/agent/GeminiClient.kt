package io.brokentooth.soma.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// ── Gemini Wire types ──────────────────────────────────────────────────────

@Serializable
data class Content(val role: String, val parts: List<Part>)

@Serializable
data class Part(val text: String)

@Serializable
private data class GeminiRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null
)

@Serializable
private data class GeminiResponse(
    val candidates: List<Candidate>
)

@Serializable
private data class Candidate(
    val content: Content
)

/**
 * Client for Google's Gemini API.
 * Implements LlmProvider so it can be swapped with other providers.
 */
class GeminiClient(private val apiKey: String) : LlmProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        // Convert ChatMessage to Gemini's Content format
        // Gemini uses "model" instead of "assistant"
        val contents = messages.map { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            Content(role, listOf(Part(msg.content)))
        }

        val systemContent = Content("user", listOf(Part(SYSTEM_PROMPT)))

        val body = json.encodeToString(
            GeminiRequest(contents = contents, systemInstruction = systemContent)
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = http.newCall(request).execute()

        if (!response.isSuccessful) {
            val error = response.body?.string() ?: "Unknown error"
            throw Exception("Gemini API Error: ${response.code} $error")
        }

        val responseText = response.body?.string() ?: ""
        try {
            val result = json.decodeFromString<GeminiResponse>(responseText)
            val text = result.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            if (text.isNotEmpty()) emit(text)
        } catch (e: Exception) {
            throw Exception("Failed to parse Gemini response: ${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val SYSTEM_PROMPT = "You are SOMA, a helpful AI assistant. " +
            "You have access to tools. To open an app, output [[OPEN_APP:package.name]]. " +
            "Common packages: com.android.chrome (Chrome), com.google.android.gm (Gmail), " +
            "com.google.android.apps.maps (Maps), com.android.settings (Settings), " +
            "com.google.android.youtube (YouTube), com.google.android.dialer (Phone), " +
            "com.google.android.apps.messaging (Messages)."
    }
}
