package io.brokentooth.soma.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.brokentooth.soma.BuildConfig
import io.brokentooth.soma.SomaApplication
import io.brokentooth.soma.agent.ChatAgent
import io.brokentooth.soma.agent.ChatMessage
import io.brokentooth.soma.agent.GeminiClient
import io.brokentooth.soma.agent.LlmProvider
import io.brokentooth.soma.agent.ModelOption
import io.brokentooth.soma.agent.OpenRouterClient
import io.brokentooth.soma.data.model.Message
import io.brokentooth.soma.data.model.Session
import io.brokentooth.soma.tools.handleToolCalls
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val sessionId: String = "",
    val messages: List<Message> = emptyList(),
    /** Non-null while streaming; updated per-token. */
    val streamingText: String? = null,
    val isStreaming: Boolean = false,
    val error: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SomaApplication.database
    private val sessionDao = db.sessionDao()
    private val messageDao = db.messageDao()

    val availableModels = listOf(
        ModelOption("gemini-flash", "Gemini Flash", "gemini"),
        ModelOption("anthropic/claude-sonnet-4", "Claude Sonnet 4", "openrouter"),
        ModelOption("deepseek/deepseek-r1", "DeepSeek R1", "openrouter"),
    )

    private val _currentModel = MutableStateFlow(availableModels.first())
    val currentModel: StateFlow<ModelOption> = _currentModel.asStateFlow()

    private val agent = ChatAgent(
        provider = createProvider(availableModels.first()),
        messageDao = messageDao
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String = ""
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch { initSession() }
    }

    // ── Model switching ──────────────────────────────────────────────────────

    fun switchModel(model: ModelOption) {
        if (model.id == _currentModel.value.id) return
        _currentModel.value = model
        agent.switchProvider(createProvider(model))

        // Add a system message to the chat
        viewModelScope.launch {
            val sysMsg = Message(
                sessionId = currentSessionId,
                role = "system",
                content = "Switched to ${model.displayName}"
            )
            messageDao.insert(sysMsg)
            _uiState.update { it.copy(messages = it.messages + sysMsg) }
        }
    }

    private fun createProvider(model: ModelOption): LlmProvider {
        return when (model.provider) {
            "gemini" -> GeminiClient(BuildConfig.GOOGLE_API_KEY)
            "openrouter" -> OpenRouterClient(
                apiKey = BuildConfig.OPENROUTER_API_KEY,
                modelId = model.id
            )
            else -> throw IllegalArgumentException("Unknown provider: ${model.provider}")
        }
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

                val responseText = accum.toString()
                commitAssistantMessage(responseText)

                // Check for tool calls in the response
                val toolResult = handleToolCalls(getApplication(), responseText)
                if (toolResult != null) {
                    val toolMsg = Message(
                        sessionId = currentSessionId,
                        role = "system",
                        content = toolResult
                    )
                    messageDao.insert(toolMsg)
                    _uiState.update { it.copy(messages = it.messages + toolMsg) }
                }

                // Rename session after first exchange
                if (_uiState.value.messages.size <= 2) {
                    sessionDao.updateTitle(currentSessionId, text.trim().take(50))
                }
            } catch (e: Exception) {
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
