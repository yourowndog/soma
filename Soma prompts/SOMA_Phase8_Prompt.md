# SOMA Phase 8: Smart Routing + Accessibility Automation + Polish

## Difficulty: Hard — Opus-level for routing design, Sonnet for accessibility
## Prerequisite: Phase 7 complete (or at least Phase 5)

---

## Task 8a: Rule-Based Model Router

Create file: `agent/ModelRouter.kt`

Automatically select the best model based on the user's message and context.

```kotlin
package io.brokentooth.soma.agent

/**
 * Classifies user messages and routes to the optimal model.
 * Rules are simple keyword/pattern matching — no LLM needed for classification.
 */
class ModelRouter(
    private val availableModels: List<ModelOption>,
    private val fleetOnline: Boolean,
    private val monthlyBudget: Double,
    private val currentSpend: Double
) {
    data class RoutingDecision(
        val model: ModelOption,
        val reason: String
    )

    fun route(userMessage: String): RoutingDecision {
        val msg = userMessage.lowercase()

        // If budget is nearly exhausted, force free models
        if (currentSpend >= monthlyBudget * 0.9) {
            return findFreeModel("Budget limit approaching")
        }

        // Code-related → Claude Sonnet (best for code)
        if (containsCodeKeywords(msg)) {
            return findModel("anthropic/claude-sonnet-4", "Code task detected")
                ?: findFreeModel("Code task, no paid model available")
        }

        // Long document/analysis → Gemini Pro (large context)
        if (msg.length > 2000 || msg.contains("analyze") || msg.contains("summarize this")) {
            return findModel("google/gemini-2.5-flash-preview", "Long context task")
                ?: findFreeModel("Long context task")
        }

        // Math/logic/reasoning → DeepSeek R1
        if (containsReasoningKeywords(msg)) {
            return findModel("deepseek/deepseek-r1", "Reasoning task")
                ?: findFreeModel("Reasoning task")
        }

        // Simple chat → free model
        return findFreeModel("General chat")
    }

    private fun containsCodeKeywords(msg: String): Boolean {
        val keywords = listOf("code", "function", "debug", "error", "compile",
            "kotlin", "python", "javascript", "gradle", "git", "commit",
            "refactor", "implement", "class ", "def ", "fun ", "```")
        return keywords.any { msg.contains(it) }
    }

    private fun containsReasoningKeywords(msg: String): Boolean {
        val keywords = listOf("solve", "calculate", "prove", "logic",
            "math", "equation", "step by step", "think through", "compare")
        return keywords.any { msg.contains(it) }
    }

    private fun findModel(id: String, reason: String): RoutingDecision? {
        val model = availableModels.find { it.id == id }
        return model?.let { RoutingDecision(it, reason) }
    }

    private fun findFreeModel(reason: String): RoutingDecision {
        val freeModel = availableModels.firstOrNull { it.isFree }
            ?: availableModels.first()  // fallback
        return RoutingDecision(freeModel, "$reason → using free model")
    }
}
```

### Wire into ChatViewModel:

Add an "Auto" mode to model selection:
- When /model → show "Auto (Smart Routing)" as the first option
- When auto is active, each message goes through ModelRouter before sending
- Show which model was selected in the chat: "🤖 Routed to: Claude Sonnet 4 (code task)"
- User can override by selecting a specific model

---

## Task 8b: Accessibility Service Automation

Create file: `service/SomaAccessibilityService.kt`

This is Tier 3 phone control — the agent can interact with ANY app's UI.

```kotlin
package io.brokentooth.soma.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

class SomaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: SomaAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("SOMA Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events — we're action-driven
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ── Actions the agent can perform ──────────────────────────────────

    /**
     * Find and click a UI element containing the given text.
     */
    fun clickByText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            // Try clicking the parent if the node itself isn't clickable
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    /**
     * Read all visible text on the current screen.
     */
    fun readScreen(): String {
        val rootNode = rootInActiveWindow ?: return "No active window"
        val texts = mutableListOf<String>()
        collectText(rootNode, texts)
        return texts.joinToString("\n")
    }

    private fun collectText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) texts.add("[${it}]") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, texts) }
        }
    }

    /**
     * Scroll in a direction.
     */
    fun scroll(direction: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val action = when (direction.lowercase()) {
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> return false
        }
        return findScrollable(rootNode)?.performAction(action) ?: false
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findScrollable(child)
            if (result != null) return result
        }
        return null
    }

    /**
     * Press system buttons.
     */
    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Tap at specific coordinates.
     */
    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
