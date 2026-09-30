package com.example.ai

import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DiagnosticErrorCode {
    MODEL_NOT_FOUND,
    AUTHENTICATION_FAILED,
    FORBIDDEN,
    QUOTA_EXCEEDED,
    RATE_LIMITED,
    NETWORK_ERROR,
    SERVER_ERROR,
    UNKNOWN_ERROR
}

data class DiagnosticReport(
    val isSuccess: Boolean,
    val message: String,
    val errorCode: DiagnosticErrorCode? = null,
    val providerType: String,
    val modelTested: String
)

class DiagnosticException(
    message: String,
    val errorCode: DiagnosticErrorCode,
    cause: Throwable? = null
) : Exception(message, cause)

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
        val normalizedModel = ModelIdNormalizer.normalize(model)
        return when (providerType.uppercase()) {
            "GEMINI" -> GeminiAIProvider(projectName, apiKey.ifBlank { null }, normalizedModel)
            "OPENAI" -> OpenAIProvider(projectName, apiKey.ifBlank { null }, normalizedModel)
            "ANTHROPIC" -> AnthropicProvider(projectName, apiKey.ifBlank { null }, normalizedModel)
            else -> MockAIProvider(normalizedModel)
        }
    }

    fun classifyHttpError(code: Int, errorBody: String? = null): DiagnosticErrorCode {
        return when (code) {
            404 -> DiagnosticErrorCode.MODEL_NOT_FOUND
            401 -> DiagnosticErrorCode.AUTHENTICATION_FAILED
            403 -> DiagnosticErrorCode.FORBIDDEN
            429 -> {
                val lower = errorBody?.lowercase().orEmpty()
                if (lower.contains("quota") || lower.contains("resource_exhausted")) {
                    DiagnosticErrorCode.QUOTA_EXCEEDED
                } else {
                    DiagnosticErrorCode.RATE_LIMITED
                }
            }
            in 500..599 -> DiagnosticErrorCode.SERVER_ERROR
            else -> DiagnosticErrorCode.UNKNOWN_ERROR
        }
    }

    open fun classifyThrowable(throwable: Throwable): DiagnosticErrorCode {
        if (throwable is DiagnosticException) {
            return throwable.errorCode
        }
        if (throwable is retrofit2.HttpException) {
            val errorBody = try { throwable.response()?.errorBody()?.string() } catch (_: Throwable) { null }
            return classifyHttpError(throwable.code(), errorBody)
        }
        if (throwable is java.net.UnknownHostException || throwable is java.net.SocketTimeoutException || throwable is java.io.IOException) {
            return DiagnosticErrorCode.NETWORK_ERROR
        }
        val msg = throwable.message.orEmpty().lowercase()
        return when {
            msg.contains("401") || msg.contains("api_key_invalid") || msg.contains("unauthenticated") || msg.contains("authentication") -> DiagnosticErrorCode.AUTHENTICATION_FAILED
            msg.contains("403") || msg.contains("permission_denied") || msg.contains("forbidden") -> DiagnosticErrorCode.FORBIDDEN
            msg.contains("404") || msg.contains("not found") || msg.contains("unsupported model") || msg.contains("no longer available") -> DiagnosticErrorCode.MODEL_NOT_FOUND
            msg.contains("429") && (msg.contains("quota") || msg.contains("resource_exhausted")) -> DiagnosticErrorCode.QUOTA_EXCEEDED
            msg.contains("429") || msg.contains("rate limit") -> DiagnosticErrorCode.RATE_LIMITED
            msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("server error") -> DiagnosticErrorCode.SERVER_ERROR
            msg.contains("network") || msg.contains("timeout") || msg.contains("connection") -> DiagnosticErrorCode.NETWORK_ERROR
            else -> DiagnosticErrorCode.UNKNOWN_ERROR
        }
    }

    suspend fun testConnectionDetailed(
        providerType: String,
        apiKey: String,
        model: String
    ): DiagnosticReport {
        val result = testConnection(providerType, apiKey, model)
        val normalizedModel = ModelIdNormalizer.normalize(model)
        return if (result.isSuccess) {
            DiagnosticReport(
                isSuccess = true,
                message = result.getOrNull() ?: "Success",
                providerType = providerType,
                modelTested = normalizedModel
            )
        } else {
            val ex = result.exceptionOrNull()
            val code = (ex as? DiagnosticException)?.errorCode ?: DiagnosticErrorCode.UNKNOWN_ERROR
            DiagnosticReport(
                isSuccess = false,
                message = ex?.message ?: "Failure",
                errorCode = code,
                providerType = providerType,
                modelTested = normalizedModel
            )
        }
    }

    suspend fun testConnection(
        providerType: String,
        apiKey: String,
        model: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val actualModel = ModelIdNormalizer.normalize(model)
        if (actualModel.isBlank() && providerType.uppercase() != "MOCK") {
            return@withContext Result.failure(
                DiagnosticException("Model name cannot be blank.", DiagnosticErrorCode.UNKNOWN_ERROR)
            )
        }
        try {
            when (providerType.uppercase()) {
                "GEMINI" -> {
                    val keyToUse = apiKey.trim().ifBlank {
                        keyManager.getApiKey("GEMINI") ?: ""
                    }
                    if (keyToUse.isBlank() || keyToUse == "MY_GEMINI_API_KEY") {
                        return@withContext Result.failure(
                            DiagnosticException("Gemini API key cannot be blank.", DiagnosticErrorCode.AUTHENTICATION_FAILED)
                        )
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
                        return@withContext Result.failure(
                            DiagnosticException("OpenAI API key cannot be blank.", DiagnosticErrorCode.AUTHENTICATION_FAILED)
                        )
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
                        return@withContext Result.failure(
                            DiagnosticException("Anthropic API key cannot be blank.", DiagnosticErrorCode.AUTHENTICATION_FAILED)
                        )
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
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Throwable) { null }
            val errorCode = classifyHttpError(e.code(), errorBody)
            val friendlyMsg = when (e.code()) {
                401 -> "Invalid API key (HTTP 401): Authentication failed for $providerType. Please verify your API key in Settings."
                403 -> "Unauthorized or forbidden (HTTP 403): The provided key does not have permission to access model '$actualModel' on $providerType."
                404 -> "Model '$actualModel' not found or unsupported (HTTP 404). This model is unavailable on the live $providerType API. Please use dynamic model discovery to select an active model."
                429 -> "Rate limit or quota exceeded (HTTP 429): Quota limit reached for $providerType. Please check your billing or plan limits."
                400 -> "Malformed request (HTTP 400): Model parameter '$actualModel' was rejected by $providerType."
                503 -> "Provider service high demand (HTTP 503): Service temporarily unavailable. The model is valid; please retry shortly."
                in 500..599 -> "Provider server error (HTTP ${e.code()}): Internal error at $providerType."
                else -> "HTTP error ${e.code()}: ${e.message()}"
            }
            Result.failure(DiagnosticException(friendlyMsg, errorCode, e))
        } catch (e: java.net.UnknownHostException) {
            val msg = "Network failure: Unable to reach $providerType servers. Please check your internet connection."
            Result.failure(DiagnosticException(msg, DiagnosticErrorCode.NETWORK_ERROR, e))
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "Request timed out: $providerType did not respond within timeout limits."
            Result.failure(DiagnosticException(msg, DiagnosticErrorCode.NETWORK_ERROR, e))
        } catch (e: IllegalArgumentException) {
            Result.failure(DiagnosticException(e.message ?: "Invalid argument", DiagnosticErrorCode.UNKNOWN_ERROR, e))
        } catch (e: Exception) {
            Result.failure(DiagnosticException(e.message ?: "Connection test failed", DiagnosticErrorCode.UNKNOWN_ERROR, e))
        }
    }
}
