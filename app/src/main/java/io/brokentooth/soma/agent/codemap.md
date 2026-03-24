# SOMA Agent Layer

## Responsibility
**Agent Orchestration & LLM Integration**
- Manages the lifecycle of AI agents and their interaction with LLM providers.
- Handles conversation history management and provider-agnostic message formatting.
- Orchestrates tool-calling loops via the Koog framework.

## Design
- **Strategy Pattern**: `LlmProvider` interface allows hot-swapping between `GeminiClient`, `OpenRouterClient`, and `KoogAgentManager`.
- **Manager/Orchestrator**: `ChatAgent` acts as the primary coordinator for conversation state.
- **Registry**: `ModelRegistry` and `OpenRouterModels` manage dynamic discovery of available LLM models.

## Flow
1. **Input**: `ChatViewModel` calls `ChatAgent.sendMessage(text)`.
2. **History**: `ChatAgent` appends user message to in-memory `history`.
3. **Execution**: `LlmProvider.streamChat(history)` is invoked.
4. **Tool Loop (Koog)**: If using `KoogAgentManager`, the request enters an autonomous loop:
   - LLM decides to call a tool (e.g., `OpenAppTool`).
   - `ToolRegistry` executes the tool via `DeviceTools`.
   - Result is fed back to LLM until a final response is generated.
5. **Output**: Final text is emitted as a `Flow<String>` back to the ViewModel.

## Integration
- **Koog Framework**: Deep integration with `ai.koog.agents` for tool-use and prompt DSL.
- **LLM APIs**: Direct integrations with Google Gemini and OpenRouter (Ktor/OkHttp).
- **Local Tools**: Hooks into `io.brokentooth.soma.tools` for Android system interaction.