```

### AndroidManifest additions:

```xml
<service
    android:name=".service.SomaAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

Create `res/xml/accessibility_service_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagRequestTouchExplorationMode"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
```

### Register as tools:

```kotlin
register(ToolDefinition(
    name = "screen_read",
    description = "Read all visible text on the current screen. " +
        "Use this to understand what app is open and what's displayed.",
    parameters = emptyMap()
)) { _ ->
    SomaAccessibilityService.instance?.readScreen()
        ?: "Accessibility service not enabled. Please enable in Settings > Accessibility > SOMA"
}

register(ToolDefinition(
    name = "screen_tap",
    description = "Tap/click on a UI element containing specific text",
    parameters = mapOf(
        "text" to ToolParameter("string", "Text on the element to tap")
    )
)) { params ->
    val text = params["text"]?.toString() ?: return@register "Error: no text"
    val service = SomaAccessibilityService.instance
        ?: return@register "Accessibility service not enabled"
    if (service.clickByText(text)) "Tapped: $text" else "Element not found: $text"
}

register(ToolDefinition(
    name = "screen_scroll",
    description = "Scroll the current screen",
    parameters = mapOf(
        "direction" to ToolParameter("string", "Scroll direction: up or down",
            enum = listOf("up", "down"))
    )
)) { params ->
    val dir = params["direction"]?.toString() ?: "down"
    val service = SomaAccessibilityService.instance
        ?: return@register "Accessibility service not enabled"
    if (service.scroll(dir)) "Scrolled $dir" else "Cannot scroll"
}

register(ToolDefinition(
    name = "press_back",
    description = "Press the Android back button",
    parameters = emptyMap()
)) { _ ->
    SomaAccessibilityService.instance?.pressBack()
        ?.let { if (it) "Pressed back" else "Failed" }
        ?: "Accessibility service not enabled"
}

register(ToolDefinition(
    name = "press_home",
    description = "Press the Android home button",
    parameters = emptyMap()
)) { _ ->
    SomaAccessibilityService.instance?.pressHome()
        ?.let { if (it) "Pressed home" else "Failed" }
        ?: "Accessibility service not enabled"
}
```

---

## Task 8c: Self-Healing + Heartbeat

Modify `service/SomaService.kt`:

### Heartbeat:
- Every 60 seconds, log a heartbeat to Room DB
- Dashboard reads heartbeat logs to show uptime

### Self-healing:
- If a tool call fails, the agent automatically:
  1. Logs the error
  2. Tries an alternative approach (e.g., if open_app fails, try search_web)
  3. Reports what happened to the user

Add to system prompt:
```
"If a tool call fails, try an alternative approach. For example, if open_app
fails, try searching the web for the app instead. Always tell the user what
you tried and what happened."
```

### Error recovery:
- If the LLM API call fails (network error, rate limit):
  1. Retry once after 2 seconds
  2. If still failing, try a different model (fallback chain)
  3. If all models fail, show clear error with retry button

---

## Task 8d: UI Polish

Final pass on the UI to match the spec from the Architecture Plan:

1. **Code blocks** — syntax highlighting (use a simple regex-based highlighter
   for Kotlin/Python/bash keywords, or a library like highlight.js via WebView)
2. **Tool call cards** — the purple-bordered collapsible cards from the spec
3. **Streaming animation** — cursor/caret at end of streaming text
4. **Smooth scrolling** — no janky jumps when new messages arrive
5. **Long-press message** — copy text, share, regenerate response
6. **Markdown rendering** — bold, italic, headers, links, lists
   (consider using a library like `compose-markdown` or `markwon` adapted for Compose)

---

## Testing

1. Set model to "Auto" → send code question → routes to Claude
2. Send "what's the weather" → routes to free model
3. Enable accessibility service → "read this screen" → reads visible text
4. "Tap on Settings" → actually taps the Settings element on screen
5. "Press back" → goes back
6. Close and reopen app → dashboard shows uptime from heartbeat logs
7. Disconnect internet → send message → retry + fallback works gracefully
8. Code blocks have basic syntax highlighting
9. Tool calls show as purple cards with expand/collapse
