# SOMA - Android Native Agentic Assistant

## Responsibility
**Native Android Agentic Assistant**
- SOMA is a native Android agent designed to provide natural language control over the device.
- It integrates LLMs (Gemini, OpenRouter) with native Android system tools.

## Design
- **Clean Architecture (Simplified)**: Separated into `ui`, `agent`, `data`, and `tools` layers.
- **Agentic Loop**: Uses the Koog framework for autonomous tool-calling and reasoning.
- **Native First**: Rejects sandbox hacks in favor of direct Android API integration.

## Flow
1. **User Input**: Captured via Jetpack Compose UI.
2. **ViewModel**: `ChatViewModel` processes input and delegates to `ChatAgent`.
3. **Agent Layer**: `ChatAgent` manages history and invokes an `LlmProvider`.
4. **Tool Execution**: If needed, `KoogAgentManager` calls native tools in `DeviceTools.kt`.
5. **Persistence**: All messages and sessions are stored in a local Room database.
6. **UI Update**: Streaming responses are reflected in the UI via `StateFlow`.

## Integration
- **Google Gemini API**: Direct integration for high-performance reasoning.
- **OpenRouter API**: Access to a wide range of LLMs with auto-failover.
- **Koog Agent Framework**: Core logic for tool-use and agent strategies.
- **Android System**: Deep integration with OS-level APIs (Intents, Settings, Hardware).

## Project Structure
- `app/src/main/java/io/brokentooth/soma/`
  - `agent/`: LLM clients and agent orchestration.
  - `data/`: Room database, DAOs, and models.
  - `tools/`: Native Android system tools.
  - `ui/`: Jetpack Compose UI and ViewModels.
