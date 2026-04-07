# GEMINI.md - Project SOMA (Master Context)

## Project Vision
**SOMA** is an Android-native agentic AI assistant designed to move beyond simple chatbots. It leverages the **Koog Agent Framework** to provide tool-augmented intelligence with deep OS integration. It is designed to replace standalone automation apps by centralizing wake-word detection, screen reading, and device control into a single, persistent foreground service.

## Core Architecture
- **Agent Engine:** [Koog](https://github.com/JetBrains/koog) for the agent loop and function calling.
- **Persistence:** Room (SQLite) for sessions, messages, and Tier 1 (local) memory.
- **Background Operations:** `SomaService` (Foreground Service) for persistence and wake-word monitoring.
- **Interaction:** Jetpack Compose (Material 3 Dark Theme), Voice (STT/TTS), and Accessibility Services.
- **Ecosystem:** Integrates with a home Linux cluster ("The Fleet") for local inference (Ollama) and high-tier memory (QMD).

---

## Development Roadmap (Status: Phase 1.5 Complete)

### ✅ Completed
- **Phase 0-1.5:** Multi-model support (Gemini, Anthropic, OpenRouter), message persistence, model switching UI, and basic regex-based tool hacks.

### 🚀 Phase 2: Native Tooling (Active)
- **Koog Integration:** Replacing regex hacks with native function calling.
- **ToolRegistry:** Centralized definitions for native tools (OpenApp, DeviceInfo, Clipboard).

### 🛠 Phase 3 & 3.5: Android Deep Integration & Voice
- **System Control:** Alarms, Timers, Volume, Brightness, DND, and Flashlight.
- **Intents:** Launching any app activity or system settings panel.
- **Wake Word:** Porcupine integration ("Wake up Soma") in `SomaService`.
- **STT/TTS:** OpenAI Whisper (cloud) or Android Native (offline) with streaming playback.
- **Screen Reading:** Replacing "ScreenSense" with an on-demand accessibility tool.

### 💾 Phase 4 & 5: Memory & Management
- **Session Management:** Multi-conversation support, titles, and Markdown export.
- **Cross-Session Memory:** Saving/searching facts across conversations in Room.
- **Dashboard:** Token cost tracking, tool success rates, and agent health monitoring.
- **Profiles:** Customizable system prompts and model preferences per task.

### 🌐 Phase 7 & 8: Fleet & Smart Automation
- **Fleet Sync:** Auto-discovery of home nodes (Titan, Sleeper, Weakling) and Ollama offloading.
- **Smart Routing:** Auto-selection of models (e.g., Sonnet for code, Flash for chat).
- **Accessibility Automation:** Tier 3 control (clicking, scrolling, and navigating any app UI).
- **Self-Healing:** Automatic retry/fallback logic for failed tool calls or model errors.

---

## Technical Context for Agents

### Development Conventions
- **UI:** Stick to the "Power Tool" aesthetic (Dark theme, #0D1117 background, JetBrains Mono for code).
- **Asynchrony:** Use Kotlin Coroutines and Flow for all streaming and network operations.
- **Permissions:** Be mindful of Android runtime permissions (RECORD_AUDIO, ACCESSIBILITY, etc.).
- **Tools:** Register all new capabilities in `ToolRegistry.kt`. Prefer native function calling over string parsing.

### Key Knowledge Base
- **`SomaArchitecture.md`**: The definitive UI and architectural specification.
- **`SOMA_Master_Index.md`**: Tracks the status of each build phase.
- **`Soma prompts/`**: Contains the specific implementation guides for each phase. **Read the relevant phase prompt before starting work on a new feature.**

### Environmental Setup
- **`local.properties`**: Must contain `GOOGLE_API_KEY`, `OPENROUTER_API_KEY`, and `PICOVOICE_ACCESS_KEY`.
- **Target SDK:** 35 (Android 15). Min SDK: 26.
