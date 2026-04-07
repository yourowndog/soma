# SOMA Phase 1.5: Model Selector Improvements

## Split into 3 small tasks. Each can be done independently.

---

# Task 1.5a: Fetch Live Models from OpenRouter API
**Difficulty: Medium — give to Gemini Pro or any competent model**

### Context
SOMA currently has 3 hardcoded models. OpenRouter has 400+ models available
via their API, including many free ones. We need to fetch the real model list
dynamically.

### What to build

Create file: `agent/OpenRouterModels.kt`

This file should:

1. **Fetch models from OpenRouter API on app startup:**
```
GET https://openrouter.ai/api/v1/models
Header: Authorization: Bearer {OPENROUTER_API_KEY}
```

The response is JSON with a `data` array. Each model object has:
- `id` (string) — e.g. "anthropic/claude-sonnet-4"
- `name` (string) — e.g. "Claude Sonnet 4"  
- `pricing` (object) — `prompt` and `completion` fields, both strings of
  dollar amounts per token. "0" means free.
- `context_length` (number) — max context window
- `top_provider` (object) — has `is_moderated` field

2. **Parse into our ModelOption format**, adding:
```kotlin
data class ModelOption(
    val id: String,
    val displayName: String,
    val provider: String,
    val isFree: Boolean,          // NEW
    val contextLength: Int = 0,   // NEW
    val inputPrice: String = "",  // NEW — for display, e.g. "$3.00/M"
)
```

3. **Cache the model list** in memory (no need to refetch every time the
   bottom sheet opens). Fetch once on app start, store in the ViewModel.
   If fetch fails (offline), fall back to a small hardcoded list of known
   good models.

4. **Filter out dead models**: Only include models where `pricing` is not null.

**Hardcoded fallback list** (these are confirmed working as of March 2026):
```kotlin
val FALLBACK_MODELS = listOf(
    ModelOption("stepfun/step-2-16k:free", "Step 2 16K (Free)", "openrouter", true, 16000),
    ModelOption("google/gemma-3-27b-it:free", "Gemma 3 27B (Free)", "openrouter", true, 96000),
    ModelOption("qwen/qwen3-235b-a22b:free", "Qwen3 235B (Free)", "openrouter", true, 40960),
    ModelOption("anthropic/claude-sonnet-4", "Claude Sonnet 4", "openrouter", false, 200000),
    ModelOption("deepseek/deepseek-r1", "DeepSeek R1", "openrouter", false, 163840),
    ModelOption("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash", "openrouter", false, 1048576),
)
```

### Modify ChatViewModel.kt

- Remove the hardcoded `availableModels` list
- Add a `MutableStateFlow<List<ModelOption>>` for the live model list
- Fetch models in `init` block (in a coroutine)
- Change the default model to StepFun Step 2 Free:
```kotlin
private val DEFAULT_MODEL = ModelOption(
    id = "stepfun/step-2-16k:free",
    displayName = "Step 2 16K (Free)", 
    provider = "openrouter",
    isFree = true,
    contextLength = 16000
)
```

### Keep Gemini as a special entry
Gemini isn't on OpenRouter (you're using your direct API key). Add it
manually to the top of the model list:
```kotlin
val geminiModel = ModelOption(
    id = "gemini-flash",
    displayName = "Gemini 2.0 Flash (Direct)",
    provider = "gemini",
    isFree = true, // free via your API key
    contextLength = 1000000
)
// Prepend to the fetched OpenRouter list
```

---

# Task 1.5b: Search + Filter in Model Selector
**Difficulty: Easy — any model can do this, even free tier**

### Context
The model selector bottom sheet now shows hundreds of models. Users need to
search and filter.

### Modify `ModelSelectorSheet.kt`

1. **Add a search field at the top of the sheet:**
```kotlin
var searchQuery by remember { mutableStateOf("") }

// Search TextField at top of sheet
OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    placeholder = { Text("Search models...", color = SomaTextMuted) },
    singleLine = true,
    colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = SomaAccentBlue,
        unfocusedBorderColor = SomaBorder,
        cursorColor = SomaAccentBlue,
        focusedTextColor = SomaTextPrimary,
        unfocusedTextColor = SomaTextPrimary
    ),
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
)
```

