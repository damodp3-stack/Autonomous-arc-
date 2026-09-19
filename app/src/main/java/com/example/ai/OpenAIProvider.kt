package com.example.ai

import com.example.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAIProvider(
    private val projectName: String,
    private val providedApiKey: String? = null,
    val model: String = "gpt-4o"
) : AIProvider {
    private var latestUsage: TokenUsage? = null

    override fun getLatestUsage(): TokenUsage? = latestUsage

    override suspend fun generateResponse(
        prompt: String,
        context: List<MessageEntity>,
        projectContext: ProjectContext?
    ): String {
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey ?: ""
            if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY" || apiKey == "YOUR_OPENAI_API_KEY") {
                return@withContext "Error: OpenAI API key is missing or invalid. Please set it in Settings."
            }

            val systemInstruction = buildString {
                append("You are an AI coding assistant for the project '$projectName'. Provide helpful, concise responses. ")
                if (projectContext?.currentOpenFile != null) {
                    append("The user is currently viewing the file '${projectContext.currentOpenFile.path}'. ")
                }
            }

            val messages = mutableListOf<OpenAIMessage>()
            messages.add(OpenAIMessage(role = "system", content = systemInstruction))

            // Limit history to last 10 messages
            val recentHistory = context.filter { !it.text.startsWith("AI proposed changes") }.takeLast(10)
            recentHistory.forEach { msg ->
                messages.add(OpenAIMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                ))
            }

            messages.add(OpenAIMessage(role = "user", content = prompt))

            val request = OpenAIChatRequest(
                model = model,
                messages = messages
            )

            try {
                val response = OpenAIRetrofitClient.service.createChatCompletion("Bearer $apiKey", request = request)
                latestUsage = response.usage?.let {
                    TokenUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                }
                val responseText = response.choices?.firstOrNull()?.message?.content
                
                responseText ?: "Error: Received empty response from OpenAI."
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    "Error: OpenAI authentication failed. Invalid API key."
                } else if (e.code() == 404) {
                    "Error: Model '$model' not found or unsupported by your OpenAI account."
                } else if (e.code() == 429) {
                    "Error: OpenAI rate limit or quota exceeded. Please check your account usage."
                } else if (e.code() >= 500) {
                    "Error: OpenAI server error (${e.code()})."
                } else {
                    "Error: Unexpected OpenAI API response (${e.code()})."
                }
            } catch (e: Exception) {
                "Error: Network exception or unknown error occurred: ${e.message}"
            }
        }
    }

    override suspend fun proposeCodeChanges(
        request: String,
        projectContext: ProjectContext
    ): CodeChangeProposal {
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey ?: ""
            if (apiKey.isBlank() || apiKey == "MY_OPENAI_API_KEY" || apiKey == "YOUR_OPENAI_API_KEY") {
                throw IllegalStateException("Error: OpenAI API key is missing or invalid. Please set it in Settings.")
            }

            val systemInstructionText = buildString {
                append("You are the coding engine inside Autonomous Arc. ")
                append("Your task is to analyze the user's coding request and propose safe file changes. ")
                append("You must return a structured JSON response EXACTLY matching this schema:\n")
                append("{\n")
                append("  \"summary\": \"Short description of changes\",\n")
                append("  \"explanation\": \"Detailed explanation of why these changes made\",\n")
                append("  \"changes\": [\n")
                append("    {\n")
                append("      \"filePath\": \"path/relative/to/project/file.txt\",\n")
                append("      \"operation\": \"CREATE|MODIFY|DELETE|RENAME\",\n")
                append("      \"newFilePath\": \"new/path.txt (only required for RENAME)\",\n")
                append("      \"originalContent\": \"Exact original content (required for MODIFY/DELETE)\",\n")
                append("      \"proposedContent\": \"New content (required for CREATE/MODIFY)\"\n")
                append("    }\n")
                append("  ]\n")
                append("}\n\n")
                append("Allowed operations: CREATE, MODIFY, DELETE, RENAME.\n")
                append("Include originalContent and proposedContent for every changed file.\n")
                append("Never claim that a change was applied. Never modify files directly.\n")
                append("If the request is ambiguous, explain the problem in the explanation field instead of fabricating code.\n\n")
                append("Project Context:\n")
                append("Project Name: ${projectContext.projectName}\n")
                if (projectContext.currentOpenFile != null) {
                    append("Currently viewing file: ${projectContext.currentOpenFile.path}\n")
                    append("Current file content:\n${projectContext.currentOpenFile.content}\n\n")
                }
                
                if (projectContext.files.isNotEmpty()) {
                    append("Other files in project:\n")
                    var currentContextSize = 0
                    val MAX_CONTEXT_SIZE = 100_000
                    projectContext.files.forEach { file ->
                        if (file.id != projectContext.currentOpenFile?.id) {
                            val fileHeader = "\n--- ${file.path} ---\n"
                            val fileContentSize = file.content.length
                            if (currentContextSize + fileContentSize < MAX_CONTEXT_SIZE) {
                                append(fileHeader)
                                append(file.content)
                                append("\n")
                                currentContextSize += fileContentSize
                            } else {
                                append("- ${file.path} (Content omitted due to size limits)\n")
                            }
                        }
                    }
                }
            }

            val messages = listOf(
                OpenAIMessage(role = "system", content = systemInstructionText),
                OpenAIMessage(role = "user", content = request)
            )

            val chatRequest = OpenAIChatRequest(
                model = model,
                messages = messages,
                responseFormat = OpenAIResponseFormat(type = "json_object")
            )

            try {
                val response = OpenAIRetrofitClient.service.createChatCompletion("Bearer $apiKey", request = chatRequest)
                latestUsage = response.usage?.let {
                    TokenUsage(
                        promptTokens = it.promptTokens,
                        completionTokens = it.completionTokens,
                        totalTokens = it.totalTokens
                    )
                }
                var responseText = response.choices?.firstOrNull()?.message?.content
                    ?: throw IllegalStateException("Error: Received empty response from OpenAI.")

                // Clean up possible markdown json blocks
                if (responseText.startsWith("```json")) {
                    responseText = responseText.substringAfter("```json")
                }
                if (responseText.startsWith("```")) {
                    responseText = responseText.substringAfter("```")
                }
                if (responseText.endsWith("```")) {
                    responseText = responseText.substringBeforeLast("```")
                }
                responseText = responseText.trim()

                val adapter = OpenAIRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
                val proposal = adapter.fromJson(responseText)
                    ?: throw IllegalStateException("Error: Failed to parse AI response into CodeChangeProposal.")

                // Validate proposal
                val validatedChanges = proposal.changes.map { change ->
                    val path = change.filePath
                    if (path.contains("../") || path.startsWith("/") || path.contains("\\")) {
                        throw IllegalStateException("Security Error: Invalid path in AI proposal ($path).")
                    }
                    when (change.operation) {
                        FileOperation.RENAME -> {
                            val newPath = change.newFilePath
                            if (newPath.isNullOrBlank()) {
                                throw IllegalStateException("Error: RENAME operation requires newFilePath.")
                            }
                            if (newPath.contains("../") || newPath.startsWith("/") || newPath.contains("\\")) {
                                throw IllegalStateException("Security Error: Invalid newFilePath in AI proposal ($newPath).")
                            }
                        }
                        FileOperation.CREATE -> {
                            if (change.proposedContent == null) {
                                throw IllegalStateException("Error: CREATE operation requires proposedContent.")
                            }
                        }
                        FileOperation.MODIFY -> {
                            if (change.originalContent == null || change.proposedContent == null) {
                                throw IllegalStateException("Error: MODIFY operation requires originalContent and proposedContent.")
                            }
                        }
                        FileOperation.DELETE -> {
                            // originalContent is optional but good if provided
                        }
                    }
                    change
                }

                proposal.copy(changes = validatedChanges, tokenUsage = latestUsage)

            } catch (e: retrofit2.HttpException) {
                val errorMsg = when (e.code()) {
                    401 -> "Authentication failed. Invalid OpenAI API key. Please check your key in Settings."
                    404 -> "Model '$model' was not found or is unsupported by your OpenAI account."
                    429 -> "Rate limit or quota exceeded for OpenAI. Please check your account quota or try again later."
                    in 500..599 -> "OpenAI service temporarily unavailable (${e.code()})."
                    else -> "Unexpected API response from OpenAI (${e.code()})."
                }
                throw IllegalStateException(errorMsg, e)
            } catch (e: Exception) {
                throw IllegalStateException("Failed to generate code proposal: ${e.message}", e)
            }
        }
    }
}
