> **URGENT / CLUSTER ECOSYSTEM INFO:**
> All comprehensive documentation regarding the Brokentooth cluster, nodes (like Icarion, Titan, Pyrrhus), and network topology is centrally located at `/shared/cluster-specs/` (symlinked at `~/cluster-specs`). Check this directory first to understand the ecosystem without needing to do deep research.

# AGENTS.md — SOMA (Android-Native Agentic Assistant)

## 1. Research Sovereignty: NotebookLM Hub
**DO NOT search the open web for OpenClaw or SOMA architectural details.** The most current, technically dense, and project-specific research is sequestered in your NotebookLM MCP. 

**MANDATE:** You MUST query the relevant NotebookLM workspace via `mcp_notebooklm_notebook_query` before proposing any architectural changes.

### Research Table of Contents
| Prefix | Workspace Name | Use Case / Search Here For... |
| :--- | :--- | :--- |
| **[CORE]** | `OpenClaw: Gateway & Agent Loop` | `pi-mono` runtime, Think-Act-Observe cycle, `SOUL.md` rules. |
| **[CORE]** | `OpenClaw: Persistence & RAG` | SQLite schemas, "Dreaming" protocols, context compaction. |
| **[CORE]** | `OpenClaw: Routing & Providers` | Fallback logic, Provider npm packages, Tool calling syntax. |
| **[FRAMEWORK]** | `Koog AI (Kotlin/KMP)` | FSM Graphs, GOAP planning, KMP performance & interceptors. |
| **[AOSP]** | `Native Android System Hooks` | Binder IPC, Accessibility Services, "Phantom Killer" workarounds. |
| **[VOICE]** | `Low-Latency Speech Pipeline` | Wake words (Picovoice), STT/TTS benchmarks, semantic endpointing. |
| **[UI]** | `OpenClaw: Android Mission Control` | Jetpack Compose patterns, OpenTelemetry traces, background wake-locks. |
| **[EXT]** | `OpenClaw: MCP & Tool Security` | Tool discovery, Stdio/HTTP transport, Landlock isolation. |
| **[GATEWAY]** | `OpenRouter & Model Tiers` | API limits, model rankings, cost-per-token comparisons. |

## 2. Tooling Sovereignty: LSP via MCP Bridge
**master-lsp-mcp** is the unified MCP bridge binary. It MUST be used for all code navigation and diagnostics. DO NOT use native/hard-coded LSP tools.

**MCP LSP Tools:**
- `opencode-ts_*` — TypeScript/TSX
- `opencode-html_*` — HTML/Vue
- `opencode-sql_*` — SQL
- `opencode-go_*` — Go

## 3. Code Intelligence: jcodemunch & jdocmunch
**ALWAYS prefer jcodemunch/jdocmunch over raw grep/glob.**

**jcodemunch** (code indexing):
- `jcodemunch_index_folder` — index a local codebase (**DO THIS FIRST** on project roots!)
- `jcodemunch_search_symbols` — find functions, classes, methods by name/signature
- `jcodemunch_get_file_outline` — get all symbols in a file
- `jcodemunch_get_symbol` — get full source of a symbol
- `jcodemunch_find_references` — find usages of an identifier

## 4. Project Vision & Architecture
SOMA leverages the **Koog Agent Framework** to replace standalone automation apps by centralizing wake-word detection, screen reading, and device control into a persistent foreground service (`SomaService`).

### Key Knowledge Base
- **`SomaArchitecture.md`**: Definitive UI and architectural specification.
- **`SOMA_Master_Index.md`**: Tracks the status of each build phase.
- **`Soma prompts/`**: Specific implementation guides for each phase. **Read the relevant phase prompt before starting work.**

## 5. Development Conventions
- **Asynchrony:** Use Kotlin Coroutines and Flow for all operations.
- **Permissions:** Be mindful of Android runtime permissions (RECORD_AUDIO, ACCESSIBILITY).
- **Tools:** Register all new capabilities in `ToolRegistry.kt`. Prefer native function calling over string parsing.
- **Aesthetic:** Dark theme, `#0D1117` background, JetBrains Mono for code.

## 6. Sub-Agent Protocol
When tasking sub-agents, enforce the use of the specific NotebookLM workspaces listed above. Every handoff MUST include a reference to the specific [PREFIX] notebook consulted.
