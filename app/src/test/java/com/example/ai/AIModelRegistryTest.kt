package com.example.ai

import org.junit.Assert.*
import org.junit.Test

class AIModelRegistryTest {

    @Test
    fun testGetAvailableModels() {
        val geminiModels = AIModelRegistry.getAvailableModels("Gemini")
        assertTrue(geminiModels.contains("gemini-1.5-pro"))
        assertTrue(geminiModels.contains("gemini-1.5-flash"))

        val openaiModels = AIModelRegistry.getAvailableModels("OpenAI")
        assertTrue(openaiModels.contains("gpt-4o"))
        assertTrue(openaiModels.contains("gpt-4o-mini"))

        val anthropicModels = AIModelRegistry.getAvailableModels("Anthropic")
        assertTrue(anthropicModels.contains("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun testGetDefaultModel() {
        assertEquals("gemini-1.5-pro", AIModelRegistry.getDefaultModel("Gemini"))
        assertEquals("gpt-4o", AIModelRegistry.getDefaultModel("OpenAI"))
        assertEquals("claude-3-5-sonnet-20241022", AIModelRegistry.getDefaultModel("Anthropic"))
    }

    @Test
    fun testIsValidModel() {
        assertTrue(AIModelRegistry.isValidModel("Gemini", "gemini-1.5-flash"))
        assertTrue(AIModelRegistry.isValidModel("OpenAI", "gpt-4o"))
        assertTrue(AIModelRegistry.isValidModel("Anthropic", "claude-3-5-sonnet-20241022"))
        assertFalse(AIModelRegistry.isValidModel("Gemini", "non-existent-model"))
        assertFalse(AIModelRegistry.isValidModel("OpenAI", ""))
    }

    @Test
    fun testGetModelDescription() {
        val desc = AIModelRegistry.getModelDescription("gpt-4o")
        assertTrue(desc.contains("GPT-4o"))
        assertFalse(desc.isBlank())
    }
}
