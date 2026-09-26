package com.example.ai

object AIModelRegistry {
    val GEMINI_MODELS = listOf(
        "gemini-3.1-flash-lite-preview",
        "gemini-flash-latest",
        "gemini-flash-lite-latest",
        "gemini-3.5-flash",
        "gemini-3.8-flash",
        "gemini-3.1-pro-preview"
    )

    val OPENAI_MODELS = listOf(
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4-turbo",
        "o1-mini",
        "o3-mini"
    )

    val ANTHROPIC_MODELS = listOf(
        "claude-3-5-sonnet-20241022",
        "claude-3-5-sonnet-20240620",
        "claude-3-5-haiku-20241022",
        "claude-3-opus-20240229"
    )

    val MOCK_MODELS = listOf(
        "mock-default"
    )

    fun getAvailableModels(providerType: String): List<String> {
        return when (providerType.uppercase()) {
            "GEMINI" -> GEMINI_MODELS
            "OPENAI" -> OPENAI_MODELS
            "ANTHROPIC" -> ANTHROPIC_MODELS
            else -> MOCK_MODELS
        }
    }

    fun getDefaultModel(providerType: String): String {
        return when (providerType.uppercase()) {
            "GEMINI" -> "gemini-3.1-flash-lite-preview"
            "OPENAI" -> "gpt-4o"
            "ANTHROPIC" -> "claude-3-5-sonnet-20241022"
            else -> "mock-default"
        }
    }

    fun isProhibitedGeminiModel(model: String): Boolean {
        val lower = model.trim().removePrefix("models/").lowercase()
        return lower.startsWith("gemini-1.0") ||
               lower.startsWith("gemini-1.5") ||
               lower.startsWith("gemini-2.0") ||
               lower == "gemini-pro"
    }

    fun resolveModel(providerType: String, model: String): String {
        val trimmed = model.trim().removePrefix("models/")
        if (providerType.equals("GEMINI", ignoreCase = true)) {
            val lower = trimmed.lowercase()
            return when {
                lower in listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-2.0-flash-exp", "gemini flash") -> "gemini-flash-latest"
                lower in listOf("gemini-1.5-pro", "gemini-2.0-pro", "gemini-2.0-flash-thinking", "gemini pro") -> "gemini-3.1-pro-preview"
                lower in listOf("gemini lite", "flash lite", "gemini-lite", "flash-lite") -> "gemini-3.1-flash-lite-preview"
                lower.isBlank() -> getDefaultModel("GEMINI")
                isProhibitedGeminiModel(trimmed) -> getDefaultModel("GEMINI")
                else -> trimmed
            }
        }
        return trimmed.ifBlank { getDefaultModel(providerType) }
    }

    fun isValidModel(providerType: String, model: String): Boolean {
        if (model.isBlank()) return false
        val clean = model.trim().removePrefix("models/")
        if (providerType.equals("GEMINI", ignoreCase = true) && isProhibitedGeminiModel(clean)) {
            return false
        }
        val known = getAvailableModels(providerType)
        return known.any { it.equals(clean, ignoreCase = true) }
    }

    fun getModelDescription(model: String): String {
        val clean = model.trim().removePrefix("models/")
        return when (clean) {
            "gemini-3.1-flash-lite-preview" -> "Gemini 3.1 Flash Lite: Ultra-fast, highly responsive preview model"
            "gemini-flash-latest" -> "Gemini Flash (Latest): Fast general intelligence & high performance"
            "gemini-flash-lite-latest" -> "Gemini Flash Lite (Latest): Highly efficient & rapid turnaround"
            "gemini-3.5-flash" -> "Gemini 3.5 Flash: Next-gen speed, reasoning, and context"
            "gemini-3.8-flash" -> "Gemini 3.8 Flash: High-efficiency frontier coding and reasoning"
            "gemini-3.1-pro-preview" -> "Gemini 3.1 Pro: Advanced reasoning, deep STEM and complex architecture"
            "gpt-4o" -> "GPT-4o: OpenAI flagship multimodal model for high-precision coding"
            "gpt-4o-mini" -> "GPT-4o Mini: Fast, low-cost intelligence for quick edits"
            "gpt-4-turbo" -> "GPT-4 Turbo: High capability with 128k context"
            "o1-mini" -> "o1-mini: Specialized reasoning model for complex logic and math"
            "o3-mini" -> "o3-mini: Next-gen reasoning model with speed and precision"
            "claude-3-5-sonnet-20241022" -> "Claude 3.5 Sonnet (Latest): State-of-the-art coding and reasoning"
            "claude-3-5-sonnet-20240620" -> "Claude 3.5 Sonnet (v1): Strong coding performance"
            "claude-3-5-haiku-20241022" -> "Claude 3.5 Haiku: Blazing fast responsiveness with strong intelligence"
            "claude-3-opus-20240229" -> "Claude 3 Opus: Deep contextual understanding for large projects"
            else -> "Configured model: $clean"
        }
    }
}
