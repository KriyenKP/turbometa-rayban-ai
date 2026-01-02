package com.turbometa.rayban.services

import android.content.Context
import com.turbometa.rayban.utils.AIProvider
import com.turbometa.rayban.utils.APIKeyManager

/**
 * Provider Endpoint Configuration
 * Stores REST and WebSocket endpoints for each AI provider
 */
data class ProviderEndpoints(
    val restBaseUrl: String,
    val wsBaseUrl: String,
    val visionModel: String,
    val realtimeModel: String,
    val voice: String
)

/**
 * AI Provider Configuration
 * Template-based configuration system supporting:
 * - Alibaba Cloud (Qwen models)
 * - OpenAI (GPT models)
 * - Custom endpoints (self-hosted/enterprise)
 */
object AIProviderConfig {
    
    // Predefined provider templates
    private val providerTemplates = mapOf(
        AIProvider.ALIBABA_CLOUD to ProviderEndpoints(
            restBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            wsBaseUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime",
            visionModel = "qwen3-vl-plus",
            realtimeModel = "qwen3-omni-flash-realtime",
            voice = "longxiaochun"
        ),
        AIProvider.OPENAI to ProviderEndpoints(
            restBaseUrl = "https://api.openai.com/v1",
            wsBaseUrl = "wss://api.openai.com/v1/realtime",
            visionModel = "gpt-4-turbo",
            realtimeModel = "gpt-4o-realtime-preview-2025-06-03",
            voice = "alloy"
        )
    )
    
    /**
     * Get provider configuration
     * For CUSTOM provider, reads from user settings
     */
    fun getProviderConfig(context: Context, provider: AIProvider): ProviderEndpoints {
        return when (provider) {
            AIProvider.CUSTOM -> {
                val apiKeyManager = APIKeyManager.getInstance(context)
                ProviderEndpoints(
                    restBaseUrl = apiKeyManager.getCustomRestEndpoint(),
                    wsBaseUrl = apiKeyManager.getCustomWsEndpoint(),
                    visionModel = apiKeyManager.getCustomVisionModel(),
                    realtimeModel = apiKeyManager.getCustomRealtimeModel(),
                    voice = apiKeyManager.getCustomVoice()
                )
            }
            else -> providerTemplates[provider] ?: providerTemplates[AIProvider.ALIBABA_CLOUD]!!
        }
    }
    
    /**
     * Get API key for provider
     */
    fun getAPIKey(context: Context, provider: AIProvider): String? {
        return APIKeyManager.getInstance(context).getAPIKey(provider)
    }
    
    /**
     * Get currently selected provider
     */
    fun getSelectedProvider(context: Context): AIProvider {
        return APIKeyManager.getInstance(context).getSelectedProvider()
    }
    
    /**
     * Resolve provider configuration
     * If provider is null, uses selected provider from settings
     */
    fun resolveProvider(context: Context, provider: AIProvider?): Pair<AIProvider, ProviderEndpoints> {
        val selectedProvider = provider ?: getSelectedProvider(context)
        val config = getProviderConfig(context, selectedProvider)
        return Pair(selectedProvider, config)
    }
}
