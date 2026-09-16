package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenAIMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAIResponseFormat(
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class OpenAIChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<OpenAIMessage>,
    @Json(name = "response_format") val responseFormat: OpenAIResponseFormat? = null,
    @Json(name = "temperature") val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIChoice(
    @Json(name = "message") val message: OpenAIMessage
)

@JsonClass(generateAdapter = true)
data class OpenAIChatResponse(
    @Json(name = "choices") val choices: List<OpenAIChoice>?
)

interface OpenAIApiService {
    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: OpenAIChatRequest
    ): OpenAIChatResponse
}

object OpenAIRetrofitClient {
    private const val BASE_URL = "https://api.openai.com/"

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: OpenAIApiService = retrofit.create(OpenAIApiService::class.java)
}
