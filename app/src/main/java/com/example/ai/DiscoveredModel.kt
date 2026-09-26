package com.example.ai

import com.squareup.moshi.JsonClass

/**
 * Represents an AI model dynamically discovered from a provider's live API.
 */
@JsonClass(generateAdapter = true)
data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val supportedGenerationMethods: List<String> = emptyList(),
    val isImageGeneration: Boolean = false,
    val inputTokenLimit: Int? = null,
    val outputTokenLimit: Int? = null,
    val providerType: String = "GEMINI",
    val isAvailable: Boolean = true
)
