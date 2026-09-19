package com.example.ai

import org.junit.Assert.*
import org.junit.Test

class APIKeyManagerTest {

    private class FakeAPIKeyManager : APIKeyManager {
        private val keys = mutableMapOf<String, String>()

        override fun getApiKey(providerId: String): String? {
            val normalized = providerId.trim().uppercase()
            return keys[normalized] ?: if (normalized == "GEMINI") "gemini-fallback-key" else null
        }

        override fun saveApiKey(providerId: String, apiKey: String) {
            keys[providerId.trim().uppercase()] = apiKey
        }

        override fun clearApiKey(providerId: String) {
            keys.remove(providerId.trim().uppercase())
        }

        override fun hasApiKey(providerId: String): Boolean {
            return !getApiKey(providerId).isNullOrBlank()
        }
    }

    @Test
    fun testSaveAndRetrieveApiKey() {
        val manager = FakeAPIKeyManager()
        manager.saveApiKey("openai", "sk-test-12345")
        
        assertEquals("sk-test-12345", manager.getApiKey("OpenAI"))
        assertEquals("sk-test-12345", manager.getApiKey("OPENAI"))
        assertTrue(manager.hasApiKey("OpenAI"))
    }

    @Test
    fun testClearApiKey() {
        val manager = FakeAPIKeyManager()
        manager.saveApiKey("Anthropic", "sk-ant-test")
        assertTrue(manager.hasApiKey("Anthropic"))

        manager.clearApiKey("anthropic")
        assertNull(manager.getApiKey("Anthropic"))
        assertFalse(manager.hasApiKey("Anthropic"))
    }

    @Test
    fun testGeminiFallbackKey() {
        val manager = FakeAPIKeyManager()
        assertTrue(manager.hasApiKey("Gemini"))
        assertEquals("gemini-fallback-key", manager.getApiKey("Gemini"))

        manager.saveApiKey("Gemini", "user-override-key")
        assertEquals("user-override-key", manager.getApiKey("Gemini"))
    }
}
