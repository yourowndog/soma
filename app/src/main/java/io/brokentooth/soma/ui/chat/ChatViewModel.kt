package io.brokentooth.soma.ui.chat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.brokentooth.soma.BuildConfig
import io.brokentooth.soma.SomaApplication
import io.brokentooth.soma.agent.ChatAgent
import io.brokentooth.soma.agent.GeminiClient
import io.brokentooth.soma.agent.LlmProvider
import io.brokentooth.soma.agent.ModelOption
import io.brokentooth.soma.agent.ModelRegistry
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

    // Default model — free tier via OpenRouter so it always works
    private val defaultModel = ModelOption(
        id = "stepfun/step-3.5-flash:free",
        displayName = "[Stp] Step 3.5 Flash (Free)", 
        provider = "openrouter",
        isFree = true,
        contextLength = 256000,
        ipdScore = 100f
    )

    val geminiModel = ModelOption(
        id = "gemini-flash",
        displayName = "[Goo] Gemini 3.1 Flash (Direct)",
        provider = "gemini",
        isFree = true,
        contextLength = 1048576,
        ipdScore = 1000f // Keep it top
    )

    private val _availableModels = MutableStateFlow(listOf(defaultModel))
    val availableModels: StateFlow<List<ModelOption>> = _availableModels.asStateFlow()

    private val _modelsLoading = MutableStateFlow(true)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    private val _currentModel = MutableStateFlow(defaultModel)
    val currentModel: StateFlow<ModelOption> = _currentModel.asStateFlow()

    private val agent = ChatAgent(
        provider = createProvider(defaultModel),
        messageDao = messageDao
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var currentSessionId: String = ""
    private var streamingJob: Job? = null

    init {
        viewModelScope.launch { initSession() }
        viewModelScope.launch { loadModels() }
    }

    // ── Dynamic model loading ────────────────────────────────────────────────

    private suspend fun loadModels() {
        try {
            val models = ModelRegistry.fetchAvailableModels(
                geminiApiKey = BuildConfig.GOOGLE_API_KEY,
                openRouterApiKey = BuildConfig.OPENROUTER_API_KEY
            )
            // Prepend gemini direct model
            val combined = (listOf(geminiModel) + models).distinctBy { it.id }
            if (combined.isNotEmpty()) {
                _availableModels.value = combined
                // If current default isn't in the fetched list, switch to first available
                if (combined.none { it.id == _currentModel.value.id }) {
                    val first = combined.first()
                    _currentModel.value = first
                    agent.switchProvider(createProvider(first))
                    Log.w("ChatViewModel", "Default model ${defaultModel.id} not available, switched to ${first.id}")
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Failed to load models", e)
        } finally {
            _modelsLoading.value = false
        }
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
            "gemini" -> GeminiClient(
                apiKey = BuildConfig.GOOGLE_API_KEY,
                modelId = model.id
            )
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

            var attempts = 0
            val freeModels = _availableModels.value.filter { it.isFree }
            var maxAttempts = if (_currentModel.value.isFree) freeModels.size else 1
            if (maxAttempts == 0) maxAttempts = 1

            var success = false

            while (attempts < maxAttempts && !success) {
                val accum = StringBuilder()
                try {
                    agent.sendMessage(text.trim()).collect { token ->
                        accum.append(token)
                        _uiState.update { it.copy(streamingText = accum.toString()) }
                    }

                    val responseText = accum.toString()
                    Log.d("ChatViewModel", "Full response: $responseText")
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
                    success = true
                } catch (e: Exception) {
                    val errorMsg = e.message ?: ""
                    Log.e("ChatViewModel", "sendMessage failed: model=${_currentModel.value.id} provider=${_currentModel.value.provider}", e)

                    if ((errorMsg.contains("429") || errorMsg.contains("rate limit", true)) && _currentModel.value.isFree) {
                        attempts++
                        if (attempts >= maxAttempts) {
                            _uiState.update { it.copy(streamingText = null, isStreaming = false, error = "All free model usage currently expended. Please wait a few moments or switch to a paid model.") }
                            break
                        }

                        // Round-robin swap
                        val currentIndex = freeModels.indexOfFirst { it.id == _currentModel.value.id }
                        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % freeModels.size else 0
                        val nextModel = freeModels[nextIndex]

                        // Switch model
                        _currentModel.value = nextModel
                        agent.switchProvider(createProvider(nextModel))

                        // Add system message
                        val sysMsg = Message(
                            sessionId = currentSessionId,
                            role = "system",
                            content = "Rate limit reached. Auto-swapped to ${nextModel.displayName}"
                        )
                        messageDao.insert(sysMsg)
                        _uiState.update { it.copy(messages = it.messages + sysMsg, error = null, streamingText = "") }

                        // continue to retry loop
                    } else {
                        if (accum.isNotEmpty()) commitAssistantMessage(accum.toString())
                        _uiState.update { it.copy(streamingText = null, isStreaming = false, error = e.message) }
                        break
                    }
                }
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
