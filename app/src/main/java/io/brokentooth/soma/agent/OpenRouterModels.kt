package io.brokentooth.soma.agent

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "OpenRouterModels"

@Serializable
data class ORModelsResponse(
    val data: List<ORModelInfo> = emptyList()
)

@Serializable
data class ORModelInfo(
    val id: String,
    val name: String = "",
    @SerialName("context_length") val contextLength: Int = 0,
    val architecture: ORArchitecture? = null,
    val pricing: ORPricing? = null
)

@Serializable
data class ORArchitecture(
    val modality: String = ""
)

@Serializable
data class ORPricing(
    val prompt: String = "0",
    val completion: String = "0"
)

object OpenRouterModels {

    private val json = Json { ignoreUnknownKeys = true }

    // Models confirmed dead via live probe on 2026-03-20 (400/404/502 errors — not transient 429s).
    // 429s (rate-limited free models) are intentionally excluded from this list — they ARE real.
    private val BROKEN_MODELS = setOf(
        "openai/gpt-5.4-nano", "openai/gpt-5.4-mini", "openai/gpt-5.4", "openai/gpt-5.4-pro",
        "openai/gpt-5.3-chat", "openai/gpt-5.3-codex",
        "openai/gpt-5.2-codex", "openai/gpt-5.2-chat", "openai/gpt-5.2-pro", "openai/gpt-5.2",
        "openai/gpt-5.1-codex-max", "openai/gpt-5.1-chat", "openai/gpt-5.1",
        "openai/gpt-5.1-codex", "openai/gpt-5.1-codex-mini",
        "openai/gpt-5-image-mini", "openai/gpt-5-image", "openai/gpt-5-codex",
        "openai/gpt-5-chat", "openai/gpt-5", "openai/gpt-5-mini", "openai/gpt-5-nano", "openai/gpt-5-pro",
        "openai/gpt-audio-mini", "openai/gpt-audio", "openai/gpt-4o-audio-preview",
        "openai/gpt-4-1106-preview",
        "openai/o1", "openai/o1-pro",
        "openai/o3", "openai/o3-mini", "openai/o3-mini-high", "openai/o3-pro", "openai/o3-deep-research",
        "openai/o4-mini", "openai/o4-mini-high", "openai/o4-mini-deep-research",
        "openai/gpt-4.1", "openai/gpt-4.1-nano", "openai/gpt-4.1-mini",
        "openai/gpt-oss-20b:free", "openai/gpt-oss-120b:free",
        "minimax/minimax-m2.5:free",
        "arcee-ai/spotlight", "arcee-ai/maestro-reasoning", "arcee-ai/coder-large", "arcee-ai/virtuoso-large",
        "google/gemma-3n-e4b-it:free",
        "allenai/olmo-2-0325-32b-instruct",
        "tngtech/deepseek-r1t2-chimera",
        "morph/morph-v3-fast", "morph/morph-v3-large",
        "relace/relace-apply-3"
    )

