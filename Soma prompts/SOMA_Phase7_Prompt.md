# SOMA Phase 7: Fleet Integration

## Difficulty: Hard — Sonnet or Opus recommended
## Prerequisite: Phase 4+ complete (sessions, memory working)

---

## Context

Sam has a 3-node Endeavour OS Linux cluster at home:
- Titan: i7-8700K, 32GB RAM, GTX 1060 6GB (PCIe issues), 1TB NVMe + 5.5TB HDD
- Sleeper: i7-7700, 32GB RAM, 256GB NVMe
- Weakling: i7-8700, 16GB RAM, 256GB NVMe
All connected via gigabit Ethernet. No home WiFi — phone connects via hotspot.

The fleet is OFFLINE when Sam is at work. It comes online when he gets home
and his phone hotspot is in range.

Phase 7 makes the fleet an extension of SOMA:
- Run local models on the fleet via Ollama
- Sync conversation state between phone and fleet
- Execute commands on fleet nodes via SSH
- Serve QMD memory backend from the fleet

---

## Task 7a: Fleet Discovery

Create file: `fleet/FleetDiscovery.kt`

When the phone is on the same network as the fleet, auto-discover it.

**Simple approach (no mDNS needed):**
- Store fleet node IPs in app settings (user configures once)
- Periodically ping the stored IPs to check if they're reachable
- Expose a `FleetStatus` StateFlow: Online/Offline per node

```kotlin
package io.brokentooth.soma.fleet

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

data class FleetNode(
    val name: String,
    val ip: String,
    val port: Int = 8080,  // SOMA fleet API port
    val ollamaPort: Int = 11434
)

data class FleetStatus(
    val nodes: Map<String, Boolean> = emptyMap()  // name -> isReachable
) {
    val isAnyOnline get() = nodes.values.any { it }
}

class FleetDiscovery(
    private val nodes: List<FleetNode>,
    private val scope: CoroutineScope
) {
    private val _status = MutableStateFlow(FleetStatus())
    val status: StateFlow<FleetStatus> = _status

    fun startMonitoring(intervalMs: Long = 30_000) {
        scope.launch {
            while (isActive) {
                val results = nodes.associate { node ->
                    node.name to isReachable(node.ip)
                }
                _status.value = FleetStatus(results)
                delay(intervalMs)
            }
        }
    }

    private suspend fun isReachable(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            InetAddress.getByName(ip).isReachable(3000)
        } catch (e: Exception) {
            false
        }
    }
}
```

### Settings UI

Add a fleet configuration screen where user enters:
- Node name, IP address for each fleet node
- Store in SharedPreferences or Room

---

## Task 7b: Ollama Integration (Local Models on Fleet)

Create file: `fleet/OllamaClient.kt`

Ollama's API is also OpenAI-compatible:
- Endpoint: `http://{FLEET_IP}:11434/v1/chat/completions`
- No API key needed (local)
- Same request/response format as OpenRouter

```kotlin
package io.brokentooth.soma.fleet

import io.brokentooth.soma.agent.ChatMessage
import io.brokentooth.soma.agent.LlmProvider
import kotlinx.coroutines.flow.Flow
// ... same SSE streaming pattern as OpenRouterClient
// but pointing to http://{fleetIp}:11434/v1/chat/completions
// and with no Authorization header
```

### Register as a provider

When fleet is online, add local models to the model selector:
- Fetch available models: `GET http://{ip}:11434/api/tags`
- Returns list of installed models with their names and sizes
- Add them to availableModels with provider = "ollama"
- Display as "Llama 3.1 8B (Local)" or similar, marked as FREE

### Model Selector Integration

When fleet comes online:
- Fetch Ollama model list
- Add to model selector under "LOCAL (Fleet)" group
- Show fleet status indicator (green dot = online)

When fleet goes offline:
- Remove local models from selector
- If currently using a local model, show warning and prompt to switch

---

## Task 7c: State Sync

Create file: `fleet/StateSync.kt`

Sync conversation state between phone and fleet so you can start a
conversation on the phone and continue on a terminal on the fleet.

### Minimal approach (file-based):

1. Export session as JSON: messages + metadata
2. When fleet is online, push to fleet via HTTP:
   `POST http://{ip}:8080/soma/sessions/{id}`
3. Pull updated sessions from fleet:
   `GET http://{ip}:8080/soma/sessions`
4. Merge by timestamp — latest message wins

### Fleet-side server

Create a companion document for setting up a simple Ktor server on
the fleet that:
- Receives session data via HTTP
- Stores in a local SQLite database
- Serves sessions back to the phone
- Optionally runs a Koog agent locally (for fleet-side inference)

**This is a separate project** — `soma-fleet-server/` — not part of
the Android app. Include a brief setup guide:

```bash
# On Titan:
git clone https://github.com/yourowndog/soma-fleet
cd soma-fleet
./gradlew run
# Starts HTTP server on port 8080
# Connects to local Ollama on port 11434
```

---

## Task 7d: Fleet Management Tools

Add tools to ToolRegistry that interact with the fleet:

```kotlin
register(ToolDefinition(
    name = "fleet_status",
    description = "Check the status of home fleet nodes (Titan, Sleeper, Weakling)",
    parameters = emptyMap()
)) { _ ->
    fleetDiscovery.status.value.nodes.entries.joinToString("\n") { (name, online) ->
        "$name: ${if (online) "✓ Online" else "✗ Offline"}"
    }
}

register(ToolDefinition(
    name = "fleet_ssh",
    description = "Execute a command on a fleet node via SSH. " +
        "Available nodes: titan, sleeper, weakling. " +
        "Use for: checking disk space, starting services, git operations, etc.",
    parameters = mapOf(
        "node" to ToolParameter("string", "Fleet node name: titan, sleeper, or weakling"),
        "command" to ToolParameter("string", "Shell command to execute")
    )
)) { params ->
    val node = params["node"]?.toString() ?: return@register "Error: no node specified"
    val command = params["command"]?.toString() ?: return@register "Error: no command"
    // Execute via HTTP to fleet server which runs the command
    // DO NOT embed SSH keys in the Android app
    // Instead, the fleet server runs locally and has SSH access to all nodes
    executeOnFleet(node, command)
}
```

**Security note:** The Android app does NOT SSH directly to fleet nodes.
Instead, it sends commands to the fleet HTTP server, which executes them
locally. This avoids storing SSH keys on the phone.

---

## Task 7e: QMD Memory Backend (Fleet)

When fleet is online, upgrade memory from SQLite keyword search to
QMD-style hybrid search running on the fleet.

### Fleet-side QMD service:
- Runs on Titan
- Bun + node-llama-cpp for local embeddings
- BM25 keyword search + vector similarity search + LLM re-ranking
- HTTP API: POST /memory/search, POST /memory/store, GET /memory/all

### Phone-side integration:
- Modify MemoryManager to check fleet status
- If fleet online → use fleet memory API (Tier 2)
- If fleet offline → fall back to local Room SQLite FTS search (Tier 1)
- Optionally: use Mem0 cloud as Tier 3 fallback

This is the most complex task in Phase 7 and can be deferred.
The fleet server setup document should include QMD installation instructions.

---

## Testing

1. Configure fleet IPs in settings
2. Dashboard shows fleet status (online/offline for each node)
3. When home: fleet shows online, local Ollama models appear in /model
4. Switch to local model → inference happens on fleet, zero API cost
5. "Check Titan's disk space" → fleet_ssh tool → shows df output
6. Start conversation on phone → sync → continue on fleet terminal
7. When away from home: fleet shows offline, local models disappear,
   graceful fallback to cloud models
