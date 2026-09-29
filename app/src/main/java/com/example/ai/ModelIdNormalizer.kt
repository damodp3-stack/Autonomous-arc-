package com.example.ai

/**
 * Utility for robust model ID normalization across AI providers.
 * Ensures consistent IDs and strips single or repeated "models/" prefixes.
 *
 * Example:
 * "models/gemini-3.5-flash" -> "gemini-3.5-flash"
 * "models/models/gemini-3.5-flash" -> "gemini-3.5-flash"
 * "  models/gemini-flash-latest  " -> "gemini-flash-latest"
 */
object ModelIdNormalizer {
    fun normalize(modelId: String): String {
        return modelId.trim().replace(Regex("^(models/)+"), "").trim()
    }
}
