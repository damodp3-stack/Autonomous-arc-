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
    suspend fun proposeCodeChanges(request: String, projectContext: ProjectContext): CodeChangeProposal
}

class MockAIProvider : AIProvider {
    override suspend fun generateResponse(prompt: String, context: List<MessageEntity>, projectContext: ProjectContext?): String {
        val fileInfo = projectContext?.currentOpenFile?.let { " Currently viewing: ${it.name}." } ?: ""
        return "Building modular architecture based on: \"$prompt\".$fileInfo\nI've added the initial scaffolding for your request.\n\n```kotlin\n// TODO: Implement requested features\n```\n\nWhat's next?"
    }

    override suspend fun proposeCodeChanges(request: String, projectContext: ProjectContext): CodeChangeProposal {
        val currentFile = projectContext.currentOpenFile?.path ?: "MainActivity.kt"
        return CodeChangeProposal(
            summary = "Mock: $request",
            explanation = "This is a mock proposal to verify the architecture.",
            changes = listOf(
                FileChange(
                    filePath = currentFile,
                    operation = FileOperation.MODIFY,
                    originalContent = projectContext.currentOpenFile?.content ?: "",
                    proposedContent = (projectContext.currentOpenFile?.content ?: "") + "\n// AI Mock Change"
                )
            )
        )
    }
}
