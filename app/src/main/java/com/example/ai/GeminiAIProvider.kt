package com.example.ai

import com.example.BuildConfig
import com.example.data.MessageEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Common Data Classes ---

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

class GeminiAIProvider(private val projectName: String) : AIProvider {
    override suspend fun generateResponse(prompt: String, context: List<MessageEntity>, projectContext: ProjectContext?): String {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
                return@withContext "Error: Gemini API key is missing or invalid. Please check your .env configuration."
            }

            // Convert conversation context to Gemini format
            val historyContents = context.map { msg ->
                Content(
                    role = if (msg.isUser) "user" else "model",
                    parts = listOf(Part(text = msg.text))
                )
            }

            val currentRequest = Content(
                role = "user",
                parts = listOf(Part(text = prompt))
            )
            
            // Limit history to last 10 messages to avoid token issues during prototyping
            val recentHistory = historyContents.takeLast(10)
            
            val contents = recentHistory + currentRequest

            val request = GenerateContentRequest(
                contents = contents,
                systemInstruction = Content(
                    role = "user",
                    parts = listOf(Part(text = buildString {
                        append("You are an AI coding assistant for the project '$projectName'. Provide helpful, concise responses. ")
                        if (projectContext?.currentOpenFile != null) {
                            append("The user is currently viewing the file '${projectContext.currentOpenFile.path}'. ")
                        }
                    }))
                )
            )

            try {
                val response = GeminiRetrofitClient.service.generateContent(apiKey, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (responseText != null) {
                    responseText
                } else {
                    "Error: Received empty response from Gemini."
                }
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 || e.code() == 403) {
                    "Error: API authentication failed or invalid API key."
                } else if (e.code() == 429) {
                    "Error: Rate limit exceeded. Please try again later."
                } else if (e.code() >= 500) {
                    "Error: Gemini server error (${e.code()})."
                } else {
                    "Error: Unexpected API response (${e.code()})."
                }
            } catch (e: java.net.UnknownHostException) {
                "Error: No internet connection or network failure."
            } catch (e: java.net.SocketTimeoutException) {
                "Error: Request timed out."
            } catch (e: Exception) {
                "Error: Network exception or unknown error occurred."
            }
        }
    }

    override suspend fun proposeCodeChanges(request: String, projectContext: ProjectContext): CodeChangeProposal {
        return withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
                throw IllegalStateException("Error: Gemini API key is missing or invalid.")
            }

            val currentRequest = Content(
                role = "user",
                parts = listOf(Part(text = request))
            )
            
            val systemInstructionText = buildString {
                append("You are the coding engine inside Autonomous Arc. ")
                append("Your task is to analyze the user's coding request and propose safe file changes. ")
                append("You must:\n")
                append("1. Understand the request.\n")
                append("2. Inspect the supplied project context.\n")
                append("3. Identify which files need changes.\n")
                append("4. Return a structured JSON change proposal matching the CodeChangeProposal schema.\n")
                append("5. Include originalContent and proposedContent for every changed file.\n")
                append("6. Never claim that a change was applied.\n")
                append("7. Never modify files directly.\n")
                append("8. Never invent files unless creating them is necessary.\n")
                append("9. Prefer the smallest correct change.\n")
                append("10. Preserve existing architecture and working functionality.\n")
                append("11. Follow existing project conventions.\n")
                append("12. If the request is ambiguous or required information is missing, explain the problem in the explanation field instead of fabricating code.\n\n")
                append("Allowed operations: CREATE, MODIFY, DELETE.\n\n")
                append("Project Context:\n")
                append("Project Name: ${projectContext.projectName}\n")
                if (projectContext.currentOpenFile != null) {
                    append("Currently viewing file: ${projectContext.currentOpenFile.path}\n")
                    append("Current file content:\n${projectContext.currentOpenFile.content}\n\n")
                }
                if (projectContext.files.isNotEmpty()) {
                    append("Other files in project:\n")
                    projectContext.files.forEach { file ->
                        if (file.id != projectContext.currentOpenFile?.id) {
                            append("- ${file.path}\n")
                        }
                    }
                }
            }

            val generateContentRequest = GenerateContentRequest(
                contents = listOf(currentRequest),
                systemInstruction = Content(
                    role = "user",
                    parts = listOf(Part(text = systemInstructionText))
                ),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )

            try {
                val response = GeminiRetrofitClient.service.generateContent(apiKey, generateContentRequest)
                var responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw IllegalStateException("Error: Received empty response from Gemini.")
                
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

                val adapter = GeminiRetrofitClient.moshi.adapter(CodeChangeProposal::class.java)
                val proposal = adapter.fromJson(responseText)
                    ?: throw IllegalStateException("Error: Failed to parse AI response into CodeChangeProposal.")
                
                // Validate paths
                val validatedChanges = proposal.changes.map { change ->
                    if (change.filePath.contains("../") || change.filePath.startsWith("/")) {
                        throw IllegalStateException("Security Error: Path traversal detected in AI proposal.")
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
