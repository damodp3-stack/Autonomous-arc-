package com.example.ai

/**
 * Static catalog of AI model descriptions, metadata, and offline documentation.
 *
 * CRITICAL SAFETY RULES:
 * 1. This catalog must NEVER override live provider model discovery.
 * 2. Models listed here are NOT verified live models (isAvailable = false, isVerifiedLive = false).
 * 3. Never present catalog models as confirmed available unless the provider API
 *    has actually returned that model via live dynamic discovery.
 * 4. Obsolete/shut down models (such as gemini-2.0-flash, gemini-1.5-pro, gemini-1.5-flash)
 *    are strictly excluded.
 */
object AIModelCatalog {

    val GEMINI_CATALOG = listOf(
        DiscoveredModel(
            id = "gemini-3.1-flash-lite-preview",
            displayName = "Gemini 3.1 Flash Lite (Preview)",
            description = "Ultra-fast, highly responsive preview model for coding and quick iteration.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-flash-latest",
            displayName = "Gemini Flash (Latest)",
            description = "Fast general intelligence, high throughput, and context reasoning.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-flash-lite-latest",
            displayName = "Gemini Flash Lite (Latest)",
            description = "Highly efficient & rapid turnaround for routine assistant tasks.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-3.5-flash",
            displayName = "Gemini 3.5 Flash",
            description = "Next-generation speed, deep reasoning, and high throughput context.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-3.8-flash",
            displayName = "Gemini 3.8 Flash",
            description = "High-efficiency frontier coding, architecture, and reasoning.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-3.1-pro-preview",
            displayName = "Gemini 3.1 Pro (Preview)",
            description = "Frontier intelligence for advanced reasoning, complex architecture and coding.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            description = "Balanced multimodal foundation model for general generation tasks.",
            supportedGenerationMethods = listOf("generateContent", "countTokens"),
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gemini-2.5-flash-image",
            displayName = "Gemini 2.5 Flash Image",
            description = "Real-time image generation and multimodal image output.",
            supportedGenerationMethods = listOf("generateContent"),
            isImageGeneration = true,
            providerType = "GEMINI",
            isAvailable = false,
            isVerifiedLive = false
        )
    )

    val OPENAI_CATALOG = listOf(
        DiscoveredModel(
            id = "gpt-4o",
            displayName = "GPT-4o",
            description = "OpenAI flagship multimodal model for high-precision coding.",
            supportedGenerationMethods = listOf("chat.completions"),
            providerType = "OPENAI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gpt-4o-mini",
            displayName = "GPT-4o Mini",
            description = "Fast, low-cost intelligence for quick edits.",
            supportedGenerationMethods = listOf("chat.completions"),
            providerType = "OPENAI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "gpt-4-turbo",
            displayName = "GPT-4 Turbo",
            description = "High capability with 128k context.",
            supportedGenerationMethods = listOf("chat.completions"),
            providerType = "OPENAI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "o1-mini",
            displayName = "o1-mini",
            description = "Specialized reasoning model for complex logic and math.",
            supportedGenerationMethods = listOf("chat.completions"),
            providerType = "OPENAI",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "o3-mini",
            displayName = "o3-mini",
            description = "Next-gen reasoning model with speed and precision.",
            supportedGenerationMethods = listOf("chat.completions"),
            providerType = "OPENAI",
            isAvailable = false,
            isVerifiedLive = false
        )
    )

    val ANTHROPIC_CATALOG = listOf(
        DiscoveredModel(
            id = "claude-3-5-sonnet-20241022",
            displayName = "Claude 3.5 Sonnet (Latest)",
            description = "State-of-the-art coding and reasoning.",
            supportedGenerationMethods = listOf("messages"),
            providerType = "ANTHROPIC",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "claude-3-5-sonnet-20240620",
            displayName = "Claude 3.5 Sonnet (v1)",
            description = "Strong coding performance.",
            supportedGenerationMethods = listOf("messages"),
            providerType = "ANTHROPIC",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "claude-3-5-haiku-20241022",
            displayName = "Claude 3.5 Haiku",
            description = "Blazing fast responsiveness with strong intelligence.",
            supportedGenerationMethods = listOf("messages"),
            providerType = "ANTHROPIC",
            isAvailable = false,
            isVerifiedLive = false
        ),
        DiscoveredModel(
            id = "claude-3-opus-20240229",
            displayName = "Claude 3 Opus",
            description = "Deep contextual understanding for large projects.",
            supportedGenerationMethods = listOf("messages"),
            providerType = "ANTHROPIC",
            isAvailable = false,
            isVerifiedLive = false
        )
    )

    val MOCK_CATALOG = listOf(
        DiscoveredModel(
            id = "mock-default",
            displayName = "Mock Provider Model",
            description = "Local mock model for testing and offline development.",
            supportedGenerationMethods = listOf("generateContent"),
            providerType = "MOCK",
            isAvailable = true,
            isVerifiedLive = true
        )
    )

    fun getCatalogForProvider(providerType: String): List<DiscoveredModel> {
        return when (providerType.uppercase()) {
            "GEMINI" -> GEMINI_CATALOG
            "OPENAI" -> OPENAI_CATALOG
            "ANTHROPIC" -> ANTHROPIC_CATALOG
            else -> MOCK_CATALOG
        }
    }

    fun getModelDescription(modelId: String): String? {
        val clean = ModelIdNormalizer.normalize(modelId)
        val all = GEMINI_CATALOG + OPENAI_CATALOG + ANTHROPIC_CATALOG + MOCK_CATALOG
        return all.firstOrNull { it.id.equals(clean, ignoreCase = true) }?.description
    }
}
