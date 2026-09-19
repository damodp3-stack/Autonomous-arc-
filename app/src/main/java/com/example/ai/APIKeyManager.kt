package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig

interface APIKeyManager {
    fun saveApiKey(providerId: String, apiKey: String)
    fun getApiKey(providerId: String): String?
    fun clearApiKey(providerId: String)
    fun hasApiKey(providerId: String): Boolean = !getApiKey(providerId).isNullOrBlank()
}

class SecureAPIKeyManager(context: Context) : APIKeyManager {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_api_keys", Context.MODE_PRIVATE)

    override fun saveApiKey(providerId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        val normalizedUpper = providerId.trim().uppercase()
        prefs.edit()
            .putString("api_key_$providerId", trimmed)
            .putString("api_key_$normalizedUpper", trimmed)
            .apply()
    }

    override fun getApiKey(providerId: String): String? {
        val trimmed = providerId.trim()
        val upper = trimmed.uppercase()
        val capitalized = trimmed.lowercase().replaceFirstChar { it.uppercase() }
        val lower = trimmed.lowercase()

        val saved = prefs.getString("api_key_$trimmed", null)
            ?: prefs.getString("api_key_$upper", null)
            ?: prefs.getString("api_key_$capitalized", null)
            ?: prefs.getString("api_key_$lower", null)

        if (!saved.prematureBlankCheck()) {
            return saved
        }

        // Fallback for Gemini to BuildConfig if no user key is explicitly saved
        if (upper == "GEMINI") {
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                null
            }
            if (!buildConfigKey.isNullOrBlank() &&
                buildConfigKey != "MY_GEMINI_API_KEY" &&
                buildConfigKey != "YOUR_GEMINI_API_KEY"
            ) {
                return buildConfigKey
            }
        }

        return null
    }

    override fun clearApiKey(providerId: String) {
        val trimmed = providerId.trim()
        val upper = trimmed.uppercase()
        val capitalized = trimmed.lowercase().replaceFirstChar { it.uppercase() }
        val lower = trimmed.lowercase()

        prefs.edit()
            .remove("api_key_$trimmed")
            .remove("api_key_$upper")
            .remove("api_key_$capitalized")
            .remove("api_key_$lower")
            .apply()
    }

    private fun String?.prematureBlankCheck(): Boolean {
        return this.isNullOrBlank() || this == "MY_GEMINI_API_KEY" || this == "YOUR_GEMINI_API_KEY"
    }
}
