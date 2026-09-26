package com.example.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Abstraction for dynamic AI model discovery directly from provider APIs.
 */
interface ModelDiscoveryProvider {
    val providerType: String
    suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>>
}

class GeminiModelDiscoveryProvider(
    private val apiService: GeminiApiService = GeminiRetrofitClient.service
) : ModelDiscoveryProvider {
    override val providerType: String = "GEMINI"

    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_GEMINI_API_KEY" || cleanKey == "YOUR_GEMINI_API_KEY") {
            return@withContext Result.failure(IllegalArgumentException("Gemini API key is missing or invalid."))
        }

        try {
            val allDiscovered = mutableListOf<DiscoveredModel>()
            var pageToken: String? = null
            var pagesFetched = 0
            val maxPages = 5

            do {
                val response = apiService.listModels(apiKey = cleanKey, pageSize = 100, pageToken = pageToken)
                val rawModels = response.models.orEmpty()

                for (dto in rawModels) {
                    val rawName = dto.name
                    val cleanId = rawName.removePrefix("models/").trim()
                    val methods = dto.supportedGenerationMethods.orEmpty()

                    // Rule 1: Exclude strictly prohibited / deprecated models
                    if (AIModelRegistry.isProhibitedGeminiModel(cleanId)) {
                        continue
                    }

                    // Rule 2: Must support generateContent
                    if (!methods.contains("generateContent")) {
                        continue
                    }

                    // Rule 3: Filter out non-interactive models (e.g., embeddings, transcribe, robotics)
                    val lower = cleanId.lowercase()
                    val isEmbedding = lower.contains("embedding") || lower.contains("aqa")
                    val isRobotics = lower.contains("robotics")
                    if (isEmbedding || isRobotics) {
                        continue
                    }

                    val isImageGen = lower.contains("image") || lower.contains("banana")

                    allDiscovered.add(
                        DiscoveredModel(
                            id = cleanId,
                            displayName = dto.displayName?.ifBlank { null } ?: cleanId,
                            description = dto.description,
                            supportedGenerationMethods = methods,
                            isImageGeneration = isImageGen,
                            inputTokenLimit = dto.inputTokenLimit,
                            outputTokenLimit = dto.outputTokenLimit,
                            providerType = "GEMINI",
                            isAvailable = true
                        )
                    )
                }

                pageToken = response.nextPageToken
                pagesFetched++
            } while (!pageToken.isNullOrBlank() && pagesFetched < maxPages)

            // Prioritize standard coding/text models first
            val sorted = allDiscovered.sortedWith(
                compareBy<DiscoveredModel> {
                    // Flash lite and flash latest first, then pro, then image, then others
                    when {
                        it.id == "gemini-3.1-flash-lite-preview" -> 0
                        it.id == "gemini-flash-latest" -> 1
                        it.id == "gemini-flash-lite-latest" -> 2
                        it.id == "gemini-3.5-flash" -> 3
                        it.id == "gemini-3.8-flash" -> 4
                        it.id == "gemini-3.1-pro-preview" -> 5
                        it.id == "gemini-2.5-flash" -> 6
                        it.id == "gemini-2.5-pro" -> 7
                        it.isImageGeneration -> 20
                        else -> 10
                    }
                }.thenBy { it.id }
            )

            Result.success(sorted)
        } catch (e: retrofit2.HttpException) {
            val friendlyMsg = when (e.code()) {
                401, 403 -> "Authentication failed: Invalid or unauthorized Gemini API key."
                404 -> "Gemini Models endpoint returned 404 Not Found."
                429 -> "Gemini rate limit or quota exceeded while listing models."
                503 -> "Gemini service temporarily unavailable (HTTP 503)."
                in 500..599 -> "Gemini server error (${e.code()})."
                else -> "Gemini API error (${e.code()}): ${e.message()}"
            }
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class OpenAIModelDiscoveryProvider(
    private val apiService: OpenAIApiService = OpenAIRetrofitClient.service
) : ModelDiscoveryProvider {
    override val providerType: String = "OPENAI"

    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_OPENAI_API_KEY") {
            return@withContext Result.failure(IllegalArgumentException("OpenAI API key is missing or invalid."))
        }

        try {
            val response = apiService.listModels(authorization = "Bearer $cleanKey")
            val rawList = response.data.orEmpty()

            val relevant = rawList
                .map { it.id.trim() }
                .filter { id ->
                    val lower = id.lowercase()
                    (lower.startsWith("gpt-4") || lower.startsWith("gpt-3.5") || lower.startsWith("o1") || lower.startsWith("o3")) &&
                    !lower.contains("audio") && !lower.contains("realtime") && !lower.contains("transcribe")
                }
                .distinct()
                .map { id ->
                    DiscoveredModel(
                        id = id,
                        displayName = id,
                        description = AIModelRegistry.getModelDescription(id),
                        supportedGenerationMethods = listOf("chat.completions"),
                        isImageGeneration = id.lowercase().contains("dall-e"),
                        providerType = "OPENAI",
                        isAvailable = true
                    )
                }
                .sortedWith(
                    compareBy<DiscoveredModel> {
                        when (it.id) {
                            "gpt-4o" -> 0
                            "gpt-4o-mini" -> 1
                            "o3-mini" -> 2
                            "o1-mini" -> 3
                            "gpt-4-turbo" -> 4
                            else -> 10
                        }
                    }.thenBy { it.id }
                )

            Result.success(relevant)
        } catch (e: retrofit2.HttpException) {
            val friendlyMsg = when (e.code()) {
                401, 403 -> "Authentication failed: Invalid OpenAI API key."
                429 -> "OpenAI quota or rate limit exceeded."
                in 500..599 -> "OpenAI server error (${e.code()})."
                else -> "OpenAI API error (${e.code()}): ${e.message()}"
            }
            Result.failure(Exception(friendlyMsg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class AnthropicModelDiscoveryProvider(
    private val apiService: AnthropicApiService = AnthropicRetrofitClient.service
) : ModelDiscoveryProvider {
    override val providerType: String = "ANTHROPIC"

    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank() || cleanKey == "MY_ANTHROPIC_API_KEY") {
            return@withContext Result.failure(IllegalArgumentException("Anthropic API key is missing or invalid."))
        }

        try {
            val response = apiService.listModels(apiKey = cleanKey)
            val models = response.data.orEmpty()
                .filter { it.id.isNotBlank() }
                .map { dto ->
                    DiscoveredModel(
                        id = dto.id,
                        displayName = dto.displayName ?: dto.id,
                        description = AIModelRegistry.getModelDescription(dto.id),
                        supportedGenerationMethods = listOf("messages"),
                        isImageGeneration = false,
                        providerType = "ANTHROPIC",
                        isAvailable = true
                    )
                }
            if (models.isNotEmpty()) {
                Result.success(models)
            } else {
                // Return curated active models if API returned empty list
                Result.success(AIModelRegistry.ANTHROPIC_MODELS.map { id ->
                    DiscoveredModel(
                        id = id,
                        displayName = id,
                        description = AIModelRegistry.getModelDescription(id),
                        providerType = "ANTHROPIC",
                        isAvailable = true
                    )
                })
            }
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404 || e.code() == 400) {
                // If models endpoint isn't supported on account tier, fallback to known valid models
                Result.success(AIModelRegistry.ANTHROPIC_MODELS.map { id ->
                    DiscoveredModel(
                        id = id,
                        displayName = id,
                        description = AIModelRegistry.getModelDescription(id),
                        providerType = "ANTHROPIC",
                        isAvailable = true
                    )
                })
            } else {
                val friendlyMsg = when (e.code()) {
                    401, 403 -> "Authentication failed: Invalid Anthropic API key."
                    429 -> "Anthropic rate limit exceeded."
                    in 500..599 -> "Anthropic server error (${e.code()})."
                    else -> "Anthropic API error (${e.code()}): ${e.message()}"
                }
                Result.failure(Exception(friendlyMsg, e))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MockModelDiscoveryProvider : ModelDiscoveryProvider {
    override val providerType: String = "MOCK"

    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> {
        return Result.success(
            listOf(
                DiscoveredModel(
                    id = "mock-default",
                    displayName = "Mock Provider Model",
                    description = "Local mock model for testing and offline development",
                    supportedGenerationMethods = listOf("generateContent"),
                    providerType = "MOCK",
                    isAvailable = true
                )
            )
        )
    }
}