    // Models culled for quality/relevance reasons (not broken — just noise):
    // - Guard/safety classifiers (not chat models)
    // - Search-augmented models (behave differently, not general chat)
    // - Router meta-models
    // - Superseded older generation models (when newer gen exists)
    // - Tiny (<10B) models that are outclassed
    // - Vanilla base Llama instruct (fine-tuned variants kept)
    // - Google AI Studio tunneled :free models (can't accept system prompts)
    // - Dated versioned duplicates when canonical/latest exists
    // - Multimodal VL-only variants not useful for text chat
    // RP/creative models intentionally KEPT
    private val CULLED_MODELS = setOf(
        // Guard/safety/search/router — not general chat
        "meta-llama/llama-guard-4-12b", "meta-llama/llama-guard-3-8b",
        "openai/gpt-oss-safeguard-20b",
        "relace/relace-search",
        "openai/gpt-4o-search-preview", "openai/gpt-4o-mini-search-preview",
        "perplexity/sonar-pro", "perplexity/sonar", "perplexity/sonar-deep-research", "perplexity/sonar-reasoning-pro",
        "switchpoint/router", "openrouter/auto", "openrouter/free", "openrouter/bodybuilder",

        // Google AI Studio tunneled :free — can't accept system prompts (400 INVALID_ARGUMENT)
        "google/gemma-3n-e2b-it:free", "google/gemma-3-4b-it:free",
        "google/gemma-3-12b-it:free", "google/gemma-3-27b-it:free",
        "mistralai/mistral-small-3.1-24b-instruct:free",
        "qwen/qwen3-coder:free", "qwen/qwen3-next-80b-a3b-instruct:free", "qwen/qwen3-4b:free",
        "meta-llama/llama-3.3-70b-instruct:free", "meta-llama/llama-3.2-3b-instruct:free",
        "nousresearch/hermes-3-llama-3.1-405b:free",
        "cognitivecomputations/dolphin-mistral-24b-venice-edition:free",

        // Superseded older generations (newer gen exists from same provider)
        "openai/gpt-3.5-turbo", "openai/gpt-3.5-turbo-0613", "openai/gpt-3.5-turbo-16k", "openai/gpt-3.5-turbo-instruct",
        "openai/gpt-4", "openai/gpt-4-0314", "openai/gpt-4-turbo", "openai/gpt-4-turbo-preview",
        "openai/gpt-4o", "openai/gpt-4o-mini",
        "openai/gpt-4o-2024-05-13", "openai/gpt-4o-2024-08-06", "openai/gpt-4o-2024-11-20",
        "openai/gpt-4o-mini-2024-07-18",
        "anthropic/claude-3-haiku", "anthropic/claude-3.5-haiku", "anthropic/claude-3.5-sonnet",
        "anthropic/claude-3.7-sonnet", "anthropic/claude-3.7-sonnet:thinking",
        "anthropic/claude-sonnet-4", "anthropic/claude-sonnet-4.5",
        "anthropic/claude-opus-4", "anthropic/claude-opus-4.1", "anthropic/claude-opus-4.5",
        "google/gemini-2.0-flash-001", "google/gemini-2.0-flash-lite-001",
        "google/gemini-2.5-flash", "google/gemini-2.5-flash-lite", "google/gemini-2.5-flash-lite-preview-09-2025",
        "google/gemini-2.5-flash-image",
        "google/gemini-2.5-pro", "google/gemini-2.5-pro-preview", "google/gemini-2.5-pro-preview-05-06",
        "google/gemini-3-flash-preview", "google/gemini-3-pro-preview",
        "google/gemini-3-pro-image-preview", "google/gemini-3.1-flash-image-preview",
        "google/gemini-3.1-pro-preview-customtools",
        "deepseek/deepseek-chat", "deepseek/deepseek-chat-v3-0324", "deepseek/deepseek-chat-v3.1",
        "deepseek/deepseek-v3.1-terminus", "deepseek/deepseek-v3.2-speciale", "deepseek/deepseek-v3.2-exp",
        "deepseek/deepseek-r1", "deepseek/deepseek-r1-0528",
        "deepseek/deepseek-r1-distill-llama-70b", "deepseek/deepseek-r1-distill-qwen-32b",
        "mistralai/mistral-7b-instruct-v0.1", "mistralai/mistral-nemo",
        "mistralai/mixtral-8x7b-instruct", "mistralai/mixtral-8x22b-instruct",
        "mistralai/mistral-large", "mistralai/mistral-large-2407", "mistralai/mistral-large-2411", "mistralai/mistral-large-2512",
        "mistralai/mistral-saba",
        "mistralai/mistral-small-24b-instruct-2501", "mistralai/mistral-small-creative",
        "mistralai/mistral-medium-3",
        "mistralai/ministral-3b-2512", "mistralai/ministral-8b-2512", "mistralai/ministral-14b-2512",
        "x-ai/grok-3", "x-ai/grok-3-beta", "x-ai/grok-3-mini", "x-ai/grok-3-mini-beta",
        "x-ai/grok-4",
        "meta-llama/llama-3-8b-instruct", "meta-llama/llama-3-70b-instruct",
        "meta-llama/llama-3.1-8b-instruct", "meta-llama/llama-3.1-70b-instruct", "meta-llama/llama-3.1-405b",
        "meta-llama/llama-3.2-1b-instruct", "meta-llama/llama-3.2-3b-instruct", "meta-llama/llama-3.2-11b-vision-instruct",
        "meta-llama/llama-3.3-70b-instruct",
        "nousresearch/hermes-2-pro-llama-3-8b", "nousresearch/hermes-3-llama-3.1-70b", "nousresearch/hermes-3-llama-3.1-405b",
        "cohere/command-r-08-2024", "cohere/command-r-plus-08-2024", "cohere/command-r7b-12-2024",
        "google/gemma-2-9b-it", "google/gemma-2-27b-it",
        "qwen/qwq-32b",
        "qwen/qwen-2.5-7b-instruct", "qwen/qwen-2.5-72b-instruct", "qwen/qwen-2.5-coder-32b-instruct",
        "qwen/qwen2.5-coder-7b-instruct", "qwen/qwen2.5-vl-32b-instruct", "qwen/qwen2.5-vl-72b-instruct",
        "qwen/qwen-plus", "qwen/qwen-max", "qwen/qwen-turbo", "qwen/qwen-vl-plus", "qwen/qwen-vl-max",
        "qwen/qwen-plus-2025-07-28", "qwen/qwen-plus-2025-07-28:thinking",
        "qwen/qwen3-4b", "qwen/qwen3-8b", "qwen/qwen3-14b", "qwen/qwen3-32b",
        "qwen/qwen3-30b-a3b", "qwen/qwen3-235b-a22b", "qwen/qwen3-max", "qwen/qwen3-max-thinking",
        "qwen/qwen3-coder", "qwen/qwen3-30b-a3b-thinking-2507", "qwen/qwen3-30b-a3b-instruct-2507",
        "qwen/qwen3-235b-a22b-thinking-2507", "qwen/qwen3-235b-a22b-2507",
        "qwen/qwen3.5-9b", "qwen/qwen3.5-27b", "qwen/qwen3.5-35b-a3b", "qwen/qwen3.5-flash-02-23", "qwen/qwen3.5-plus-02-15",
        "qwen/qwen3-vl-8b-instruct", "qwen/qwen3-vl-8b-thinking",

        // Small models (<12B, outclassed by free giants)
        "liquid/lfm-2.5-1.2b-instruct:free", "liquid/lfm-2.5-1.2b-thinking:free",
        "liquid/lfm-2.2-6b", "liquid/lfm2-8b-a1b",
        "ibm-granite/granite-4.0-h-micro",
        "google/gemma-3n-e2b-it", "google/gemma-3n-e4b-it", "google/gemma-3-4b-it",
        "microsoft/phi-4",
        "alfredpros/codellama-7b-instruct-solidity", "eleutherai/llemma_7b",
        "aion-labs/aion-rp-llama-3.1-8b", "aion-labs/aion-1.0-mini",
        "bytedance/ui-tars-1.5-7b",
        "meta-llama/llama-3.2-1b-instruct",
        "qwen/qwen-2.5-vl-7b-instruct", "qwen/qwen3-vl-8b-instruct", "qwen/qwen3-vl-8b-thinking",
        "nvidia/nemotron-nano-9b-v2", "nvidia/nemotron-nano-9b-v2:free",
        "allenai/olmo-3-7b-instruct", "allenai/olmo-3-7b-think", "allenai/olmo-3.1-32b-instruct",
        "allenai/olmo-3-32b-think", "allenai/molmo-2-8b",
        "bytedance-seed/seed-2.0-mini", "bytedance-seed/seed-1.6-flash",

        // Older/niche duplicates with better alternatives available
        "nvidia/llama-3.1-nemotron-70b-instruct", "nvidia/llama-3.3-nemotron-super-49b-v1.5",
        "amazon/nova-micro-v1", "amazon/nova-lite-v1",
        "z-ai/glm-4-32b", "z-ai/glm-4.6", "z-ai/glm-4.6v", "z-ai/glm-4.7", "z-ai/glm-4.7-flash",
        "z-ai/glm-4.5", "z-ai/glm-4.5v", "z-ai/glm-5-turbo",
        "baidu/ernie-4.5-21b-a3b", "baidu/ernie-4.5-21b-a3b-thinking",
        "baidu/ernie-4.5-vl-28b-a3b", "baidu/ernie-4.5-vl-424b-a47b",
        "moonshotai/kimi-k2", "moonshotai/kimi-k2-0905", "moonshotai/kimi-k2-thinking",
        "mistralai/mistral-small-2603",
        "deepseek/deepseek-chat-v3-0324",
        "nex-agi/deepseek-v3.1-nex-n1",
        "deepcogito/cogito-v2.1-671b", "prime-intellect/intellect-3",
        "essentialai/rnj-1-instruct",
        "alibaba/tongyi-deepresearch-30b-a3b", "meituan/longcat-flash-chat",
        "tencent/hunyuan-a13b-instruct",
        "upstage/solar-pro-3",
        "ai21/jamba-large-1.7",
        "inflection/inflection-3-productivity", "inflection/inflection-3-pi",
        "cohere/command-a",
        "aion-labs/aion-1.0", "aion-labs/aion-2.0",
        "writer/palmyra-x5",
        "microsoft/wizardlm-2-8x22b",
        "minimax/minimax-m1", "minimax/minimax-m2", "minimax/minimax-m2-her",
        "qwen/qwen3-coder-30b-a3b-instruct",
        "openai/gpt-oss-20b",
        "x-ai/grok-4.20-beta", "x-ai/grok-4.20-multi-agent-beta",
        "google/gemini-3-pro-image-preview",
        "inception/mercury", "inception/mercury-coder",
        "liquid/lfm-2-24b-a2b",
        "mistralai/voxtral-small-24b-2507",
        "bytedance-seed/seed-2.0-lite",

        // Older euryale/sao10k versions — keep only latest (l3.3)
        "sao10k/l3-euryale-70b", "sao10k/l3-lunaris-8b",
        "sao10k/l3.1-euryale-70b", "sao10k/l3.1-70b-hanami-x1",

        // Multiple devstral sizes — keep medium (best balance)
        "mistralai/devstral-small",

        // Mistral small duplicates — keep 3.2 only
        "mistralai/mistral-small-3.1-24b-instruct",

        // Qwen VL redundant sizes — keep 235B (largest) and 32B (mid)
        "qwen/qwen3-vl-30b-a3b-instruct", "qwen/qwen3-vl-30b-a3b-thinking",

        // Qwen3 coder variants — keep next (newest) and flash (fastest)
        "qwen/qwen3-coder-plus",

        // Qwen3.5 — keep only the flagship 397B
        "qwen/qwen3.5-122b-a10b",

        // Amazon Nova — keep only pro tier
        "amazon/nova-2-lite-v1",

        // Minimax — keep m2.5 and m2.7 (latest two), drop older
        "minimax/minimax-01",

        // Nvidia nemotron nano 12b VL — keep free, drop paid duplicate
        "nvidia/nemotron-nano-12b-v2-vl",

        // Grok — keep 4.1-fast as daily driver, code-fast-1 for coding
        // Drop grok-4-fast (superseded by 4.1-fast)
        "x-ai/grok-4-fast",

        // Baidu ernie — obscure, limited English quality
        "baidu/ernie-4.5-300b-a47b",

        // Perplexity sonar-pro-search is search-augmented — behaves differently
        "perplexity/sonar-pro-search",

        // Amazon nova-2-lite already dropped, drop premier (overpriced niche)
        "amazon/nova-premier-v1",

        // Bytedance seed — niche, little community data
        "bytedance-seed/seed-1.6",

        // Thedrummer — keep only cydonia (latest flagship), drop older rocinante/unslopnemo
        "thedrummer/rocinante-12b", "thedrummer/unslopnemo-12b",

        // Inception mercury-2 — niche, little track record
        "inception/mercury-2"
    )

