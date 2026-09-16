package com.example.ai

import com.example.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        return withContext(Dispatchers.IO) {
            val apiKey = providedApiKey ?: ""
            if (apiKey.isBlank() || apiKey == "MY_ANTHROPIC_API_KEY" || apiKey == "YOUR_ANTHROPIC_API_KEY") {
                return@withContext "Error: Anthropic API key is missing or invalid. Please set it in Settings."
            }

            val systemInstruction = buildString {
                append("You are an AI coding assistant for the project '$projectName'. Provide helpful, concise responses. ")
                if (projectContext?.currentOpenFile != null) {
                    append("The user is currently viewing the file '${projectContext.currentOpenFile.path}'. ")
                }
            }

            val messages = mutableListOf<AnthropicMessage>()

            // Limit history to last 10 messages
            val recentHistory = context.filter { !it.text.startsWith("AI proposed changes") }.takeLast(10)
            recentHistory.forEach { msg ->
                messages.add(AnthropicMessage(
                    role = if (msg.isUser) "user" else "assistant",
                    content = msg.text
                ))
            }

            messages.add(AnthropicMessage(role = "user", content = prompt))

            val request = AnthropicRequest(
                model = model,
                messages = messages,
                system = systemInstruction
            )

            try {
                val response = AnthropicRetrofitClient.service.createMessage(apiKey = apiKey, request = request)
                val responseText = response.content?.firstOrNull()?.text
                
                responseText ?: "Error: Received empty response from Anthropic."
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    "Error: API authentication failed or invalid API key."
                } else if (e.code() == 429) {
                    "Error: Rate limit exceeded. Please try again later."
                } else {
                    "Error: Unexpected API response (${e.code()})."
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
            if (apiKey.isBlank() || apiKey == "MY_ANTHROPIC_API_KEY" || apiKey == "YOUR_ANTHROPIC_API_KEY") {
                throw IllegalStateException("Error: Anthropic API key is missing or invalid. Please set it in Settings.")
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
                
                // Add explicit instruction to only output JSON for Claude
                append("\n\nIMPORTANT: Output ONLY valid JSON matching the schema above. Do not include any markdown formatting like ```json or any other text before or after the JSON object.")
            }

            val messages = listOf(
                AnthropicMessage(role = "user", content = request)
            )

            val apiRequest = AnthropicRequest(
                model = model,
                messages = messages,
                system = systemInstructionText
            )

            try {
                val response = AnthropicRetrofitClient.service.createMessage(apiKey = apiKey, request = apiRequest)
                var responseText = response.content?.firstOrNull()?.text
                    ?: throw IllegalStateException("Error: Received empty response from Anthropic.")

                // Clean up possible markdown json blocks just in case
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

                val adapter = AnthropicRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
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

                proposal.copy(changes = validatedChanges)

            } catch (e: Exception) {
                throw IllegalStateException("Failed to generate code proposal: ${e.message}", e)
            }
        }
    }
}
