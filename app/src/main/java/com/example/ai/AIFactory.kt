package com.example.ai

import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class AIFactory(
    private val configRepository: AIProviderConfigRepository,
    private val keyManager: APIKeyManager
) {
    open suspend fun getProvider(projectName: String, uiSelectedProvider: String? = null): AIProvider {
        return getProviderInternal(projectName, uiSelectedProvider, null)
    }

    open suspend fun getProvider(
        projectName: String,
        uiSelectedProvider: String?,
        selectedModel: String?
    ): AIProvider {
        if (selectedModel == null) {
            return getProvider(projectName, uiSelectedProvider)
        }
        return getProviderInternal(projectName, uiSelectedProvider, selectedModel)
    }

    private suspend fun getProviderInternal(
        projectName: String,
        uiSelectedProvider: String?,
        selectedModel: String?
    ): AIProvider {
        val activeConfig = configRepository.getActiveConfig().firstOrNull()

        val providerType = uiSelectedProvider?.ifBlank { null }
            ?: activeConfig?.providerType
            ?: "GEMINI"

        val configuredModelForProvider = configRepository.getConfigByProviderType(providerType)?.selectedModel
            ?: if (activeConfig?.providerType.equals(providerType, ignoreCase = true)) activeConfig?.selectedModel else null

        val effectiveModel = selectedModel?.ifBlank { null }
            ?: configuredModelForProvider?.ifBlank { null }
            ?: AIModelRegistry.getDefaultModel(providerType)

        val apiKey = keyManager.getApiKey(providerType)
            ?: keyManager.getApiKey(providerType.uppercase())
            ?: activeConfig?.let { keyManager.getApiKey(it.id) }
            ?: ""

        return createProviderInstance(providerType, projectName, apiKey, effectiveModel)
    }

    open fun createProviderInstance(
        providerType: String,
        projectName: String,
        apiKey: String,
        model: String
    ): AIProvider {
        return when (providerType.uppercase()) {
            "GEMINI" -> GeminiAIProvider(projectName, apiKey.ifBlank { null }, model)
            "OPENAI" -> OpenAIProvider(projectName, apiKey.ifBlank { null }, model)
            "ANTHROPIC" -> AnthropicProvider(projectName, apiKey.ifBlank { null }, model)
            else -> MockAIProvider(model)
        }
    }

    suspend fun testConnection(
        providerType: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (model.isBlank() && providerType.uppercase() != "MOCK") {
            return@withContext Result.failure(IllegalArgumentException("Model name cannot be blank."))
        }
        val effectiveModel = model.ifBlank { AIModelRegistry.getDefaultModel(providerType) }
        try {
            when (providerType.uppercase()) {
                "GEMINI" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("GEMINI") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_GEMINI_API_KEY") {
                        return@withContext Result.failure(IllegalArgumentException("Gemini API key cannot be blank."))
                    }
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = "ping"))))
                    )
                    GeminiRetrofitClient.service.generateContent(effectiveModel, keyToUse, request)
                    Result.success("Successfully connected to Gemini ($effectiveModel)!")
                }
                "OPENAI" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("OPENAI") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_OPENAI_API_KEY") {
                        return@withContext Result.failure(IllegalArgumentException("OpenAI API key cannot be blank."))
                    }
                    val request = OpenAIChatRequest(
                        model = effectiveModel,
                        messages = listOf(OpenAIMessage(role = "user", content = "ping"))
                    )
                    OpenAIRetrofitClient.service.createChatCompletion("Bearer $keyToUse", request = request)
                    Result.success("Successfully connected to OpenAI ($effectiveModel)!")
                }
                "ANTHROPIC" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("ANTHROPIC") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_ANTHROPIC_API_KEY") {
                        return@withContext Result.failure(IllegalArgumentException("Anthropic API key cannot be blank."))
                    }
                    val request = AnthropicRequest(
                        model = effectiveModel,
                        messages = listOf(AnthropicMessage(role = "user", content = "ping")),
                        maxTokens = 10
                    )
                    AnthropicRetrofitClient.service.createMessage(apiKey = keyToUse, request = request)
                    Result.success("Successfully connected to Anthropic ($effectiveModel)!")
                }
                else -> {
                    Result.success("Connected to local Mock provider successfully.")
                }
            }
        } catch (e: retrofit2.HttpException) {
            val friendlyMsg = when (e.code()) {
                401, 403 -> "Authentication failed (HTTP ${e.code()}): Invalid or unauthorized API key."
                404 -> "Model '$effectiveModel' not found or unsupported (HTTP 404)."
                429 -> "Rate limit or quota exceeded (HTTP 429)."
                in 500..599 -> "Provider service temporarily unavailable (HTTP ${e.code()})."
                else -> "HTTP error ${e.code()}: ${e.message()}"
            }
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Connection test failed", e))
        }
    }
}
