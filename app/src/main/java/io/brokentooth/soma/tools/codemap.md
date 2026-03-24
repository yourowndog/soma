# SOMA Device Tools

## Responsibility
**Native Android System Interaction**
- Provides the "hands" for the AI agent to interact with the Android OS.
- Encapsulates complex Android APIs (Intents, CameraManager, ClipboardManager) into simple, tool-compatible functions.

## Design
- **Command Pattern**: Tools like `OpenAppTool` and `FlashlightTool` implement a standard execution interface for the agent.
- **Glossary Mapping**: `DeviceTools.kt` contains a semantic `APP_GLOSSARY` to map natural language (e.g., "texts") to package names (`com.google.android.apps.messaging`).
- **Context Injection**: `ToolContext` singleton ensures tools have access to the `ApplicationContext` without memory leaks.

## Flow
1. **Trigger**: `KoogAgentManager` identifies a tool need from LLM output.
2. **Dispatch**: The `ToolRegistry` looks up the corresponding `SimpleTool` object.
3. **Execution**: The tool's `execute()` method calls a native function in `DeviceTools.kt`.
4. **Feedback**: The string result (e.g., "Opened Chrome") is returned to the agent loop.

## Integration
- **Android OS**: Direct calls to `PackageManager`, `BatteryManager`, `ConnectivityManager`, and `Settings`.
- **Koog Agents**: Tools are registered in `KoogAgentManager` to be exposed to the LLM.
