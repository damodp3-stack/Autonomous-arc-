package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_provider_config")
data class AIProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String, // "GEMINI", "OPENAI", "ANTHROPIC"
    val name: String,
    val selectedModel: String,
    val isActive: Boolean
)
