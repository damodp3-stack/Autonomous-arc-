package com.example.data

import com.example.ai.AIModelRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class AIProviderConfigRepository(private val dao: AIProviderConfigDao) {
    fun getAllConfigs(): Flow<List<AIProviderConfigEntity>> = dao.getAllConfigs()

    fun getActiveConfig(): Flow<AIProviderConfigEntity?> = dao.getActiveConfig()

    suspend fun getConfigById(id: String): AIProviderConfigEntity? = dao.getConfigById(id)

    suspend fun getConfigByProviderType(providerType: String): AIProviderConfigEntity? =
        dao.getConfigByProviderType(providerType)

    suspend fun saveConfig(config: AIProviderConfigEntity) {
        dao.insertConfig(config)
    }
    
    suspend fun setActiveConfig(id: String) {
        dao.deactivateAll()
        dao.setActive(id)
    }

    suspend fun setActiveProviderType(providerType: String) {
        dao.deactivateAll()
        val existing = dao.getConfigByProviderType(providerType)
        if (existing != null) {
            dao.setActiveByProviderType(providerType)
        } else {
            val defaultConfig = AIProviderConfigEntity(
                id = "${providerType.lowercase()}_default",
                providerType = providerType.uppercase(),
                name = providerType.lowercase().replaceFirstChar { it.uppercase() },
                selectedModel = AIModelRegistry.getDefaultModel(providerType),
                isActive = true
            )
            dao.insertConfig(defaultConfig)
        }
    }

    suspend fun updateModelForProvider(providerType: String, model: String) {
        val existing = dao.getConfigByProviderType(providerType)
        if (existing != null) {
            dao.updateConfig(existing.copy(selectedModel = model))
        } else {
            val newConfig = AIProviderConfigEntity(
                id = "${providerType.lowercase()}_default",
                providerType = providerType.uppercase(),
                name = providerType.lowercase().replaceFirstChar { it.uppercase() },
                selectedModel = model,
                isActive = false
            )
            dao.insertConfig(newConfig)
        }
    }

    suspend fun initializeDefaultConfigsIfNeeded() {
        val all = dao.getAllConfigs().firstOrNull() ?: emptyList()
        if (all.isEmpty()) {
            val defaults = listOf(
                AIProviderConfigEntity(
                    id = "gemini_default",
                    providerType = "GEMINI",
                    name = "Google Gemini",
                    selectedModel = AIModelRegistry.getDefaultModel("GEMINI"),
                    isActive = true
                ),
                AIProviderConfigEntity(
                    id = "openai_default",
                    providerType = "OPENAI",
                    name = "OpenAI",
                    selectedModel = AIModelRegistry.getDefaultModel("OPENAI"),
                    isActive = false
                ),
                AIProviderConfigEntity(
                    id = "anthropic_default",
                    providerType = "ANTHROPIC",
                    name = "Anthropic",
                    selectedModel = AIModelRegistry.getDefaultModel("ANTHROPIC"),
                    isActive = false
                )
            )
            defaults.forEach { dao.insertConfig(it) }
        }
    }

    suspend fun deleteConfig(id: String) {
        dao.deleteConfig(id)
    }
}
