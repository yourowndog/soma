# SOMA Phase 5: Dashboard + Agent Configuration

## Difficulty: Medium — Gemini Pro or any decent model
## Prerequisite: Phase 4 complete

---

## Context

SOMA now has multiple sessions, tools, memory, and model switching.
Phase 5 adds visibility into what the agent is doing (dashboard) and
the ability to configure agent behavior (profiles, system prompts).

---

## Task 5a: Tool Call Logging

Create file: `data/db/ToolLogDao.kt` and `data/model/ToolLog.kt`

```kotlin
@Entity(tableName = "tool_logs")
data class ToolLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val toolName: String,
    val args: String,       // JSON string of arguments
    val result: String,
    val success: Boolean,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
```

Modify ToolRegistry.execute() to log every tool call:
- Record start time
- Execute tool
- Record end time
- Save ToolLog to Room
- Return result as before

---

## Task 5b: Cost Tracking

Create file: `data/CostTracker.kt`

Track API usage per request:
- After each API call completes, parse the usage from the response
  (OpenRouter returns token counts in the final SSE message or response headers)
- Store: model_id, input_tokens, output_tokens, estimated_cost, timestamp
- Estimated cost = input_tokens * model_input_price + output_tokens * model_output_price

Create Room entity: `ApiUsageLog`
```kotlin
@Entity(tableName = "api_usage")
data class ApiUsageLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val modelId: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCost: Double,  // USD
    val timestamp: Long = System.currentTimeMillis()
)
```

---

## Task 5c: Dashboard Screen

Create file: `ui/dashboard/DashboardScreen.kt`

Follow the layout from the SOMA Architecture Plan document (Section 4,
"Screen: Dashboard"). Show:

1. **Agent Health** — Status (running/stopped), uptime, error count (24h)
2. **Cost Tracker** — Today, this week, this month, by provider
3. **Tool Usage (24h)** — Bar chart of tool call counts, success rate
4. **Connections** — Fleet status (offline for now), memory stats
5. **Memory** — Entry count, storage used

Use simple Compose layouts (Column, Row, Card). No charting library needed
for MVP — use Text-based bar representations:
```kotlin
// Simple text bar: "web_search  ████████  23"
Text(
    text = buildString {
        append(toolName.padEnd(16))
        append("█".repeat((count * 8 / maxCount).coerceAtLeast(1)))
        append("  $count")
    },
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    color = SomaAccentGreen
)
```

### Navigation

Add Dashboard as a tab or menu item. Options:
- Bottom navigation bar with 3 tabs: Chat, Dashboard, Config
- OR: Drawer/hamburger menu
- OR: Top bar overflow menu → "Dashboard"

Pick whichever is simplest. A top-bar menu item is fine for MVP.

---

## Task 5d: Agent Configuration Screen

Create file: `ui/config/AgentConfigScreen.kt`

### Agent Profiles

Create Room entity: `AgentProfile`
```kotlin
@Entity(tableName = "agent_profiles")
data class AgentProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,             // "Default", "Coding", "Research"
    val systemPrompt: String,
    val preferredModelId: String?, // null = use current
    val temperature: Float = 0.7f,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Config Screen Layout:

1. **Profile selector** — horizontal chips at the top, tap to switch
2. **System Prompt** — editable text area showing the current system prompt
3. **Preferred Model** — dropdown to set a default model for this profile
4. **Temperature** — slider from 0.0 to 1.0
5. **Tool Permissions** — list of tools with toggles (Enabled/Disabled)
6. **"+ New Profile"** button

### Slash Command

`/agent` — opens profile selector popup (like /model)

### Wire Into ChatAgent

When a profile is active:
- System prompt comes from the profile, not the hardcoded default
- If profile has a preferred model, auto-switch to it
- Disabled tools are excluded from the tool definitions sent to the API

---

## Task 5e: Tool Call Visualization in Chat

Modify `MessageBubble.kt` or create `ToolCallCard.kt`:

When a tool is called, show it in the chat as a card:
```
┌ ⚙ open_app ──────────── 0.2s ┐
│ package_name: com.android.chrome │
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│ ✓ Opened com.android.chrome      │
└──────────────────────────────────┘
```

Use a Card composable with:
- SomaAccentPurple left border (use `Modifier.drawBehind` or `Border`)
- Tool name in header, duration on the right
- Args section in monospace
- Result with ✓ (green) or ✗ (red)
- Collapsible (tap header to expand/collapse)

This replaces the current plain system messages for tool calls.

---

## Testing

1. Dashboard shows real data — cost, tool usage, memory count
2. Agent profiles can be created, edited, switched
3. /agent command switches profiles
4. System prompt changes affect agent behavior
5. Tool calls show as styled cards in chat, not plain text
6. Cost tracking shows per-model breakdown
