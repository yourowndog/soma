# SOMA Phase 2: Koog Agent Loop + Proper Tool System

## Difficulty: Hard — Use Sonnet via OpenRouter or save for Opus exchange
## Prerequisite: Phase 1.5 complete (model switching, search, persist)

---

## Context

SOMA currently uses a raw HTTP client (GeminiClient/OpenRouterClient) that
sends messages and gets responses. Tool calls are hacked via regex pattern
matching on the response text ([[OPEN_APP:package.name]]). This works but
it's fragile and doesn't scale.

Phase 2 replaces the hack with Koog's proper agent loop. Koog handles:
- Sending messages to the LLM
- Detecting when the LLM wants to call a tool (via the API's native tool_use)
- Executing the tool
- Feeding the result back to the LLM
- Letting the LLM continue with the tool result

This means tools work reliably via the LLM's native function calling, not
regex hacks on response text.

**IMPORTANT:** Koog is already in the dependencies (ai.koog:koog-agents:0.6.2).
We need to actually USE it now.

---

## Step 1: Understand Koog's Android Compatibility

Before writing code, verify Koog works on Android. Create a simple test:

Create file: `agent/KoogTest.kt`
```kotlin
package io.brokentooth.soma.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.SingleLLMPromptExecutor

/**
 * Quick test to verify Koog initializes on Android.
 * Call this from ChatViewModel.init and log the result.
 * If this fails, we know Koog's Android target has issues
 * and we need to fall back to our manual approach.
 *
 * NOTE: The exact import paths and class names above are GUESSES
 * based on Koog documentation. YOU MUST verify against the actual
 * Koog 0.6.2 API. Check:
 * - https://docs.koog.ai/
 * - https://github.com/JetBrains/koog/tree/develop/examples
 * 
 * If the imports don't resolve, look at what's actually in the
 * ai.koog package by browsing the dependency in Android Studio
 * (External Libraries > koog-agents).
 */
suspend fun testKoogInit(): String {
    return try {
        // Try to create a minimal Koog agent
        // ADJUST THESE IMPORTS/CALLS based on actual Koog API
        "Koog initialized successfully"
    } catch (e: Exception) {
        "Koog init failed: ${e.message}"
    }
}
```

**CRITICAL:** The agent implementing this MUST browse the actual Koog 0.6.2
jar in the project's External Libraries to see what classes are available.
Do NOT guess. Koog's API has changed between versions and documentation
may be outdated. The source of truth is the actual compiled dependency.

---

## Step 2: Create Koog-Based Provider

If Koog works on Android, create a new provider that uses Koog's executor:

Create file: `agent/KoogProvider.kt`

This should:
1. Create a Koog `SingleLLMPromptExecutor` (or whatever the current API calls it)
   configured for OpenRouter (OpenAI-compatible endpoint)
2. Implement our `LlmProvider` interface
3. Support tool definitions — accept a list of tool schemas that get passed
   to the Koog agent
4. Return streamed text via Flow<String> just like our existing providers

**If Koog does NOT work on Android** (imports fail, runtime crashes, etc.):
Skip to Step 2B below.

---

## Step 2B: Fallback — Manual Tool Calling via OpenAI Function Calling API

If Koog doesn't work on Android, we implement tool calling ourselves using
OpenRouter's native function calling support (OpenAI-compatible format).

Modify `OpenRouterClient.kt` to support tools:

### Request format with tools:
```json
{
  "model": "anthropic/claude-sonnet-4",
  "stream": true,
  "messages": [...],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "open_app",
        "description": "Opens an Android app by its package name",
        "parameters": {
          "type": "object",
          "properties": {
            "package_name": {
              "type": "string",
              "description": "The Android package name, e.g. com.android.chrome"
            }
          },
          "required": ["package_name"]
        }
      }
    }
  ]
}
```

### Response handling for tool calls:
When the model wants to call a tool, the SSE stream will contain:
```json
{
  "choices": [{
    "delta": {
      "tool_calls": [{
        "index": 0,
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "open_app",
          "arguments": "{\"package_name\": \"com.android.chrome\"}"
        }
      }]
    }
  }]
}
```

The agent loop must:
1. Detect tool_calls in the response
2. Execute the tool locally
3. Send the result back as a new message with role "tool":
```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "✓ Opened com.android.chrome"
}
```
4. Continue the conversation — the model will now respond with text
   incorporating the tool result

---

## Step 3: Tool Registry

Create file: `tools/ToolRegistry.kt`

```kotlin
package io.brokentooth.soma.tools

import android.content.Context

/**
 * Defines a tool the agent can call.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
)

data class ToolParameter(
    val type: String,  // "string", "number", "boolean"
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

/**
 * Central registry of all available tools.
 * Returns tool definitions (for the LLM) and executes tools (when called).
 */
class ToolRegistry(private val context: Context) {

    private val tools = mutableMapOf<String, Pair<ToolDefinition, suspend (Map<String, Any?>) -> String>>()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        register(
            ToolDefinition(
                name = "open_app",
                description = "Opens an Android app by its package name. " +
                    "Common packages: com.android.chrome (Chrome), " +
                    "com.google.android.gm (Gmail), com.google.android.apps.maps (Maps), " +
                    "com.android.settings (Settings), com.google.android.youtube (YouTube), " +
                    "com.android.vending (Play Store), com.sec.android.app.sbrowser (Samsung Internet)",
                parameters = mapOf(
                    "package_name" to ToolParameter("string", "Android package name to open")
                )
            )
        ) { params ->
            val pkg = params["package_name"]?.toString() ?: return@register "Error: no package name"
            openApp(context, pkg)
        }

        register(
            ToolDefinition(
                name = "get_device_info",
                description = "Get device information including battery level, charging status, " +
                    "network connectivity, and device model",
                parameters = emptyMap()
            )
        ) { _ ->
            getDeviceInfo(context)
        }

        register(
            ToolDefinition(
                name = "clipboard_read",
                description = "Read the current text from the device clipboard",
                parameters = emptyMap()
            )
        ) { _ ->
            readClipboard(context)
        }

        register(
            ToolDefinition(
                name = "clipboard_write",
                description = "Write text to the device clipboard",
                parameters = mapOf(
                    "text" to ToolParameter("string", "Text to copy to clipboard")
                )
            )
        ) { params ->
            val text = params["text"]?.toString() ?: return@register "Error: no text"
            writeClipboard(context, text)
        }
    }

    fun register(definition: ToolDefinition, handler: suspend (Map<String, Any?>) -> String) {
        tools[definition.name] = Pair(definition, handler)
    }

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.first }

    suspend fun execute(name: String, params: Map<String, Any?>): String {
        val tool = tools[name] ?: return "Error: unknown tool '$name'"
        return try {
            tool.second(params)
        } catch (e: Exception) {
            "Error executing $name: ${e.message}"
        }
    }

    /**
     * Convert tool definitions to OpenAI function calling format
     * for inclusion in API requests.
     */
    fun toOpenAIFormat(): List<Map<String, Any>> {
        return getDefinitions().map { tool ->
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to tool.parameters.mapValues { (_, param) ->
                            buildMap {
                                put("type", param.type)
                                put("description", param.description)
                                if (param.enum != null) put("enum", param.enum)
                            }
                        },
                        "required" to tool.parameters.filter { it.value.required }.keys.toList()
                    )
                )
            )
        }
    }
}
```

---

## Step 4: Additional Tool Implementations

Create file: `tools/DeviceTools.kt`
```kotlin
package io.brokentooth.soma.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build

fun getDeviceInfo(context: Context): String {
    val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    val isCharging = batteryManager.isCharging

    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)
    val networkType = when {
        capabilities == null -> "No connection"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        else -> "Other"
    }

    return buildString {
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Battery: $batteryLevel%${if (isCharging) " (charging)" else ""}")
        appendLine("Network: $networkType")
    }.trim()
}

fun readClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    return if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).text?.toString() ?: "(empty clipboard)"
    } else {
        "(empty clipboard)"
    }
}

fun writeClipboard(context: Context, text: String): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SOMA", text))
    return "Copied to clipboard: ${text.take(50)}${if (text.length > 50) "..." else ""}"
}
```

---

## Step 5: Integrate Tool Calling into the Agent Loop

Modify `ChatAgent.kt` to support an agent loop with tool calling:

```kotlin
class ChatAgent(
    private var provider: LlmProvider,
    private val messageDao: MessageDao,
    private val toolRegistry: ToolRegistry? = null  // NEW
) {
    // ... existing code ...

    /**
     * Enhanced send that supports tool calling.
     * Returns a Flow that emits text tokens AND handles tool calls internally.
     * The Flow completes when the model is done (no more tool calls).
     */
    fun sendMessageWithTools(userText: String): Flow<String>
    // Implementation depends on whether we're using Koog (Step 2) or manual (Step 2B)
}
```

If using the manual approach (Step 2B), the flow is:
1. Send message + tool definitions to OpenRouter
2. Stream response
3. If response contains tool_calls → execute tools → send results back → stream next response
4. If response is pure text → emit tokens → done
5. Maximum 5 tool call rounds to prevent infinite loops

---

## Step 6: Update ChatViewModel

Modify the `sendMessage` function to use the new tool-aware agent:

- Remove the regex-based `handleToolCalls` call
- Remove the `[[OPEN_APP:...]]` pattern stripping
- Remove the tool-related system prompt from GeminiClient and OpenRouterClient
  (tools are now declared via the API, not in the system prompt)
- Tool calls should appear in the chat as ToolCallCard composables (Phase 2.5)

For now, tool execution can be shown as system messages:
"⚙ open_app(com.android.chrome) → ✓ Opened com.android.chrome"

---

## Step 7: Remove the Regex Hack

Delete the tool-related regex patterns from:
- `OpenAppTool.kt` — remove `handleToolCalls` and `OPEN_APP_PATTERN`
- `ChatViewModel.kt` — remove the `handleToolCalls` call and pattern stripping
- `GeminiClient.kt` — remove tool instructions from SYSTEM_PROMPT
- `OpenRouterClient.kt` — remove tool instructions from SYSTEM_PROMPT

The system prompt should now be just:
```kotlin
"You are SOMA, a helpful AI assistant running on an Android phone. " +
"You can use tools to interact with the device when asked."
```

The actual tool descriptions are sent via the `tools` parameter in the API call.

---

## Testing

1. Say "what's my battery level?" → agent calls get_device_info → shows result
2. Say "open Chrome" → agent calls open_app → Chrome opens
3. Say "copy hello world to clipboard" → agent calls clipboard_write → confirms
4. Say "what's on my clipboard?" → agent calls clipboard_read → shows content
5. Tool calls should NOT show [[OPEN_APP:...]] patterns — they should be clean
6. Model switching still works — tools available on all models that support function calling
7. Free models that DON'T support function calling should still work for chat
   (just no tools)

---

## Important Note on Gemini Direct

The Gemini direct API (your free API key) has a DIFFERENT function calling
format than OpenAI. For Phase 2, tool calling only works via OpenRouter
models. When using Gemini Direct, tools are simply not available (chat only).
This is fine — you'll use free OpenRouter models for tool testing.

Adding Gemini-native function calling is a future enhancement.

---

## What Success Looks Like

The agent can call tools natively through the LLM's function calling API.
No more regex hacks. Tools are declared in a registry and can be easily
added. The system is extensible — adding a new tool is: define it in
ToolRegistry, implement the handler, done.