    // Hardcoded IPD map from NotebookLM knowledge
    private val IPD_TIERS = mapOf(
        "stepfun/step-3.5-flash:free" to 100f,
        "qwen/qwen3-next-80b-a3b-instruct:free" to 99f,
        "nvidia/nemotron-3-super-120b-a12b:free" to 98f,
        "arcee-ai/trinity-large-preview:free" to 97f,
        "deepseek/deepseek-v3.2" to 90f,
        "minimax/minimax-m2.1" to 89f,
        "google/gemini-3.1-flash-lite-preview" to 88f
    )

    fun getFallbackModels(): List<ModelOption> = listOf(
        ModelOption("stepfun/step-3.5-flash:free", "[Stp] Step 3.5 Flash (Free)", "openrouter", true, 256000, 0f, 0f, 100f),
        ModelOption("qwen/qwen3-next-80b-a3b-instruct:free", "[Qwn] Qwen3 Next 80B (Free)", "openrouter", true, 262000, 0f, 0f, 99f),
        ModelOption("nvidia/nemotron-3-super-120b-a12b:free", "[Nvd] Nemotron 3 Super (Free)", "openrouter", true, 262144, 0f, 0f, 98f),
        ModelOption("arcee-ai/trinity-large-preview:free", "[Arc] Trinity Large (Free)", "openrouter", true, 131000, 0f, 0f, 97f),
        ModelOption("deepseek/deepseek-v3.2", "[Dsk] DeepSeek V3.2 ($0.26/$0.38)", "openrouter", false, 163840, 0.26f, 0.38f, 90f),
        ModelOption("minimax/minimax-m2.1", "[Mmx] MiniMax M2.1 ($0.27/$0.95)", "openrouter", false, 196608, 0.27f, 0.95f, 89f),
        ModelOption("google/gemini-3.1-flash-lite-preview", "[Goo] Gemini 3.1 Flash Lite ($0.25/$1.50)", "openrouter", false, 1048576, 0.25f, 1.50f, 88f)
    )

