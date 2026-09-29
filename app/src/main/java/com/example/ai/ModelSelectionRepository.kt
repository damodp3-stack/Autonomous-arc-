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
    val isFromCache: Boolean = false,
    val statusMessage: String = "Model availability not verified"
)

interface ModelSelectionRepository {
    fun getModelsState(providerType: String): StateFlow<ModelDiscoveryState>
    fun getCachedModels(providerType: String): List<DiscoveredModel>
    fun getLastDiscoveryTimestamp(providerType: String): Long?
    suspend fun refreshModels(providerType: String, apiKey: String): Result<List<DiscoveredModel>>
    fun getCompatibleFallback(providerType: String, unavailableModel: String, availableModels: List<DiscoveredModel>): DiscoveredModel?
}

class RealModelSelectionRepository(
    private val providers: Map<String, ModelDiscoveryProvider> = mapOf(
        "GEMINI" to GeminiModelDiscoveryProvider(),
        "OPENAI" to OpenAIModelDiscoveryProvider(),
        "ANTHROPIC" to AnthropicModelDiscoveryProvider(),
        "MOCK" to MockModelDiscoveryProvider()
    ),
    private val onDiscoveryTimestampUpdated: (suspend (providerType: String, timestamp: Long) -> Unit)? = null
) : ModelSelectionRepository {

    // Thread-safe in-memory cache of live-discovered models only.
    // Never populated with static catalog models as if they were live.
    // Never contains sensitive credentials or API keys.
    private val cache = ConcurrentHashMap<String, List<DiscoveredModel>>()
    private val lastDiscoveryTimestamps = ConcurrentHashMap<String, Long>()
    private val states = ConcurrentHashMap<String, MutableStateFlow<ModelDiscoveryState>>()

    private fun getOrCreateState(providerType: String): MutableStateFlow<ModelDiscoveryState> {
        val key = providerType.uppercase()
        return states.computeIfAbsent(key) {
            val cached = cache[key]
            val timestamp = lastDiscoveryTimestamps[key]
            if (cached != null && cached.isNotEmpty()) {
                MutableStateFlow(
                    ModelDiscoveryState(
                        models = cached,
                        isFromCache = true,
                        lastRefreshedTimestamp = timestamp,
                        statusMessage = "Cached models (${cached.size} available)"
                    )
                )
            } else {
                MutableStateFlow(
                    ModelDiscoveryState(
                        models = emptyList(),
                        isFromCache = false,
                        lastRefreshedTimestamp = null,
                        statusMessage = "Model availability not verified"
                    )
                )
            }
        }
    }

    override fun getModelsState(providerType: String): StateFlow<ModelDiscoveryState> {
        return getOrCreateState(providerType).asStateFlow()
    }

    override fun getCachedModels(providerType: String): List<DiscoveredModel> {
        val key = providerType.uppercase()
        // Authoritative: returns only previously discovered and cached models
        return cache[key] ?: emptyList()
    }

    override fun getLastDiscoveryTimestamp(providerType: String): Long? {
        val key = providerType.uppercase()
        return lastDiscoveryTimestamps[key]
    }

    override suspend fun refreshModels(providerType: String, apiKey: String): Result<List<DiscoveredModel>> {
        val key = providerType.uppercase()
        val stateFlow = getOrCreateState(key)
        val cleanKey = apiKey.trim()

        if (cleanKey.isBlank() && key != "MOCK") {
            val msg = "API key required — model availability not verified."
            stateFlow.value = stateFlow.value.copy(
                isLoading = false,
                errorMessage = msg,
                statusMessage = msg
            )
            return Result.failure(IllegalArgumentException(msg))
        }

        stateFlow.value = stateFlow.value.copy(isLoading = true, errorMessage = null)

        val discoveryProvider = providers[key]
        if (discoveryProvider == null) {
            val err = "No model discovery provider registered for '$providerType'"
            stateFlow.value = stateFlow.value.copy(
                isLoading = false,
                errorMessage = err,
                statusMessage = "Model availability not verified"
            )
            return Result.failure(IllegalArgumentException(err))
        }

        val result = discoveryProvider.discoverModels(cleanKey)
        return if (result.isSuccess) {
            val discovered = result.getOrNull().orEmpty()
            val now = System.currentTimeMillis()
            cache[key] = discovered
            lastDiscoveryTimestamps[key] = now

            stateFlow.value = ModelDiscoveryState(
                isLoading = false,
                models = discovered,
                errorMessage = null,
                lastRefreshedTimestamp = now,
                isFromCache = false,
                statusMessage = "Live discovered models (${discovered.size} verified available)"
            )

            onDiscoveryTimestampUpdated?.invoke(key, now)
            Result.success(discovered)
        } else {
            val error = result.exceptionOrNull()
            val errorMsg = error?.message ?: "Failed to discover models."
            val existingCache = cache[key]
            val lastTimestamp = lastDiscoveryTimestamps[key]

            if (!existingCache.isNullOrEmpty()) {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    models = existingCache,
                    errorMessage = "$errorMsg (Using last cached models)",
                    lastRefreshedTimestamp = lastTimestamp,
                    isFromCache = true,
                    statusMessage = "Cached models (Last live discovery failed)"
                )
            } else {
                stateFlow.value = stateFlow.value.copy(
                    isLoading = false,
                    models = emptyList(),
                    errorMessage = errorMsg,
                    isFromCache = false,
                    statusMessage = "Model availability not verified"
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
        val clean = ModelIdNormalizer.normalize(unavailableModel)
        if (availableModels.isEmpty()) return null

        // 1. Direct match check
        val directMatch = availableModels.firstOrNull { it.id.equals(clean, ignoreCase = true) }
        if (directMatch != null) return directMatch

        // 2. Identify required modality/capability
        val isVideoRequest = clean.contains("video")
        val isImageRequest = !isVideoRequest && (clean.contains("image") || clean.contains("banana"))

        // 3. Filter discovered models strictly by capability and provider
        val providerPool = availableModels.filter { it.providerType.equals(providerType, ignoreCase = true) }
        val candidatePool = if (providerPool.isNotEmpty()) providerPool else availableModels

        val candidates = when {
            isVideoRequest -> candidatePool.filter { model ->
                model.supportedGenerationMethods.any { it.contains("video", ignoreCase = true) }
            }
            isImageRequest -> candidatePool.filter { it.isImageGeneration }
            else -> candidatePool.filter { model ->
                !model.isImageGeneration && (model.supportedGenerationMethods.isEmpty() || model.supportedGenerationMethods.any { m ->
                    m.contains("generateContent", ignoreCase = true) ||
                    m.contains("chat", ignoreCase = true) ||
                    m.contains("messages", ignoreCase = true) ||
                    m.contains("completions", ignoreCase = true)
                })
            }
        }

        // 4. Return null if no compatible discovered model exists (never invent a model ID)
        if (candidates.isEmpty()) {
            return null
        }

        // 5. Select the safest compatible discovered model (prefer live-verified, then first candidate)
        return candidates.firstOrNull { it.isVerifiedLive } ?: candidates.first()
    }
}
