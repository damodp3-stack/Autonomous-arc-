package com.example.ai

import com.example.data.MessageEntity

interface AIProvider {
    suspend fun generateResponse(prompt: String, context: List<MessageEntity>): String
}

class MockAIProvider : AIProvider {
    override suspend fun generateResponse(prompt: String, context: List<MessageEntity>): String {
        return "Building modular architecture based on: \"$prompt\".\nI've added the initial scaffolding for your request.\n\n```kotlin\n// TODO: Implement requested features\n```\n\nWhat's next?"
    }
}
