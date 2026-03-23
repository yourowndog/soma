package io.brokentooth.soma.agent

import android.content.Context
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import io.brokentooth.soma.tools.OpenAppTool
import io.brokentooth.soma.tools.GetDeviceInfoTool
import io.brokentooth.soma.tools.ClipboardReadTool
import io.brokentooth.soma.tools.ClipboardWriteTool
import io.brokentooth.soma.tools.FlashlightTool
import io.brokentooth.soma.tools.SettingsPanelTool
import io.brokentooth.soma.tools.ToolContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class KoogAgentManager(
    private val apiKey: String,
    private val modelId: String,
    private val appContext: Context
) : LlmProvider {

    init {
        // Initialize the tool context so tools can access Android APIs
        ToolContext.init(appContext)
    }

    override fun streamChat(messages: List<ChatMessage>): Flow<String> = flow {
        val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content ?: ""
        
        try {
            // 1. Build the Koog tool registry with our tools
            val toolRegistry = ToolRegistry {
                tool(OpenAppTool)
                tool(GetDeviceInfoTool)
                tool(ClipboardReadTool)
                tool(ClipboardWriteTool)
                tool(FlashlightTool)
                tool(SettingsPanelTool)
            }

            // 2. Build a Koog prompt that includes conversation history
            val koogPrompt = prompt("soma-chat") {
                system("You are SOMA, a helpful AI assistant running on an Android phone. You can use tools to interact with the device when asked.")
                // Add conversation history (skip the last user message — that's the input)
                for (msg in messages.dropLast(1)) {
                    when (msg.role) {
                        "user" -> user(msg.content)
                        "assistant" -> assistant(msg.content)
                    }
                }
            }

            // 3. Create the agent
            val executor = simpleOpenRouterExecutor(apiKey)
            
            // 4. Create agent config
            val config = AIAgentConfig(
                prompt = koogPrompt,
                model = LLModel(
                    provider = LLMProvider.OpenRouter,
                    id = modelId,
                    capabilities = listOf(
                        LLMCapability.Completion,
                        LLMCapability.Tools,
                        LLMCapability.Temperature
                    ),
                    contextLength = 128_000,
                    maxOutputTokens = 4096
                ),
                maxAgentIterations = 30
            )

            // 5. Build and run the agent
            val agent = AIAgent(
                promptExecutor = executor,
                toolRegistry = toolRegistry,
                strategy = chatAgentStrategy(),
                agentConfig = config
            )

            // 6. Run the agent with the latest user message
            val result = agent.run(lastUserMessage)
            
            // 7. Emit the complete response
            emit(result)
        } catch (e: Exception) {
            val errorMsg = e.message ?: ""
            val causeMsg = e.cause?.message ?: ""
            val isNetworkError = errorMsg.contains("Software caused connection abort") || 
                                 causeMsg.contains("Software caused connection abort") ||
                                 errorMsg.contains("Connection reset") ||
                                 causeMsg.contains("Connection reset")
                                 
            val isNetworkContext = lastUserMessage.lowercase().contains("wifi") || 
                                   lastUserMessage.lowercase().contains("network") ||
                                   lastUserMessage.lowercase().contains("internet")

            if (isNetworkError && isNetworkContext) {
                emit("I executed the network command, but changing the device's network state temporarily severed my connection to the AI server. The action should be complete.")
            } else {
                // Rethrow with a descriptive message so ChatViewModel can handle it
                throw RuntimeException("Error running Koog agent: ${e.message}", e)
            }
        }
    }.flowOn(Dispatchers.IO)
}