    suspend fun fetchModels(apiKey: String, http: OkHttpClient): List<ModelOption> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "OpenRouter API key is blank, using fallback")
            return@withContext getFallbackModels()
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter API request failed", e)
            return@withContext getFallbackModels()
        }

        if (!response.isSuccessful) {
            Log.e(TAG, "OpenRouter models API ${response.code}: ${response.body?.string()}")
            return@withContext getFallbackModels()
        }

        val body = response.body?.string() ?: return@withContext getFallbackModels()
        val parsed = json.decodeFromString<ORModelsResponse>(body)

        val validModels = parsed.data.filter { model ->
            model.pricing != null && model.id.isNotBlank()
        }.filter { model ->
            val modality = model.architecture?.modality ?: ""
            if (modality.contains("->")) modality.substringAfter("->").contains("text") else true
        }.filter { !it.id.contains(":extended") }
         .filter { it.id !in BROKEN_MODELS }
         .filter { it.id !in CULLED_MODELS }

        validModels.map { model ->
            val promptCost = model.pricing?.prompt?.toFloatOrNull() ?: 0f
            val completionCost = model.pricing?.completion?.toFloatOrNull() ?: 0f
            val isFree = promptCost == 0f && completionCost == 0f
            
            // Convert to cost per million tokens for display
            val promptCostM = promptCost * 1000000
            val completionCostM = completionCost * 1000000
            
            val cleanName = model.name.substringAfter(": ").ifBlank { model.id.substringAfter("/") }
            
            val displayPrice = if (isFree) {
                "(Free)"
            } else {
                "($${String.format("%.2f", promptCostM)}/$${String.format("%.2f", completionCostM)})"
            }
            
            val displayName = "[OR] $cleanName $displayPrice"
            
            val ipdScore = IPD_TIERS[model.id] ?: if (isFree) 50f else 0f

            ModelOption(
                id = model.id,
                displayName = displayName,
                provider = "openrouter",
                isFree = isFree,
                contextLength = model.contextLength,
                inputPrice = promptCostM,
                outputPrice = completionCostM,
                ipdScore = ipdScore
            )
        }.sortedWith(
            compareByDescending<ModelOption> { it.ipdScore > 0f } // High IPD first
                .thenByDescending { it.ipdScore }                 // Highest IPD value
                .thenByDescending { it.isFree }                   // Other free next
                .thenBy { it.displayName }                        // Alphabetical rest
        )
    }
}
