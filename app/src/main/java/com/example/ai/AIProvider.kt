package com.example.ai

import com.example.data.MessageEntity
import com.example.data.ProjectFileEntity

data class ProjectContext(
    val projectName: String,
    val files: List<ProjectFileEntity> = emptyList(),
    val currentOpenFile: ProjectFileEntity? = null
)

interface AIProvider {
    suspend fun generateResponse(prompt: String, context: List<MessageEntity>, projectContext: ProjectContext? = null): String
}

class MockAIProvider : AIProvider {
    override suspend fun generateResponse(prompt: String, context: List<MessageEntity>, projectContext: ProjectContext?): String {
        val fileInfo = projectContext?.currentOpenFile?.let { " Currently viewing: ${it.name}." } ?: ""
        return "Building modular architecture based on: \"$prompt\".$fileInfo\nI've added the initial scaffolding for your request.\n\n```kotlin\n// TODO: Implement requested features\n```\n\nWhat's next?"
    }
}
