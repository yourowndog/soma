# Project Codename: SOMA
## An OpenClaw-Class Agentic AI Assistant, Native to Android

*Architecture Plan & UI Specification — v1.0*
*Author: Claude (Opus) for Sam*
*Date: March 18, 2026*

---

## 1. Vision Statement

SOMA is an Android-native agentic AI assistant inspired by OpenClaw. It provides everything OpenClaw offers desktop users — agentic tool use, MCP integration, agent configuration, dashboards, memory, sub-agents, self-healing — plus capabilities that only a native Android app can deliver: direct intent/activity control, accessibility-driven app automation, voice-first interaction, and deep OS integration.

SOMA is not an OpenClaw port. It is not a Termux wrapper. It is what OpenClaw would be if it was designed from day one for someone who lives on their phone.

### Name Rationale
SOMA (from TechSoma.ai) — means "body" in Greek. The AI gets a body: your phone, your fleet, your life. Also a nod to the somatic — felt, physical, embodied intelligence rather than abstract cloud computation.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    SOMA Android App                   │
├─────────────┬───────────────┬───────────────────────┤
│  UI Layer   │  Agent Core   │  Platform Services     │
│             │               │                        │
│ Compose UI  │ Koog Engine   │ Foreground Service     │
│ Chat View   │ Agent Loop    │ Accessibility Service  │
│ Dashboard   │ Tool Registry │ Notification Listener  │
│ Settings    │ Memory Mgr    │ Voice Pipeline         │
│ Agent Cfg   │ Provider Mgr  │ Intent Executor        │
│             │ MCP Client    │ Fleet Connector        │
└─────────────┴───────────────┴───────────────────────┘
         │              │                │
    ┌────▼────┐   ┌────▼────┐     ┌────▼────┐
    │ Room DB │   │ LLM APIs│     │ MCP     │
    │ SQLite  │   │ Claude  │     │ Servers │
    │ Memory  │   │ Gemini  │     │ (remote)│
    │ Sessions│   │ OpenRtr │     │         │
    │ Config  │   │ Ollama  │     │         │
    └─────────┘   └─────────┘     └─────────┘
