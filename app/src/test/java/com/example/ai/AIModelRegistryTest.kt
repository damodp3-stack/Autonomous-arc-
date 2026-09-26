package com.example.ai

import org.junit.Assert.*
import org.junit.Test

class AIModelRegistryTest {

    @Test
    fun testGetAvailableModels() {
        val geminiModels = AIModelRegistry.getAvailableModels("Gemini")
        assertTrue(geminiModels.contains("gemini-3.1-flash-lite-preview"))
        assertTrue(geminiModels.contains("gemini-flash-latest"))
        assertTrue(geminiModels.contains("gemini-3.8-flash"))
        assertFalse(geminiModels.contains("gemini-1.5-pro"))
        assertFalse(geminiModels.contains("gemini-2.0-flash"))

        val openaiModels = AIModelRegistry.getAvailableModels("OpenAI")
        assertTrue(openaiModels.contains("gpt-4o"))
        assertTrue(openaiModels.contains("gpt-4o-mini"))

        val anthropicModels = AIModelRegistry.getAvailableModels("Anthropic")
        assertTrue(anthropicModels.contains("claude-3-5-sonnet-20241022"))
    }

    @Test
    fun testGetDefaultModel() {
        assertEquals("gemini-3.1-flash-lite-preview", AIModelRegistry.getDefaultModel("Gemini"))
        assertEquals("gpt-4o", AIModelRegistry.getDefaultModel("OpenAI"))
        assertEquals("claude-3-5-sonnet-20241022", AIModelRegistry.getDefaultModel("Anthropic"))
    }

    @Test
    fun testIsValidModel() {
        assertTrue(AIModelRegistry.isValidModel("Gemini", "gemini-3.1-flash-lite-preview"))
        assertTrue(AIModelRegistry.isValidModel("Gemini", "gemini-flash-latest"))
        assertFalse(AIModelRegistry.isValidModel("Gemini", "gemini-2.0-flash"))
        assertFalse(AIModelRegistry.isValidModel("Gemini", "gemini-1.5-pro"))
        assertTrue(AIModelRegistry.isValidModel("OpenAI", "gpt-4o"))
        assertTrue(AIModelRegistry.isValidModel("Anthropic", "claude-3-5-sonnet-20241022"))
        assertFalse(AIModelRegistry.isValidModel("Gemini", "non-existent-model"))
        assertFalse(AIModelRegistry.isValidModel("OpenAI", ""))
    }

    @Test
    fun testResolveModel() {
        assertEquals("gemini-flash-latest", AIModelRegistry.resolveModel("Gemini", "gemini-2.0-flash"))
        assertEquals("gemini-3.1-pro-preview", AIModelRegistry.resolveModel("Gemini", "gemini-1.5-pro"))
        assertEquals("gemini-3.1-flash-lite-preview", AIModelRegistry.resolveModel("Gemini", "flash lite"))
        assertEquals("gemini-3.1-flash-lite-preview", AIModelRegistry.resolveModel("Gemini", ""))
        assertEquals("gemini-3.8-flash", AIModelRegistry.resolveModel("Gemini", "gemini-3.8-flash"))
    }

    @Test
    fun testGetModelDescription() {
        val desc = AIModelRegistry.getModelDescription("gemini-3.1-flash-lite-preview")
        assertTrue(desc.contains("Gemini 3.1 Flash Lite"))
        assertFalse(desc.isBlank())
    }
}
