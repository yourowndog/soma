package io.brokentooth.soma.agent

import io.brokentooth.soma.data.db.MessageDao
import kotlinx.coroutines.flow.Flow

/**
 * Manages in-memory conversation history and delegates streaming to GeminiClient.
 */
class ChatAgent(
    private val client: GeminiClient,
    private val messageDao: MessageDao
) {
    private val history = mutableListOf<Content>()

    /** Call once after loading (or creating) a session to hydrate in-memory history. */
    suspend fun loadHistory(sessionId: String) {
        val persisted = messageDao.getBySessionId(sessionId)
        history.clear()
        // Map Room roles to Gemini roles: Gemini uses "user" and "model"
        history.addAll(persisted.map { 
            val role = if (it.role == "assistant") "model" else "user"
            Content(role, listOf(Part(it.content))) 
        })
    }

    /**
     * Adds the user turn to history and begins streaming a response.
     */
    fun sendMessage(userText: String): Flow<String> {
        history.add(Content("user", listOf(Part(userText))))
        return client.streamMessages(history.toList())
    }

    /** Appends the completed assistant response to in-memory history. */
    fun finalizeAssistantMessage(text: String) {
        history.add(Content("model", listOf(Part(text))))
    }
}
