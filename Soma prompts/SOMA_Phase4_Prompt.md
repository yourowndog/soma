# SOMA Phase 4: Session Management + Cross-Session Memory

## Difficulty: Medium — Gemini Pro or Sonnet
## Prerequisite: Phase 3 complete

---

## Context

SOMA currently uses a single session. All messages go into one conversation.
Phase 4 adds proper session management (multiple conversations, session list,
new chat) and basic cross-session memory (the agent can remember facts).

---

## Task 4a: Session List Screen

Create file: `ui/sessions/SessionListScreen.kt`

- Shows all past sessions ordered by most recent
- Each item shows: title, model used, message count, last updated time
- Tap a session to resume it
- Long-press or swipe to delete a session
- FAB or top-bar button to create a new session
- Use the SOMA dark theme

### Navigation

Modify `MainActivity.kt` to use navigation:
- Add `androidx.navigation:navigation-compose` dependency
- Two routes: "chat/{sessionId}" and "sessions"
- Session list → tap session → navigates to chat with that session ID
- Chat screen → back button or menu → navigates to session list
- New chat button → creates session in DB → navigates to chat

### Modify ChatViewModel

- Accept sessionId as a parameter (instead of always using the first session)
- loadHistory uses the passed sessionId
- Multiple ChatViewModel instances can exist for different sessions

### Auto-title sessions

After the first assistant response, use the first ~50 chars of the user's
first message as the session title. (This already exists but verify it works
with the new multi-session setup.)

---

## Task 4b: New Chat / Clear Chat

- "New Chat" button in the session list creates a fresh session
- `/clear` slash command clears the current session's messages (keeps the session)
- `/new` slash command creates a new session and navigates to it

Add these to the slash command autocomplete popup alongside `/model`.

---

## Task 4c: Cross-Session Memory

Create file: `data/db/MemoryDao.kt` and `data/model/MemoryEntry.kt`

### MemoryEntry entity:
```kotlin
@Entity(tableName = "memories")
data class MemoryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val key: String,       // Short label, e.g. "Sam's wife's name"
    val value: String,     // The actual info, e.g. "Kiry"
    val source: String,    // Which session created this memory
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

### MemoryDao:
```kotlin
@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MemoryEntry>

    @Query("SELECT * FROM memories WHERE key LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<MemoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: MemoryEntry)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int
}
```

Add the MemoryDao to SomaDatabase and increment the database version
with a migration.

### Memory Tools

Add to ToolRegistry:

```kotlin
register(ToolDefinition(
    name = "memory_save",
    description = "Save a fact to long-term memory. Use this when the user tells you " +
        "something you should remember across conversations, like their name, " +
        "preferences, projects, contacts, or important facts.",
    parameters = mapOf(
        "key" to ToolParameter("string", "Short label for this memory, e.g. 'user name' or 'fleet IP'"),
        "value" to ToolParameter("string", "The information to remember")
    )
)) { params ->
    val key = params["key"]?.toString() ?: return@register "Error: no key"
    val value = params["value"]?.toString() ?: return@register "Error: no value"
    val entry = MemoryEntry(key = key, value = value, source = currentSessionId)
    memoryDao.upsert(entry)
    "Remembered: $key = $value"
}

register(ToolDefinition(
    name = "memory_search",
    description = "Search long-term memory for previously saved facts. " +
        "Use this when the user asks 'do you remember...' or references " +
        "something from a past conversation.",
    parameters = mapOf(
        "query" to ToolParameter("string", "Search term to look up in memory")
    )
)) { params ->
    val query = params["query"]?.toString() ?: return@register "Error: no query"
    val results = memoryDao.search(query)
    if (results.isEmpty()) {
        "No memories found for '$query'"
    } else {
        results.joinToString("\n") { "• ${it.key}: ${it.value}" }
    }
}
```

### Memory Slash Command

`/memory` — opens a screen or dialog showing all saved memories.
User can view and delete individual memories.

### System Prompt Update

Add to the system prompt:
```
"You have access to a persistent memory system. When the user tells you 
something important about themselves, their preferences, or their projects, 
use the memory_save tool to remember it. When they reference past conversations
or ask 'do you remember...', use memory_search first."
```

---

## Task 4d: Session Export

`/export` slash command — exports the current session as a markdown file:
```markdown
# SOMA Session: Fix Gradle build error
**Date:** 2026-03-20
**Model:** Claude Sonnet 4

---

**You:** How do I fix this gradle error?

**SOMA:** The error is...

**You:** That worked, thanks!

**SOMA:** You're welcome!
```

Save to the device's Downloads folder or open the Android share sheet
with the markdown content.

---

## Testing

1. Create a new session, have a conversation, go back to session list
2. See the session with a title, tap it, conversation is still there
3. Create another session, switch between them
4. Delete a session from the list
5. Say "Remember that my wife's name is Kiry" → memory_save called
6. Start a NEW session, say "What's my wife's name?" → memory_search finds it
7. Type /memory → see saved memories
8. Type /export → get markdown of current session
9. Type /new → new empty session
10. Type /clear → clears current session messages
