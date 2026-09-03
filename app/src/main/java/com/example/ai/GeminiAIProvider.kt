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
    val systemInstruction: Content? = null
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

    private val moshi = Moshi.Builder()
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
}
