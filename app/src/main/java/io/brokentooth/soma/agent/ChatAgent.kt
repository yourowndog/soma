package io.brokentooth.soma.agent

import io.brokentooth.soma.data.db.MessageDao
import kotlinx.coroutines.flow.Flow

/**
 * Manages in-memory conversation history and delegates streaming to any LlmProvider.
 *
 * History is stored as ChatMessage (provider-agnostic). When switching providers
 * mid-conversation, the history carries over seamlessly.
 */
class ChatAgent(
    private var provider: LlmProvider,
    private val messageDao: MessageDao
) {
    private val history = mutableListOf<ChatMessage>()

    /** Swap the underlying LLM provider without losing conversation history. */
    fun switchProvider(newProvider: LlmProvider) {
        provider = newProvider
    }

    /** Call once after loading (or creating) a session to hydrate in-memory history. */
    suspend fun loadHistory(sessionId: String) {
        val persisted = messageDao.getBySessionId(sessionId)
        history.clear()
        history.addAll(persisted.map { ChatMessage(it.role, it.content) })
    }

    /**
     * Adds the user turn to history and begins streaming a response.
     * Callers must call [finalizeAssistantMessage] with the complete text when the
     * flow completes, so the assistant turn is recorded for future API calls.
     */
    fun sendMessage(userText: String): Flow<String> {
        history.add(ChatMessage("user", userText))
        return provider.streamChat(history.toList())
    }

    /** Appends the completed assistant response to in-memory history. */
    fun finalizeAssistantMessage(text: String) {
        history.add(ChatMessage("assistant", text))
    }
}
