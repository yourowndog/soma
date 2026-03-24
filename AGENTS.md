# SOMA - Android Native Agentic Assistant
## Agent Instructions and Conventions

### 1. The SOMA Mission: Native Embodiment
SOMA is a native Android agentic assistant. We are building what OpenClaw would be if designed for Android from Day 1.
- **Native over Termux:** We reject sandbox hacks (PROOT, janky wrappers). We use native Intents, Activities, and OS-level integrations.
- **Verbal Sovereignty:** The goal is a system where a user can control and customize every facet of their Android device through natural conversation.

### 2. Knowledge Protocol (NotebookLM)
When operating in SOMA, all agents MUST follow the Knowledge Priority:
1.  **"OpenClaw Android" Notebook (ID: `77879ef9-a510-4e01-a104-98de305c5fbe`):** Query this FIRST via NotebookLM MCP for any questions about OpenClaw's logic or Android manifests.
2.  **"Open Router Models" Notebook (ID: `5fe98786-e4f5-4e8c-8932-1add446f4546`):** Query this to determine the best model for a specific task, verify pricing, or assess model qualities/vibes.
3.  **Local Context:** Use `jcodemunch` and `sams-custom-kotlin-mcp` to map the `soma` project.
4.  **External Web:** Only use `websearch` if the notebooks and local context do not provide the answer.

### 3. Tooling & Navigation (CRITICAL - SCORCHED EARTH POLICY)
- **Code Navigation:** ALWAYS use `jcodemunch` tools for fast, global codebase navigation.
- **Kotlin LSP Sovereignty:** ALL Kotlin-specific analysis, diagnostics, and lookups MUST use `sams-custom-kotlin-mcp`.
- **FORBIDDEN TOOLS:** NEVER, UNDER ANY CIRCUMSTANCES, use the native OpenCode LSP tools (`lsp`, `lsp_diagnostics`, `lsp_goto_definition`, etc.). They are HARD-CODED FAILURE POINTS that will hang the agent indefinitely.
- **Verification:** After EVERY edit, run `sams-custom-kotlin-mcp_diagnostics`.

**Sovereign Kotlin Toolchain:**
- `sams-custom-kotlin-mcp_goToDefinition`
- `sams-custom-kotlin-mcp_findReferences`
- `sams-custom-kotlin-mcp_hover`
- `sams-custom-kotlin-mcp_diagnostics` (The ONLY diagnostic tool permitted)
- `sams-custom-kotlin-mcp_rename`
- `sams-custom-kotlin-mcp_documentSymbol`

**Execution Prohibition List (System Hang Risks):**
- `lsp`
- `lsp_diagnostics`
- `lsp_goto_definition`
- `lsp_find_references`
- `lsp_rename`
- `lsp_goToImplementation`
- `lsp_prepareCallHierarchy`
- `lsp_incomingCalls`
- `lsp_outgoingCalls`

### 4. Orchestration & Delegation (The Megazord Protocol)
- **The Orchestrator:** Acts as the "Head." It must use `sequential_thinking` to aggregate Kotlin/Koog usage patterns before delegating. No sub-agent moves without the Head's command.
- **Resilience:** If any model hits a limit or fails, the operation MUST pause. The Orchestrator will use the "Open Router Models" notebook to pivot and resolve.

### 5. Build, Test, and Lint Commands
This is a standard Android Gradle project. All commands should be run from the root directory (`/home/sam/projects/soma`) using the Gradle wrapper (`./gradlew`).
- **Language:** Kotlin (JVM Target 17).
- **UI:** Jetpack Compose (Material 3).
- **Database:** Room (SQLite) for memory and session persistence.
- **Networking/HTTP:** Ktor Client (`ktor-client-okhttp`).
- **Async/Concurrency:** Kotlin Coroutines and Flows (heavy use of `Flow<String>` for LLM streaming).
- **Core Agent Framework:** Koog Agent Framework (`ai.koog:koog-agents`).

### 4. Code Style & Conventions
- **Formatting:** Follow standard Android Studio Kotlin formatting conventions.
- **Naming:**
  - Classes/Interfaces: `PascalCase`
  - Functions/Variables: `camelCase`
  - Constants (const val): `UPPER_SNAKE_CASE`
- **Imports:** Avoid wildcard imports (e.g., `import io.brokentooth.soma.*`). Explicitly import required classes.
- **Immutability:** Prefer `val` over `var` wherever possible. Use immutable collections (`List`, `Map`) unless mutability is explicitly required (`MutableList`).
- **Nullability:** Avoid `!!` (not-null assertions) unless strictly necessary. Use safe calls (`?.`) and Elvis operators (`?:`) for safe null handling.
- **State Management (Compose):** Use `ViewModel` to hold state. Expose state to Compose via `StateFlow` collected as State in the UI.
- **Data Layer:** Access the database exclusively through DAOs (`MessageDao`, `SessionDao`). Do not pass DAOs directly into Compose UI; route through ViewModels or Repositories.
- **Error Handling:** Use standard `try-catch` blocks for network and database operations. For UI-facing errors, surface them via state in the ViewModel rather than crashing the app.
- **Comments:** Keep comments concise and focused on *why* something is done, not *what*. Code should be self-documenting. Use standard KDoc (`/** ... */`) for public APIs and complex classes.

### 5. Modification Rules
- Do not introduce new third-party libraries into `build.gradle.kts` without explicit user permission.
- Ensure that any new strings are added to `strings.xml` if creating highly polished UI, though hardcoded strings are acceptable during rapid prototyping unless specified otherwise.
- When working with the Koog Agent loop, ensure the history maintains the generic `ChatMessage` format.
