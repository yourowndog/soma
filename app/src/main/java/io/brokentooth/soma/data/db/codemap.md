# SOMA Persistence Layer

## Responsibility
**Local Data Storage & Session Memory**
- Manages the SQLite database via Room for persistent conversation history.
- Provides DAOs for session management and message retrieval.

## Design
- **Repository Pattern (Implicit)**: `SomaDatabase` and its DAOs act as the single source of truth for data.
- **Singleton**: `SomaApplication` holds a static reference to the database instance.
- **Entity-Relationship**: `Session` (1) \u2192 `Message` (N) relationship via `sessionId`.

## Flow
1. **Write**: `ChatViewModel` calls `messageDao.insert(message)` when a user sends or an assistant completes a message.
2. **Read**: `ChatAgent.loadHistory(sessionId)` queries `messageDao.getBySessionId(sessionId)` to hydrate in-memory history.
3. **Update**: `sessionDao.updateTitle()` is called after the first exchange to rename the session based on user input.

## Integration
- **Room Persistence Library**: Standard Android ORM for SQLite.
- **SomaApplication**: Database initialization and lifecycle management.
- **ChatViewModel**: Direct consumer of DAOs for UI state updates.