```

### Core Dependencies
- **Koog** — Agent framework (loop, tools, MCP, provider switching, history compression, persistence)
- **Jetpack Compose + Material 3** — UI
- **Room** — Local database (sessions, memory, config)
- **Ktor Client** — HTTP for API calls and fleet communication
- **Kotlin Coroutines + Flow** — Async/streaming
- **Porcupine** — Wake word detection (later phase)
- **Whisper (ONNX/TFLite) or Google Speech API** — STT (later phase)

---

## 3. Phased Roadmap

### Phase 0: Proof of Life (Target: 1 week)
**Goal:** Koog + Compose + Claude talking on your S25 Ultra.

**Deliverables:**
- New Android Studio project with Koog dependency
- Single Activity, Compose-based chat screen
- Anthropic Claude wired as the provider
- Streaming responses rendering in real-time
- Basic markdown rendering in message bubbles (bold, italic, code blocks)
- Messages persist in Room DB (survive app restart)

**Architecture decisions locked in this phase:**
- Project structure (see Section 6)
- Koog initialization pattern
- ViewModel → Agent → UI data flow
- Room schema v1

**NOT in this phase:** Tools, model switching, settings, anything beyond "type message, get response, see it stream in."

**Why this is first:** If Koog doesn't work well on Android, or streaming feels bad, or there's a dependency hell issue, you find out in week 1 — not week 8.

---

### Phase 1: Tools + Model Switching (Target: 2 weeks after Phase 0)
**Goal:** The agent can DO things. You can switch models.

**Deliverables:**
- `/model` slash command — bottom sheet listing configured providers
- Providers: Anthropic (Claude Sonnet, Opus), Google Gemini (Flash, Pro), OpenRouter
- 6 hardcoded tools:
  - `clipboard_read` / `clipboard_write`
  - `open_app` (by package name)
  - `web_search` (via a search API — Tavily, SerpAPI, or similar)
  - `read_file` / `write_file` (app-scoped storage)
  - `shell_exec` (via Runtime.getRuntime().exec())
  - `device_info` (battery, connectivity, screen brightness, etc.)
- Tool call visualization in chat (show tool name, args, result in a collapsible card)
- Basic error handling (network failures, API errors shown in chat)

**Architecture decisions locked in this phase:**
- Tool registration pattern (how new tools are added)
- Provider switching mechanism (how /model works with Koog's executor swapping)
- Slash command parsing system

---

### Phase 2: Android Deep Integration (Target: 2 weeks after Phase 1)
**Goal:** The agent controls your phone natively.

**Deliverables:**
- Intent/Activity launcher tool — agent can open any app, any activity, any settings panel
- System settings tools: brightness, volume, DND, WiFi, Bluetooth, flashlight
- Notification tools: read recent notifications, post a notification
- SMS tool: send/read messages (with permission)
- Calendar tool: read/create events (Google Calendar API via OAuth)
- Contact lookup tool
- Foreground service — agent persists in background with notification
- Permission request flow — clean UI for requesting needed permissions on first use

**Architecture decisions locked in this phase:**
- Permission management pattern
- Android service ↔ Koog agent lifecycle
- OAuth token storage and refresh pattern

---

### Phase 3: MCP + Session Management (Target: 2 weeks after Phase 2)
**Goal:** Connect to external MCP servers. Manage multiple conversations.

**Deliverables:**
- MCP client via Koog's built-in MCP support
- Connect to remote MCP servers over HTTP/SSE
- Test with: filesystem MCP server (on fleet), git MCP server, web search MCP server
- MCP server configuration UI (add/remove/test servers)
- Session management:
  - List past conversations
  - Resume any past conversation
  - Delete conversations
  - Session titles (auto-generated or manual)
- Conversation export (JSON, markdown)

**Architecture decisions locked in this phase:**
- MCP server discovery and connection management
- Session storage schema in Room
- How MCP tools merge with hardcoded tools in the tool registry

---

### Phase 4: Memory System (Target: 3 weeks after Phase 3)
**Goal:** The agent remembers across conversations.

**Three-tier memory architecture:**

**Tier 1 — Always Available (on-device):**
- Room database with SQLite FTS5 (full-text search)
- Agent can explicitly save facts: "Remember that Sam's fleet IP is 192.168.1.100"
- Agent can query memory: "What do I know about Sam's projects?"
- Keyword search via FTS5 (BM25 ranking built into SQLite)
- This is the baseline that always works, even offline

**Tier 2 — When Fleet Available (local network):**
- QMD-inspired hybrid search running on Titan
- Bun + node-llama-cpp for local embeddings
- Vector similarity search + BM25 keyword search + LLM re-ranking
- GGUF embedding model (e.g., nomic-embed-text or similar)
- Cron job to auto-embed new documents/logs
- Connected via HTTP API when phone and fleet are on same network

**Tier 3 — Cloud Fallback:**
- Mem0 integration (you have a free subscription)
- Used when fleet is offline but richer memory than Tier 1 is needed
- Opt-in per conversation (privacy control)

**Architecture decisions locked in this phase:**
- Memory query routing (which tier to hit, in what order)
- Memory storage format
- How the agent decides what to remember vs. what to forget

---

### Phase 5: Dashboard + Agent Configuration (Target: 3 weeks after Phase 4)
**Goal:** The OpenClaw-style exposed internals.

**Deliverables:**
- Dashboard view showing:
  - Agent health status (last heartbeat, error count, uptime)
  - Token usage and estimated cost (per session, per day, cumulative)
  - Tool usage stats (which tools called most, success/failure rates)
  - Memory stats (entries count, last sync, storage used)
  - Active MCP connections and their status
  - Fleet connection status
- Agent configuration screen:
  - System prompt editor (SOUL.md equivalent)
  - Tool enable/disable toggles
  - Model preferences per task type
  - Memory tier preferences
  - Auto-approve vs. confirm tool calls
- Multiple agent profiles (e.g., "Coding" agent with code-focused prompt, "Assistant" agent for daily tasks, "Research" agent for deep dives)
- Profile switching via `/agent` command
- Heartbeat system — agent pings itself on a schedule, logs health, alerts on failures
- Self-healing — if a tool call fails, agent automatically tries alternative approaches before reporting failure

---

### Phase 6: Voice Integration (Target: 3 weeks after Phase 5)
**Goal:** Hands-free operation while driving.

**Deliverables:**
- Porcupine wake word detection (always listening via foreground service)
- STT: Google's on-device speech recognition (fastest path) or Whisper ONNX
- Streaming STT — agent starts processing before you finish speaking
- TTS for agent responses (Google TTS initially, upgrade to Piper/Coqui for natural sound)
- Voice mode UI — large waveform visualization, minimal text
- Conversation mode — after agent responds, it listens for your next command without requiring wake word again (configurable timeout)
- Driving mode — extra-large UI elements, voice-only by default

---

### Phase 7: Fleet Integration (Target: 3 weeks after Phase 6)
**Goal:** Your home cluster becomes an extension of the agent.

**Deliverables:**
- Ktor server running on Titan as a Koog agent service
- Phone auto-discovers fleet via mDNS when on same network
- Offload inference to fleet (local Ollama models)
- State sync — continue a phone conversation on fleet and vice versa
- Fleet management tools:
  - SSH command execution on any fleet node
  - Service status checks
  - Start/stop fleet services
  - Disk/RAM/CPU monitoring
- QMD memory backend running on fleet (see Phase 4 Tier 2)

---

### Phase 8: Smart Routing + Polish (Ongoing)
**Goal:** The agent gets smarter about resource usage.

**Deliverables:**
- Rule-based model router:
  - Simple Q&A → Gemini Flash (cheapest)
  - Code generation → Claude Sonnet
  - Complex reasoning → Claude Opus (or best available)
  - Long context → Gemini Pro (1M context)
  - Offline/private → local Ollama model on fleet
- Cost budget system — set daily/monthly API spend limits, router respects them
- `/cost` command — show current spend, projected monthly cost
- Eventually: LLM-powered meta-router (Gemini Flash classifies task complexity and picks model)
- Sub-agent support — main agent can spawn focused sub-agents for specific tasks
- Mission control view — see all active agents/sub-agents, their tasks, progress

---

## 4. UI Specification

### Design Philosophy
Inspired by OpenCode's terminal-aesthetic chat interface. Dark theme, monospace for code, proportional for prose. Clean, information-dense, no wasted space. The UI should feel like a power tool, not a consumer chatbot.

### Color Palette
```
Background:         #0D1117 (GitHub dark)
Surface:            #161B22
Surface Elevated:   #1C2128
Border:             #30363D
Text Primary:       #E6EDF3
Text Secondary:     #8B949E
Text Muted:         #484F58
Accent Blue:        #58A6FF
Accent Green:       #3FB950  (success, tool results)
Accent Yellow:      #D29922  (warnings, pending)
Accent Red:         #F85149  (errors)
Accent Purple:      #BC8CFF  (agent actions, tool calls)
Code Background:    #0D1117
Code Text:          #E6EDF3
User Message BG:    #1C2128
Agent Message BG:   transparent (just text on #0D1117)
```

### Typography
```
Prose:          Inter or system sans-serif, 14sp
Code Inline:    JetBrains Mono, 13sp, on Code Background with 4dp padding
Code Block:     JetBrains Mono, 12sp, full-width card with syntax highlighting
Headers:        Inter Bold, 16sp
Tool Labels:    JetBrains Mono, 11sp, Accent Purple
Timestamps:     Inter, 11sp, Text Muted
Slash Commands: JetBrains Mono, 13sp, Accent Blue
```

### Screen: Main Chat

```
┌──────────────────────────────────────┐
│ ⚡ SOMA          claude-sonnet    ⋮  │  ← Top bar: app name, active model, overflow menu
│──────────────────────────────────────│
│                                      │
│ You                           12:04p │  ← Right-aligned user label + timestamp
│ ┌──────────────────────────────────┐ │
│ │ Can you check my battery level   │ │  ← User message: Surface Elevated bg, rounded
│ │ and tell me if I should plug in? │ │
│ └──────────────────────────────────┘ │
│                                      │
│ SOMA                          12:04p │  ← Left-aligned agent label + timestamp
│                                      │
│ ┌ ⚙ device_info ──────────────────┐ │  ← Tool call card: purple left border
│ │ battery_level: 34%               │ │     Collapsible, starts collapsed after first view
│ │ charging: false                  │ │     Monospace, Accent Green text
│ │ temperature: 31°C                │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Your battery is at 34% and you're    │  ← Agent prose: no background, just text
│ not charging. I'd recommend          │
│ plugging in soon, especially if      │
│ you'll be using the phone heavily.   │
│                                      │
│ You                           12:05p │
│ ┌──────────────────────────────────┐ │
│ │ /model                           │ │
│ └──────────────────────────────────┘ │
│                                      │
│──────────────────────────────────────│
│ ┌──────────────────────────────────┐ │  ← Input field: Surface bg, rounded
│ │ Message SOMA...             ⎆  ▶ │ │  ← Mic icon (⎆) + Send icon (▶)
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

### Component: Tool Call Card
```
┌ ⚙ tool_name ─────────── 0.8s  ▼ ┐   ← Purple left border (3dp)
│                                   │   ← Header: tool icon + name + duration + collapse toggle
│   parameter: "value"              │   ← Args section: monospace, Text Secondary
│   another_param: 42               │
│ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─  │   ← Dashed divider
│   ✓ result: "success data here"   │   ← Result: monospace, Accent Green (or Red if error)
│                                   │
└───────────────────────────────────┘

States:
- Running:  Yellow left border, spinning icon, "Running..." label
- Success:  Green left border, checkmark, result shown
- Error:    Red left border, X icon, error message shown
- Collapsed: Just header row with tool name + status icon
```

### Component: Code Block
```
┌─ kotlin ──────────────────── 📋 ┐   ← Language label + copy button
│                                  │   ← Syntax highlighted with standard dark theme
│  fun main() {                    │   ← JetBrains Mono, 12sp
│      println("Hello SOMA")      │   ← Scrollable horizontally if needed
│  }                               │   ← Max height: 300dp, scrollable vertically
│                                  │
└──────────────────────────────────┘
```

### Component: /model Selector
When user types `/model`, a bottom sheet slides up:

```
┌──────────────────────────────────────┐
│  Select Model                     ✕  │
│──────────────────────────────────────│
│                                      │
│  ANTHROPIC                           │  ← Provider group header
│  ┌────────────────────────────────┐  │
│  │ ● Claude Sonnet 4    ✓ active │  │  ← Radio button, checkmark on active
│  │ ○ Claude Opus 4              │  │
│  └────────────────────────────────┘  │
│                                      │
│  GOOGLE                              │
│  ┌────────────────────────────────┐  │
│  │ ○ Gemini 2.5 Flash           │  │
│  │ ○ Gemini 2.5 Pro             │  │
│  └────────────────────────────────┘  │
│                                      │
│  OPENROUTER                          │
│  ┌────────────────────────────────┐  │
│  │ ○ Browse models...            │  │  ← Opens full model list
│  └────────────────────────────────┘  │
│                                      │
│  LOCAL (Fleet)                       │
│  ┌────────────────────────────────┐  │
│  │ ○ Llama 3.1 8B   ⚠ offline  │  │  ← Status indicator
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### Component: Slash Command Autocomplete
When user types `/`, a popup appears above the input:

```
                    ┌────────────────────────┐
                    │ /model   Switch model   │
                    │ /agent   Switch agent   │
                    │ /tools   List tools     │
                    │ /memory  Query memory   │
                    │ /cost    Show spend     │
                    │ /clear   Clear chat     │
                    │ /export  Export chat     │
                    └────────────────────────┘
┌──────────────────────────────────────┐
│ /mo                            ⎆  ▶ │  ← Input with partial command
└──────────────────────────────────────┘
```

### Screen: Dashboard

```
┌──────────────────────────────────────┐
│ ← Dashboard                      ⋮  │
│──────────────────────────────────────│
│                                      │
│  AGENT HEALTH                        │
│  ┌────────────────────────────────┐  │
│  │ Status: ● Running              │  │  ← Green dot = healthy
│  │ Uptime: 4h 23m                 │  │
│  │ Last heartbeat: 2s ago         │  │
│  │ Errors (24h): 3                │  │
│  └────────────────────────────────┘  │
│                                      │
│  COST TRACKER                        │
│  ┌────────────────────────────────┐  │
│  │ Today:     $0.42               │  │
│  │ This week: $2.18               │  │
│  │ This month: $8.73              │  │
│  │ Budget:    $30/mo  ████░░ 29%  │  │  ← Progress bar
│  │                                │  │
│  │ By provider:                   │  │
│  │   Claude:    $5.21 (60%)       │  │
│  │   Gemini:    $2.89 (33%)       │  │
│  │   OpenRouter: $0.63 (7%)      │  │
│  └────────────────────────────────┘  │
│                                      │
│  TOOL USAGE (24h)                    │
│  ┌────────────────────────────────┐  │
│  │ web_search     ████████  23    │  │
│  │ clipboard      █████     14    │  │
│  │ shell_exec     ████      11    │  │
│  │ open_app       ███        8    │  │
│  │ device_info    ██         5    │  │
│  │                                │  │
│  │ Success rate: 94%              │  │
│  └────────────────────────────────┘  │
│                                      │
│  CONNECTIONS                         │
│  ┌────────────────────────────────┐  │
│  │ Fleet (Titan): ● Connected     │  │
│  │ MCP servers:   3 active        │  │
│  │ Memory tier:   Tier 2 (QMD)   │  │
│  │ Mem0:          ○ Standby       │  │
│  └────────────────────────────────┘  │
│                                      │
│  MEMORY                              │
│  ┌────────────────────────────────┐  │
│  │ Local entries:  847            │  │
│  │ Vector entries: 2,341 (fleet)  │  │
│  │ Last sync:     12m ago         │  │
│  │ Storage used:  23 MB           │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### Screen: Agent Configuration

```
┌──────────────────────────────────────┐
│ ← Agent: Coding Assistant         ⋮  │
│──────────────────────────────────────│
│                                      │
│  PROFILES                            │
│  ┌────────┐ ┌────────┐ ┌────────┐  │
│  │Coding ✓│ │  Daily │ │Research│  │  ← Horizontal chips, tap to switch
│  └────────┘ └────────┘ └────────┘  │
│  + New Profile                       │
│                                      │
│  SYSTEM PROMPT                       │
│  ┌────────────────────────────────┐  │
│  │ You are SOMA, a coding-focused │  │  ← Editable text area, monospace
│  │ AI assistant running on Sam's  │  │     Expandable
│  │ Samsung Galaxy S25 Ultra.      │  │
│  │ You have access to tools for   │  │
│  │ file manipulation, shell       │  │
│  │ commands, and...               │  │
│  │                          Edit  │  │
│  └────────────────────────────────┘  │
│                                      │
│  PREFERRED MODEL                     │
│  Claude Sonnet 4                  ▼  │
│                                      │
│  TOOL PERMISSIONS                    │
│  ┌────────────────────────────────┐  │
│  │ clipboard_read      Auto   ▼  │  │  ← Per-tool: Auto / Ask / Disabled
│  │ clipboard_write     Ask    ▼  │  │
│  │ shell_exec          Ask    ▼  │  │
│  │ open_app            Auto   ▼  │  │
│  │ send_sms            Ask    ▼  │  │
│  │ accessibility_tap   Ask    ▼  │  │
│  └────────────────────────────────┘  │
│                                      │
│  MEMORY                              │
│  ┌────────────────────────────────┐  │
│  │ Cross-session memory   [ON ]   │  │
│  │ Use Mem0 cloud         [OFF]   │  │
│  │ Auto-remember facts    [ON ]   │  │
│  └────────────────────────────────┘  │
│                                      │
│  BEHAVIOR                            │
│  ┌────────────────────────────────┐  │
│  │ Max context tokens:   128000   │  │
│  │ Temperature:          0.7      │  │
│  │ Auto-approve tools:   [OFF]    │  │
│  │ Stream responses:     [ON ]    │  │
│  │ Self-heal on error:   [ON ]    │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### Screen: Session List

```
┌──────────────────────────────────────┐
│ ← Sessions                  🔍   ⋮  │
│──────────────────────────────────────│
│                                      │
│  Today                               │
│  ┌────────────────────────────────┐  │
│  │ Fix Gradle build error          │  │  ← Auto-generated title from first message
│  │ Claude Sonnet · 23 messages     │  │  ← Model used + message count
│  │ 2:34 PM                         │  │
│  └────────────────────────────────┘  │
│  ┌────────────────────────────────┐  │
│  │ Plan weekend project            │  │
│  │ Gemini Flash · 8 messages       │  │
│  │ 11:15 AM                        │  │
│  └────────────────────────────────┘  │
│                                      │
│  Yesterday                           │
│  ┌────────────────────────────────┐  │
│  │ Debug fleet SSH config          │  │
│  │ Claude Sonnet · 45 messages     │  │
│  │ 9:22 PM                         │  │
│  └────────────────────────────────┘  │
│                                      │
│  Mar 15                              │
│  ┌────────────────────────────────┐  │
│  │ Research Koog framework         │  │
│  │ Claude Opus · 12 messages       │  │
│  │ 7:45 PM                         │  │
│  └────────────────────────────────┘  │
│                                      │
└──────────────────────────────────────┘
```

### Navigation Structure
```
Bottom Navigation (3 tabs):
┌──────────┬──────────┬──────────┐
│  💬 Chat │ 📊 Dash │ ⚙ Config │
└──────────┴──────────┴──────────┘

Chat tab:     Active conversation (default)
              Swipe right or hamburger → Session list
Dashboard:    Stats, health, connections
Config:       Agent profiles, system prompt, tools, memory, providers
```

### Streaming Behavior
- Agent responses stream token-by-token
- During streaming: input field is disabled, a "Stop" button replaces "Send"
- Tool calls appear as a card BEFORE the text response, with a spinner while executing
- Markdown renders progressively as tokens arrive (bold, italic, inline code)
- Code blocks render once the closing ``` is received (buffer until complete)
- Scroll auto-follows new content unless user has scrolled up

### Dark Theme Only (MVP)
Light theme is out of scope. This is a power tool for someone who stares at terminals. Every surface is dark. Text is light. Accents are for status and emphasis only.

---

## 5. Technical Decisions

### Why Koog (Not From Scratch)
- Agent loop with tool calling is solved
- MCP client is built in
- Provider switching mid-conversation is built in
- History compression is built in
- Persistence/checkpointing is built in
- Android is an official target
- Apache 2.0 license — use it however you want

### Why Fork ASI (As Reference, Not As Base)
- ASI proves the Koog + Android Service + Accessibility pattern works
- Study their foreground service lifecycle, accessibility wiring, Room schema
- DON'T inherit their UI decisions — build clean Compose from this spec
- DON'T inherit their Gemma-specific architecture — you're multi-model

### Why Not Stay in Termux
- Termux lives in a sandbox — can't fire intents, can't access accessibility, can't do foreground services
- OpenClaw's Android APK hits these walls immediately (the bash error you saw)
- A native app gets all Android permissions directly
- Termux can still be a tool the agent calls (shell_exec), but the agent doesn't LIVE there

### Database: Room (SQLite)
- Sessions table: id, title, created_at, updated_at, model, agent_profile
- Messages table: session_id, role, content, tool_calls (JSON), timestamp
- Memory table: id, key, value, embedding (blob, for local vector search later), tags, created_at
- Config table: key-value store for agent profiles, provider settings, etc.
- Tool_logs table: session_id, tool_name, args, result, duration_ms, success, timestamp

### State Management
- UI state: Compose ViewModel with StateFlow
- Agent state: Koog manages internally, exposed via callbacks/flows
- Persistent state: Room DB
- Config state: DataStore (for simple prefs) + Room (for complex config)

---

## 6. Project Structure

```
soma/
├── app/
│   ├── src/main/
│   │   ├── java/io/brokentooth/soma/
│   │   │   ├── SomaApplication.kt          # App entry, DI setup
│   │   │   ├── MainActivity.kt              # Single activity
│   │   │   │
│   │   │   ├── agent/
│   │   │   │   ├── SomaAgent.kt             # Koog agent wrapper
│   │   │   │   ├── AgentService.kt           # Foreground service hosting the agent
│   │   │   │   ├── ProviderManager.kt        # Multi-model provider switching
│   │   │   │   ├── MemoryManager.kt          # Three-tier memory routing
│   │   │   │   └── HealthMonitor.kt          # Heartbeat, self-healing
│   │   │   │
│   │   │   ├── tools/
│   │   │   │   ├── ToolRegistry.kt           # Central tool registration
│   │   │   │   ├── clipboard/
│   │   │   │   │   └── ClipboardTools.kt
│   │   │   │   ├── device/
│   │   │   │   │   └── DeviceTools.kt        # Battery, brightness, volume, etc.
│   │   │   │   ├── intent/
│   │   │   │   │   └── IntentTools.kt        # Launch apps, activities, settings
│   │   │   │   ├── system/
│   │   │   │   │   └── SystemTools.kt        # WiFi, BT, DND, flashlight
│   │   │   │   ├── communication/
│   │   │   │   │   └── SmsTools.kt
│   │   │   │   ├── shell/
│   │   │   │   │   └── ShellTools.kt         # Runtime.exec
│   │   │   │   ├── files/
│   │   │   │   │   └── FileTools.kt
│   │   │   │   ├── web/
│   │   │   │   │   └── WebSearchTools.kt
│   │   │   │   └── accessibility/
│   │   │   │       └── AccessibilityTools.kt  # Tier 3: screen read/tap/navigate
│   │   │   │
│   │   │   ├── mcp/
│   │   │   │   ├── McpConnectionManager.kt   # Connect to remote MCP servers
│   │   │   │   └── McpToolBridge.kt          # Bridge MCP tools into Koog registry
│   │   │   │
│   │   │   ├── ui/
│   │   │   │   ├── theme/
│   │   │   │   │   ├── Theme.kt              # SOMA dark theme
│   │   │   │   │   ├── Colors.kt
│   │   │   │   │   └── Typography.kt
│   │   │   │   ├── chat/
│   │   │   │   │   ├── ChatScreen.kt         # Main chat view
│   │   │   │   │   ├── MessageBubble.kt      # User/agent message rendering
│   │   │   │   │   ├── ToolCallCard.kt       # Tool execution visualization
│   │   │   │   │   ├── CodeBlock.kt          # Syntax-highlighted code
│   │   │   │   │   ├── ChatInput.kt          # Text field + mic + send
│   │   │   │   │   └── SlashCommandPopup.kt  # Autocomplete for /commands
│   │   │   │   ├── dashboard/
│   │   │   │   │   └── DashboardScreen.kt
│   │   │   │   ├── config/
│   │   │   │   │   ├── AgentConfigScreen.kt
│   │   │   │   │   └── ProviderConfigScreen.kt
│   │   │   │   ├── sessions/
│   │   │   │   │   └── SessionListScreen.kt
│   │   │   │   ├── model/
│   │   │   │   │   └── ModelSelectorSheet.kt  # /model bottom sheet
│   │   │   │   └── navigation/
│   │   │   │       └── SomaNavigation.kt
│   │   │   │
│   │   │   ├── data/
│   │   │   │   ├── db/
│   │   │   │   │   ├── SomaDatabase.kt       # Room database
│   │   │   │   │   ├── SessionDao.kt
│   │   │   │   │   ├── MessageDao.kt
│   │   │   │   │   ├── MemoryDao.kt
│   │   │   │   │   └── ToolLogDao.kt
│   │   │   │   ├── model/
│   │   │   │   │   ├── Session.kt
│   │   │   │   │   ├── Message.kt
│   │   │   │   │   ├── MemoryEntry.kt
│   │   │   │   │   └── ToolLog.kt
│   │   │   │   └── preferences/
│   │   │   │       └── SomaPreferences.kt    # DataStore wrapper
│   │   │   │
│   │   │   ├── voice/                         # Phase 6
│   │   │   │   ├── WakeWordService.kt
│   │   │   │   ├── SpeechRecognizer.kt
│   │   │   │   └── TextToSpeechManager.kt
│   │   │   │
│   │   │   └── fleet/                         # Phase 7
│   │   │       ├── FleetDiscovery.kt          # mDNS service discovery
│   │   │       ├── FleetClient.kt             # Ktor client for fleet API
│   │   │       └── StateSync.kt               # Conversation state transfer
│   │   │
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   │
│   └── build.gradle.kts
│
├── fleet-server/                              # Phase 7 — runs on Titan
│   ├── src/main/kotlin/
│   │   ├── Application.kt                    # Ktor server entry
│   │   ├── AgentRoutes.kt                    # HTTP API for agent interaction
│   │   ├── StateRoutes.kt                    # State sync endpoints
│   │   └── ModelRoutes.kt                    # Local Ollama proxy
│   └── build.gradle.kts
│
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
└── README.md
```

---

## 7. Instructions for Claude Code

When handing this document to Claude Code (anti-gravity) for implementation, use these instructions:

### Phase 0 Prompt:
```
Read the SOMA architecture plan (this document). We are starting Phase 0.

Create a new Android project targeting API 26+ with:
- Kotlin, Jetpack Compose, Material 3
- Koog dependency (latest from Maven Central: ai.koog)
- Room database
- Ktor client for HTTP
- Single Activity architecture

Implement ONLY:
1. The project structure from Section 6 (create the package directories)
2. The dark theme from Section 4 (Colors.kt, Typography.kt, Theme.kt)
3. A basic ChatScreen with the layout shown in the UI spec
4. A ChatViewModel that creates a Koog AIAgent with Anthropic provider
5. Streaming response rendering
6. Room database with Sessions and Messages tables
7. Messages persist across app restarts

The Anthropic API key should be read from local.properties or BuildConfig.
Do NOT implement tools, model switching, or any Phase 1+ features.
```

### Phase 1 Prompt:
```
Read the SOMA architecture plan. We are starting Phase 1.

The Phase 0 chat interface is working. Now add:
1. ToolRegistry.kt — central place to register Koog tools
2. These 6 tools: clipboard_read, clipboard_write, open_app, web_search,
   read_file, shell_exec, device_info
3. ToolCallCard.kt component matching the UI spec (purple border,
   collapsible, shows args and result)
4. /model slash command:
   - Detect when user types "/" in ChatInput
   - Show SlashCommandPopup with available commands
   - /model opens ModelSelectorSheet (bottom sheet)
   - Selecting a model swaps the Koog prompt executor
5. Add Gemini and OpenRouter provider configurations
6. Provider configs stored in Room (API keys in encrypted SharedPreferences)
```

---

## 8. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Koog Android target is immature/buggy | Medium | High | Phase 0 validates this immediately. Fallback: raw Anthropic SDK + custom agent loop (500 lines) |
| Koog doesn't support all providers equally on Android | Medium | Medium | Test provider switching in Phase 0. Fallback: write thin provider wrappers |
| History compression loses important context | Low | Medium | Test with long conversations in Phase 1. Tune compression settings. |
| Foreground service killed by Samsung's aggressive battery optimization | High | Medium | Use proper foreground service with notification. Add to battery optimization whitelist. Test on actual S25 Ultra. |
| MCP servers can't connect from Android (transport issues) | Medium | Medium | Test in Phase 3. HTTP/SSE should work. stdio won't (that's for local processes). |
| Mobile hotspot instability causes mid-stream failures | High | Medium | Implement retry logic from day 1. Cache partial responses. Graceful degradation UI. |
| API costs spiral during development | Low | Medium | Use Gemini Flash for testing. Track costs from Phase 1. Set budget alerts. |
| Project scope creep leads to nothing shipping | Medium | High | STRICT phase discipline. Each phase ships something usable. No jumping ahead. |

---

## 9. Success Criteria

**Phase 0 is successful when:** You can type a message, see Claude's response stream in real-time with proper formatting, close the app, reopen it, and see the conversation still there.

**Phase 1 is successful when:** You can ask the agent "what's my battery level?" and it calls the device_info tool, shows the tool call card, and responds with the answer. You can type /model and switch to Gemini mid-conversation.

**Phase 2 is successful when:** You can say "open my camera" and the camera opens. "Set brightness to 50%" and it changes. "Send a text to Kiry saying I'm on my way" and it sends.

**The project is successful when:** You wake up your phone with a voice command, ask your AI assistant to check your calendar, read your unread notifications, and draft a response — all without touching the screen while driving your truck.

---

*This document is the single source of truth for the SOMA project. Update it as decisions are made and phases are completed.*
