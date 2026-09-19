package com.example.ai

object AIModelRegistry {
    val GEMINI_MODELS = listOf(
        "gemini-1.5-pro",
        "gemini-1.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-exp"
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
            "GEMINI" -> "gemini-1.5-pro"
            "OPENAI" -> "gpt-4o"
            "ANTHROPIC" -> "claude-3-5-sonnet-20241022"
            else -> "mock-default"
        }
    }

    fun isValidModel(providerType: String, model: String): Boolean {
        if (model.isBlank()) return false
        val known = getAvailableModels(providerType)
        return known.any { it.equals(model.trim(), ignoreCase = true) }
    }

    fun getModelDescription(model: String): String {
        return when (model) {
            "gemini-1.5-pro" -> "Gemini 1.5 Pro: Versatile, complex reasoning & large context"
            "gemini-1.5-flash" -> "Gemini 1.5 Flash: Ultra-fast and lightweight for rapid iterations"
            "gemini-2.0-flash" -> "Gemini 2.0 Flash: Next-gen speed and multimodal capabilities"
            "gemini-2.0-flash-exp" -> "Gemini 2.0 Flash Experimental: Latest capabilities preview"
            "gpt-4o" -> "GPT-4o: OpenAI flagship multimodal model for high-precision coding"
            "gpt-4o-mini" -> "GPT-4o Mini: Fast, low-cost intelligence for quick edits"
            "gpt-4-turbo" -> "GPT-4 Turbo: High capability with 128k context"
            "o1-mini" -> "o1-mini: Specialized reasoning model for complex logic and math"
            "o3-mini" -> "o3-mini: Next-gen reasoning model with speed and precision"
            "claude-3-5-sonnet-20241022" -> "Claude 3.5 Sonnet (Latest): State-of-the-art coding and reasoning"
            "claude-3-5-sonnet-20240620" -> "Claude 3.5 Sonnet (v1): Strong coding performance"
            "claude-3-5-haiku-20241022" -> "Claude 3.5 Haiku: Blazing fast responsiveness with strong intelligence"
            "claude-3-opus-20240229" -> "Claude 3 Opus: Deep contextual understanding for large projects"
            else -> "Configured model: $model"
        }
    }
}
