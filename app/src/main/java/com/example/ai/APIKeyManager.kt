package com.example.ai

import android.content.Context
import android.content.SharedPreferences

interface APIKeyManager {
    fun saveApiKey(providerId: String, apiKey: String)
    fun getApiKey(providerId: String): String?
    fun clearApiKey(providerId: String)
}

class SecureAPIKeyManager(context: Context) : APIKeyManager {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_api_keys", Context.MODE_PRIVATE)

    override fun saveApiKey(providerId: String, apiKey: String) {
        // In a production app, use EncryptedSharedPreferences
        prefs.edit().putString("api_key_$providerId", apiKey).apply()
    }

    override fun getApiKey(providerId: String): String? {
        return prefs.getString("api_key_$providerId", null)
    }

    override fun clearApiKey(providerId: String) {
        prefs.edit().remove("api_key_$providerId").apply()
    }
}
