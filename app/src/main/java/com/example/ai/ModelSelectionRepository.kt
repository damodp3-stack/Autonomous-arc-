package com.example.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ModelDiscoveryState(
    val isLoading: Boolean = false,
    val models: List<DiscoveredModel> = emptyList(),
    val errorMessage: String? = null,
    val lastRefreshedTimestamp: Long? = null,
    val isFromCache: Boolean = false
)

interface ModelSelectionRepository {
    fun getModelsState(providerType: String): StateFlow<ModelDiscoveryState>
    fun getCachedModels(providerType: String): List<DiscoveredModel>
    suspend fun refreshModels(providerType: String, apiKey: String): Result<List<DiscoveredModel>>
    fun getCompatibleFallback(providerType: String, unavailableModel: String, availableModels: List<DiscoveredModel>): DiscoveredModel?
}

class RealModelSelectionRepository(
    private val providers: Map<String, ModelDiscoveryProvider> = mapOf(
        "GEMINI" to GeminiModelDiscoveryProvider(),
        "OPENAI" to OpenAIModelDiscoveryProvider(),
        "ANTHROPIC" to AnthropicModelDiscoveryProvider(),
        "MOCK" to MockModelDiscoveryProvider()
    )
) : ModelSelectionRepository {

    private val cache = ConcurrentHashMap<String, List<DiscoveredModel>>()
    private val states = ConcurrentHashMap<String, MutableStateFlow<ModelDiscoveryState>>()

    private fun getOrCreateState(providerType: String): MutableStateFlow<ModelDiscoveryState> {
        val key = providerType.uppercase()
        return states.computeIfAbsent(key) {
            val initialFallback = AIModelCatalog.getCatalogForProvider(key)
            MutableStateFlow(
                ModelDiscoveryState(
                    models = initialFallback,
                    isFromCache = true
                )
            )
        }
    }

    override fun getModelsState(providerType: String): StateFlow<ModelDiscoveryState> {
        return getOrCreateState(providerType).asStateFlow()
    }

    override fun getCachedModels(providerType: String): List<DiscoveredModel> {
        val key = providerType.uppercase()
        return cache[key] ?: AIModelCatalog.getCatalogForProvider(key)
    }

    override suspend fun refreshModels(providerType: String, apiKey: String): Result<List<DiscoveredModel>> {
        val key = providerType.uppercase()
        val stateFlow = getOrCreateState(key)
        stateFlow.value = stateFlow.value.copy(isLoading = true, errorMessage = null)

        val discoveryProvider = providers[key]
        if (discoveryProvider == null) {
            val err = "No model discovery provider registered for '$providerType'"
            stateFlow.value = stateFlow.value.copy(isLoading = false, errorMessage = err)
            return Result.failure(IllegalArgumentException(err))
        }

        val result = discoveryProvider.discoverModels(apiKey)
        return if (result.isSuccess) {
            val discovered = result.getOrNull().orEmpty()
            cache[key] = discovered
            val now = System.currentTimeMillis()
            stateFlow.value = ModelDiscoveryState(
                isLoading = false,
                models = discovered,
                errorMessage = null,
                lastRefreshedTimestamp = now,
                isFromCache = false
            )
            Result.success(discovered)
        } else {
            val error = result.exceptionOrNull()
            val errorMsg = error?.message ?: "Failed to discover models."
            val existingCache = cache[key]

            if (!existingCache.isNullOrEmpty()) {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    models = existingCache,
                    errorMessage = "$errorMsg (Using last cached models)",
                    isFromCache = true
                )
            } else {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    errorMessage = errorMsg,
                    isFromCache = false
                )
            }
            Result.failure(error ?: Exception(errorMsg))
        }
    }

    override fun getCompatibleFallback(
        providerType: String,
        unavailableModel: String,
        availableModels: List<DiscoveredModel>
    ): DiscoveredModel? {
        val clean = unavailableModel.trim().removePrefix("models/")
        // If the requested model is already available in the list, use it
        val match = availableModels.firstOrNull { it.id.equals(clean, ignoreCase = true) }
        if (match != null) return match

        // Determine if requested model was image generation
        val isImageRequest = clean.contains("image") || clean.contains("banana")

        val candidates = if (isImageRequest) {
            availableModels.filter { it.isImageGeneration }
        } else {
            availableModels.filter { !it.isImageGeneration }
        }

        if (candidates.isEmpty() && availableModels.isNotEmpty()) {
            return availableModels.first()
        }

        return when (providerType.uppercase()) {
            "GEMINI" -> {
                if (isImageRequest) {
                    candidates.firstOrNull { it.id == "gemini-2.5-flash-image" }
                        ?: candidates.firstOrNull()
                } else {
                    candidates.firstOrNull { it.id == "gemini-3.1-flash-lite-preview" }
                        ?: candidates.firstOrNull { it.id == "gemini-flash-latest" }
                        ?: candidates.firstOrNull { it.id == "gemini-3.5-flash" }
                        ?: candidates.firstOrNull()
                }
            }
            "OPENAI" -> {
                candidates.firstOrNull { it.id == "gpt-4o-mini" }
                    ?: candidates.firstOrNull { it.id == "gpt-4o" }
                    ?: candidates.firstOrNull()
            }
            "ANTHROPIC" -> {
                candidates.firstOrNull { it.id.contains("haiku") }
                    ?: candidates.firstOrNull { it.id.contains("sonnet") }
                    ?: candidates.firstOrNull()
            }
            else -> candidates.firstOrNull() ?: availableModels.firstOrNull()
        }
    }
}
