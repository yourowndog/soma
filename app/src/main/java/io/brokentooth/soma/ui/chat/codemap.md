# SOMA Chat UI Layer

## Responsibility
**User Interface & State Management**
- Provides the primary chat interface for user interaction.
- Manages UI state, model selection, and streaming response updates.

## Design
- **MVVM Pattern**: `ChatViewModel` holds the `ChatUiState` and handles business logic.
- **Unidirectional Data Flow (UDF)**: UI observes `StateFlow` from the ViewModel and sends events (e.g., `sendMessage`) back.
- **Compose Components**: Modular UI with `ChatScreen`, `MessageBubble`, and `ChatInput`.

## Flow
1. **Input**: User types in `ChatInput` and clicks send.
2. **Action**: `ChatViewModel.sendMessage(text)` is called.
3. **State Update**: `_uiState` is updated with the user message and `isStreaming = true`.
4. **Streaming**: As tokens arrive from `ChatAgent`, `streamingText` in the UI state is updated.
5. **Completion**: `commitAssistantMessage()` is called to finalize the message in the database and UI state.

## Integration
- **Jetpack Compose**: Material 3 components for the UI.
- **ChatAgent**: ViewModel delegates LLM interaction to the agent.
- **Room DAOs**: ViewModel directly interacts with `MessageDao` and `SessionDao` for persistence.
- **SharedPreferences**: Used for persisting the user's selected model ID.
