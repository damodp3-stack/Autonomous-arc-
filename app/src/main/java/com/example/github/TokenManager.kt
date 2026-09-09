package com.example.github

import android.content.Context
import android.content.SharedPreferences

interface TokenManager {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}

class SimpleTokenManager(context: Context) : TokenManager {
    private val prefs: SharedPreferences = context.getSharedPreferences("github_auth", Context.MODE_PRIVATE)

    override fun saveToken(token: String) {
        // In a production app, use EncryptedSharedPreferences from androidx.security
        prefs.edit().putString("github_token", token).apply()
    }

    override fun getToken(): String? {
        return prefs.getString("github_token", null)
    }

    override fun clearToken() {
        prefs.edit().remove("github_token").apply()
    }
}
