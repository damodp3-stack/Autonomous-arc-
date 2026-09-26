package com.example.ai

import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class AIFactory(
    private val configRepository: AIProviderConfigRepository,
    private val keyManager: APIKeyManager,
    val modelSelectionRepository: ModelSelectionRepository = RealModelSelectionRepository()
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
        val actualModel = model.trim().removePrefix("models/")
        if (actualModel.isBlank() && providerType.uppercase() != "MOCK") {
            return@withContext Result.failure(IllegalArgumentException("Model name cannot be blank."))
        }
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
                    GeminiRetrofitClient.service.generateContent(actualModel, keyToUse, request)
                    Result.success("Successfully connected to Gemini ($actualModel)!")
                }
                "OPENAI" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("OPENAI") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_OPENAI_API_KEY") {
                        return@withContext Result.failure(IllegalArgumentException("OpenAI API key cannot be blank."))
                    }
                    val request = OpenAIChatRequest(
                        model = actualModel,
                        messages = listOf(OpenAIMessage(role = "user", content = "ping"))
                    )
                    OpenAIRetrofitClient.service.createChatCompletion("Bearer $keyToUse", request = request)
                    Result.success("Successfully connected to OpenAI ($actualModel)!")
                }
                "ANTHROPIC" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("ANTHROPIC") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_ANTHROPIC_API_KEY") {
                        return@withContext Result.failure(IllegalArgumentException("Anthropic API key cannot be blank."))
                    }
                    val request = AnthropicRequest(
                        model = actualModel,
                        messages = listOf(AnthropicMessage(role = "user", content = "ping")),
                        maxTokens = 10
                    )
                    AnthropicRetrofitClient.service.createMessage(apiKey = keyToUse, request = request)
                    Result.success("Successfully connected to Anthropic ($actualModel)!")
                }
                else -> {
                    Result.success("Connected to local Mock provider successfully.")
                }
            }
        } catch (e: retrofit2.HttpException) {
            val friendlyMsg = when (e.code()) {
                401 -> "Invalid API key (HTTP 401): Authentication failed for $providerType. Please verify your API key."
                403 -> "Unauthorized or forbidden (HTTP 403): The provided key does not have permission to access '$actualModel'."
                404 -> "Model '$actualModel' not found or unsupported (HTTP 404). This model is unavailable on the current $providerType API. Please use model discovery to select an active model (e.g. gemini-3.1-flash-lite-preview or gemini-flash-latest)."
                429 -> "Rate limit or quota exceeded (HTTP 429): Quota limit reached for $providerType. Please check your billing or plan limits."
                400 -> "Malformed request (HTTP 400): Model parameter '$actualModel' was rejected by $providerType."
                503 -> "Provider service high demand (HTTP 503): Service temporarily unavailable. The model is valid; please retry shortly."
                in 500..599 -> "Provider server error (HTTP ${e.code()}): Internal error at $providerType."
                else -> "HTTP error ${e.code()}: ${e.message()}"
            }
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("Network failure: Unable to reach $providerType servers. Please check your internet connection.", e))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Request timed out: $providerType did not respond within timeout limits.", e))
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Connection test failed", e))
        }
    }
}
