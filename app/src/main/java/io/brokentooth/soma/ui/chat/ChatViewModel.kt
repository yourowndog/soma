package io.brokentooth.soma.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.brokentooth.soma.BuildConfig
import io.brokentooth.soma.SomaApplication
import io.brokentooth.soma.agent.AnthropicClient
import io.brokentooth.soma.agent.ChatAgent
import io.brokentooth.soma.data.model.Message
import io.brokentooth.soma.data.model.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String = "",
    val messages: List<Message> = emptyList(),
    /** Non-null while Claude is streaming; updated per-token. */
    val streamingText: String? = null,
    val isStreaming: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SomaApplication.database
    private val sessionDao = db.sessionDao()
    private val messageDao = db.messageDao()
    private val agent = ChatAgent(
        client = AnthropicClient(BuildConfig.ANTHROPIC_API_KEY),
        messageDao = messageDao
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String = ""
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch { initSession() }
    }

    // ── Session bootstrap ───────────────────────────────────────────────────

    private suspend fun initSession() {
        val sessions = sessionDao.getAll()
        val session = sessions.firstOrNull() ?: Session().also { sessionDao.insert(it) }
        currentSessionId = session.id

        val messages = messageDao.getBySessionId(currentSessionId)
        agent.loadHistory(currentSessionId)

        _uiState.update { it.copy(sessionId = currentSessionId, messages = messages) }
    }

    // ── Send ────────────────────────────────────────────────────────────────

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isStreaming) return

        streamingJob = viewModelScope.launch {
            val userMsg = Message(sessionId = currentSessionId, role = "user", content = text.trim())
            messageDao.insert(userMsg)
            _uiState.update {
                it.copy(messages = it.messages + userMsg, isStreaming = true, streamingText = "", error = null)
            }

            val accum = StringBuilder()
            try {
                agent.sendMessage(text.trim()).collect { token ->
                    accum.append(token)
                    _uiState.update { it.copy(streamingText = accum.toString()) }
                }

                commitAssistantMessage(accum.toString())

                // Rename session after first exchange
                if (_uiState.value.messages.size <= 2) {
                    sessionDao.updateTitle(currentSessionId, text.trim().take(50))
                }
            } catch (e: Exception) {
                // If we have partial content, save it rather than discard
                if (accum.isNotEmpty()) commitAssistantMessage(accum.toString())
                _uiState.update { it.copy(streamingText = null, isStreaming = false, error = e.message) }
            }
        }
    }

    // ── Stop ────────────────────────────────────────────────────────────────

    fun stopStreaming() {
        streamingJob?.cancel()
        val partial = _uiState.value.streamingText
        viewModelScope.launch {
            if (!partial.isNullOrBlank()) {
                commitAssistantMessage(partial)
            } else {
                _uiState.update { it.copy(streamingText = null, isStreaming = false) }
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun commitAssistantMessage(text: String) {
        val msg = Message(sessionId = currentSessionId, role = "assistant", content = text)
        messageDao.insert(msg)
        agent.finalizeAssistantMessage(text)
        _uiState.update { it.copy(messages = it.messages + msg, streamingText = null, isStreaming = false) }
    }
}
