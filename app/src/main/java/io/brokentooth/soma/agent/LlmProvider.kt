package io.brokentooth.soma.agent

import kotlinx.coroutines.flow.Flow

/**
 * Common message format used internally.
 * Providers translate to/from their own wire formats.
 */
data class ChatMessage(val role: String, val content: String)
// role = "user" or "assistant"

/**
 * A model available for selection.
 */
data class ModelOption(
    val id: String,           // e.g. "gemini-flash", "openrouter/claude-sonnet"
    val displayName: String,  // e.g. "Gemini Flash", "Claude Sonnet 4"
    val provider: String      // "gemini" or "openrouter"
)

/**
 * Interface all LLM providers implement.
 */
interface LlmProvider {
    fun streamChat(messages: List<ChatMessage>): Flow<String>
}
