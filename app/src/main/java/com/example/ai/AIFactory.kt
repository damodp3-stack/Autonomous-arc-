package com.example.ai

import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.flow.firstOrNull
import com.example.data.AIProviderConfigEntity

class AIFactory(
    private val configRepository: AIProviderConfigRepository,
    private val keyManager: APIKeyManager
) {
    suspend fun getProvider(projectName: String): AIProvider {
        // Just return Gemini or Mock for now since we haven't written OpenAI yet, but we will support it.
        // Or read from DB:
        val activeConfig = configRepository.getActiveConfig().firstOrNull()
        
        return if (activeConfig != null) {
            val apiKey = keyManager.getApiKey(activeConfig.id) ?: ""
            when (activeConfig.providerType) {
                "GEMINI" -> GeminiAIProvider(projectName, apiKey, activeConfig.selectedModel)
                "OPENAI" -> OpenAIProvider(projectName, apiKey, activeConfig.selectedModel)
                "ANTHROPIC" -> AnthropicProvider(projectName, apiKey, activeConfig.selectedModel)
                else -> MockAIProvider()
            }
        } else {
            // Default to Gemini if no config, but use the legacy way or just Mock
            MockAIProvider()
        }
    }
}