2. **Add a "Free Only" toggle:**
```kotlin
var showFreeOnly by remember { mutableStateOf(false) }

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
) {
    Text("Free models only", color = SomaTextSecondary, fontSize = 13.sp)
    Switch(
        checked = showFreeOnly,
        onCheckedChange = { showFreeOnly = it },
        colors = SwitchDefaults.colors(
            checkedTrackColor = SomaAccentGreen,
            checkedThumbColor = SomaTextPrimary
        )
    )
}
```

3. **Filter the model list:**
```kotlin
val filteredModels = models.filter { model ->
    val matchesSearch = searchQuery.isBlank() || 
        model.displayName.contains(searchQuery, ignoreCase = true) ||
        model.id.contains(searchQuery, ignoreCase = true)
    val matchesFree = !showFreeOnly || model.isFree
    matchesSearch && matchesFree
}
```

4. **Make the list scrollable** — wrap the model list in a `LazyColumn`
   instead of a regular `Column` since there could be hundreds of models.

5. **Show pricing info** next to each model:
```kotlin
Text(
    text = if (model.isFree) "FREE" else model.inputPrice,
    fontSize = 11.sp,
    color = if (model.isFree) SomaAccentGreen else SomaTextMuted
)
```

6. **Group by: Gemini (Direct) at top, then Free OpenRouter, then Paid OpenRouter.**
   Within each group, alphabetical.

---

# Task 1.5c: Default Model + Persist Selection
**Difficulty: Easy — any model can do this**

### Context
When the app starts, it should default to StepFun free via OpenRouter
(not Gemini). When the user switches models, that choice should persist
across app restarts.

### Changes

1. **In `SomaApplication.kt` or a new `Preferences.kt`:**
```kotlin
// Use SharedPreferences or DataStore to persist the selected model ID
fun saveSelectedModel(context: Context, modelId: String) {
    context.getSharedPreferences("soma_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("selected_model_id", modelId)
        .apply()
}

fun loadSelectedModel(context: Context): String? {
    return context.getSharedPreferences("soma_prefs", Context.MODE_PRIVATE)
        .getString("selected_model_id", null)
}
```

2. **In ChatViewModel.kt init:**
```kotlin
// Load persisted model selection, fall back to default
val savedModelId = loadSelectedModel(getApplication())
val initialModel = if (savedModelId != null) {
    availableModels.value.find { it.id == savedModelId } ?: DEFAULT_MODEL
} else {
    DEFAULT_MODEL
}
_currentModel.value = initialModel
```

3. **In switchModel():**
```kotlin
fun switchModel(model: ModelOption) {
    if (model.id == _currentModel.value.id) return
    _currentModel.value = model
    agent.switchProvider(createProvider(model))
    saveSelectedModel(getApplication(), model.id)  // Persist!
    // ... rest of the system message logic
}
```

4. **Update createProvider** to handle both gemini and openrouter:
```kotlin
private fun createProvider(model: ModelOption): LlmProvider {
    return when (model.provider) {
        "gemini" -> GeminiClient(BuildConfig.GOOGLE_API_KEY)
        "openrouter" -> OpenRouterClient(
            apiKey = BuildConfig.OPENROUTER_API_KEY,
            modelId = model.id
        )
        else -> OpenRouterClient(  // default to OpenRouter
            apiKey = BuildConfig.OPENROUTER_API_KEY,
            modelId = model.id
        )
    }
}
```

---

## Execution Order

Do these in order: **1.5a → 1.5b → 1.5c**

1.5a is the hardest (API fetching, parsing, caching). Give to Gemini Pro.
1.5b is pure UI work. Any model.
1.5c is trivial. Any model.

## Testing

After all three:
1. App launches → defaults to StepFun Step 2 Free (or your last selection)
2. Type /model → sheet opens with search field and "Free Only" toggle
3. Toggle "Free Only" → only free models shown
4. Type "step" → filters to StepFun models
5. Type "claude" → shows Claude models with pricing
6. Select a model → it persists across app restart
7. Gemini Direct appears at top of the list as a special entry
