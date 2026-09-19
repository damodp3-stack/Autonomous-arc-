package com.example.ai

import com.example.data.AIProviderConfigDao
import com.example.data.AIProviderConfigEntity
import com.example.data.AIProviderConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AIFactoryTest {

    private class FakeKeyManager : APIKeyManager {
        val keys = mutableMapOf<String, String>()
        override fun getApiKey(providerId: String): String? = keys[providerId.trim().uppercase()]
        override fun saveApiKey(providerId: String, apiKey: String) { keys[providerId.trim().uppercase()] = apiKey }
        override fun clearApiKey(providerId: String) { keys.remove(providerId.trim().uppercase()) }
        override fun hasApiKey(providerId: String): Boolean = !getApiKey(providerId).isNullOrBlank()
    }

    private class FakeDao : AIProviderConfigDao {
        val configs = mutableMapOf<String, AIProviderConfigEntity>()

        override fun getAllConfigs(): Flow<List<AIProviderConfigEntity>> = flowOf(configs.values.toList())
        override fun getActiveConfig(): Flow<AIProviderConfigEntity?> = flowOf(configs.values.firstOrNull { it.isActive })
        override suspend fun getConfigById(id: String): AIProviderConfigEntity? = configs[id]
        override suspend fun getConfigByProviderType(providerType: String): AIProviderConfigEntity? =
            configs.values.firstOrNull { it.providerType.equals(providerType, ignoreCase = true) }

        override suspend fun insertConfig(config: AIProviderConfigEntity) {
            configs[config.id] = config
        }

        override suspend fun updateConfig(config: AIProviderConfigEntity) {
            configs[config.id] = config
        }

        override suspend fun deleteConfig(id: String) {
            configs.remove(id)
        }

        override suspend fun deactivateAll() {
            configs.replaceAll { _, v -> v.copy(isActive = false) }
        }

        override suspend fun setActive(id: String) {
            configs.replaceAll { k, v -> v.copy(isActive = (k == id)) }
        }

        override suspend fun setActiveByProviderType(providerType: String) {
            configs.replaceAll { _, v -> v.copy(isActive = v.providerType.equals(providerType, ignoreCase = true)) }
        }
    }

    @Test
    fun testMockProviderFallbackWhenKeysMissing() = runBlocking {
        val keyManager = FakeKeyManager()
        val dao = FakeDao()
        val repo = AIProviderConfigRepository(dao)
        val factory = AIFactory(repo, keyManager)

        // Request MOCK provider -> returns MockAIProvider
        val provider = factory.getProvider("TestProject", "MOCK", "mock-default")
        assertTrue(provider is MockAIProvider)
    }

    @Test
    fun testRealProviderResolutionWithKey() = runBlocking {
        val keyManager = FakeKeyManager()
        keyManager.saveApiKey("OpenAI", "sk-test-mock-key")
        val dao = FakeDao()
        val repo = AIProviderConfigRepository(dao)
        val factory = AIFactory(repo, keyManager)

        val provider = factory.getProvider("TestProject", "OpenAI", "gpt-4o")
        assertTrue(provider is OpenAIProvider)
        assertEquals("gpt-4o", (provider as OpenAIProvider).model)
    }

    @Test
    fun testTestConnectionWithEmptyKey() = runBlocking {
        val keyManager = FakeKeyManager()
        val dao = FakeDao()
        val repo = AIProviderConfigRepository(dao)
        val factory = AIFactory(repo, keyManager)

        val result = factory.testConnection("OpenAI", "", "gpt-4o")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("API key cannot be blank") == true)
    }

    @Test
    fun testTestConnectionWithBlankModel() = runBlocking {
        val keyManager = FakeKeyManager()
        val dao = FakeDao()
        val repo = AIProviderConfigRepository(dao)
        val factory = AIFactory(repo, keyManager)

        val result = factory.testConnection("OpenAI", "sk-test", "")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Model name cannot be blank") == true)
    }
}
