package com.example.ai

import com.example.data.MessageEntity

class OpenAIProvider(
    private val projectName: String,
    private val providedApiKey: String? = null,
    private val model: String = "gpt-4o"
) : AIProvider {
    override suspend fun generateResponse(
        prompt: String,
        context: List<MessageEntity>,
        projectContext: ProjectContext?
    ): String {
        return "OpenAI is not yet fully implemented. Configured model: $model"
    }

    override suspend fun proposeCodeChanges(
        request: String,
        projectContext: ProjectContext
    ): CodeChangeProposal {
        throw UnsupportedOperationException("OpenAI proposal generation not yet implemented.")
    }
}

class AnthropicProvider(
    private val projectName: String,
    private val providedApiKey: String? = null,
    private val model: String = "claude-3-5-sonnet-20240620"
) : AIProvider {
    override suspend fun generateResponse(
        prompt: String,
        context: List<MessageEntity>,
        projectContext: ProjectContext?
    ): String {
        return "Anthropic is not yet fully implemented. Configured model: $model"
    }

    override suspend fun proposeCodeChanges(
        request: String,
        projectContext: ProjectContext
    ): CodeChangeProposal {
        throw UnsupportedOperationException("Anthropic proposal generation not yet implemented.")
    }
}
