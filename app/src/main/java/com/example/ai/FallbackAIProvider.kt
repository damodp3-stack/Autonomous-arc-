package com.example.ai

import com.example.data.MessageEntity

class FallbackAIProvider(
    private val primary: AIProvider,
    private val fallback: AIProvider,
    private val onFallbackTriggered: ((String, Throwable) -> Unit)? = null
) : AIProvider {
    private var lastUsedProvider: AIProvider = primary

    override suspend fun generateResponse(
        prompt: String,
        context: List<MessageEntity>,
        projectContext: ProjectContext?
    ): String {
        lastUsedProvider = primary
        return try {
            val response = primary.generateResponse(prompt, context, projectContext)
            val isRecoverableError = response.startsWith("Error:") && (
                response.contains("rate limit", ignoreCase = true) ||
                response.contains("quota exceeded", ignoreCase = true) ||
                response.contains("authentication failed", ignoreCase = true) ||
                response.contains("missing or invalid", ignoreCase = true) ||
                response.contains("server error", ignoreCase = true) ||
                response.contains("timed out", ignoreCase = true) ||
                response.contains("temporarily unavailable", ignoreCase = true)
            )

            if (isRecoverableError) {
                onFallbackTriggered?.invoke("Primary returned error: $response. Falling back.", RuntimeException(response))
                lastUsedProvider = fallback
                fallback.generateResponse(prompt, context, projectContext)
            } else {
                response
            }
        } catch (e: Exception) {
            onFallbackTriggered?.invoke("Primary provider failed: ${e.message}. Falling back.", e)
            lastUsedProvider = fallback
            fallback.generateResponse(prompt, context, projectContext)
        }
    }

    override suspend fun proposeCodeChanges(
        request: String,
        projectContext: ProjectContext
    ): CodeChangeProposal {
        lastUsedProvider = primary
        return try {
            primary.proposeCodeChanges(request, projectContext)
        } catch (e: Exception) {
            onFallbackTriggered?.invoke("Primary proposeCodeChanges failed: ${e.message}. Falling back.", e)
            lastUsedProvider = fallback
            fallback.proposeCodeChanges(request, projectContext)
        }
    }

    override fun getLatestUsage(): TokenUsage? = lastUsedProvider.getLatestUsage()
}
