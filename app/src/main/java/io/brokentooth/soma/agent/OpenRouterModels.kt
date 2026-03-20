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

        validModels.map { model ->
            val promptCost = model.pricing?.prompt?.toFloatOrNull() ?: 0f
            val completionCost = model.pricing?.completion?.toFloatOrNull() ?: 0f
            val isFree = promptCost == 0f && completionCost == 0f
            
            // Convert to cost per million tokens for display
            val promptCostM = promptCost * 1000000
            val completionCostM = completionCost * 1000000

            val providerRaw = model.id.substringBefore("/")
            val abbr = getProviderAbbreviation(providerRaw)
            
            val cleanName = model.name.substringAfter(": ").ifBlank { model.id.substringAfter("/") }
            
            val displayPrice = if (isFree) {
                "(Free)"
            } else {
                "($${String.format("%.2f", promptCostM)}/$${String.format("%.2f", completionCostM)})"
            }
            
            val displayName = "[$abbr] $cleanName $displayPrice"
            
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

    private fun getProviderAbbreviation(provider: String): String {
        return when (provider.lowercase()) {
            "google" -> "Goo"
            "openai" -> "OAI"
            "anthropic" -> "Ant"
            "deepseek" -> "Dsk"
            "openrouter" -> "OR"
            "meta-llama", "meta" -> "Met"
            "mistralai" -> "Mis"
            "x-ai" -> "xAI"
            "stepfun" -> "Stp"
            "qwen" -> "Qwn"
            "nvidia" -> "Nvd"
            "arcee-ai" -> "Arc"
            "minimax" -> "Mmx"
            "liquid" -> "Liq"
            "kwaipilot" -> "Kwa"
            else -> provider.take(3).replaceFirstChar { it.uppercase() }
        }
    }
}
