package io.brokentooth.soma.agent

import io.brokentooth.soma.data.db.MessageDao
import kotlinx.coroutines.flow.Flow

/**
 * Manages in-memory conversation history and delegates streaming to AnthropicClient.
 *
 * The history mirrors Room — it's loaded once on session start and kept in sync
 * so every API call gets the full conversation context.
 */
class ChatAgent(
    private val client: AnthropicClient,
    private val messageDao: MessageDao
) {
    private val history = mutableListOf<ApiMessage>()

    /** Call once after loading (or creating) a session to hydrate in-memory history. */
    suspend fun loadHistory(sessionId: String) {
        val persisted = messageDao.getBySessionId(sessionId)
        history.clear()
        history.addAll(persisted.map { ApiMessage(it.role, it.content) })
    }

    /**
     * Adds the user turn to history and begins streaming a response.
     * Callers must call [finalizeAssistantMessage] with the complete text when the
     * flow completes, so the assistant turn is recorded for future API calls.
     */
    fun sendMessage(userText: String): Flow<String> {
        history.add(ApiMessage("user", userText))
        return client.streamMessages(history.toList())
    }

    /** Appends the completed assistant response to in-memory history. */
    fun finalizeAssistantMessage(text: String) {
        history.add(ApiMessage("assistant", text))
    }
}
