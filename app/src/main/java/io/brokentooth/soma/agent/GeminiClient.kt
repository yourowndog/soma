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
    val contents: List<Content>
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
 */
class GeminiClient(private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun streamMessages(messages: List<Content>): Flow<String> = flow {
        val body = json.encodeToString(GeminiRequest(contents = messages))

        // Updated to use the 'gemini-flash-latest' alias confirmed by your API list
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$apiKey"

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
}
