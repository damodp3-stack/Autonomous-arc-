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
data class AnthropicMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class AnthropicRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<AnthropicMessage>,
    @Json(name = "system") val system: String? = null,
    @Json(name = "max_tokens") val maxTokens: Int = 4096
)

@JsonClass(generateAdapter = true)
data class AnthropicContent(
    @Json(name = "type") val type: String,
    @Json(name = "text") val text: String
)

@JsonClass(generateAdapter = true)
data class AnthropicResponse(
    @Json(name = "content") val content: List<AnthropicContent>?
)

interface AnthropicApiService {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = "2023-06-01",
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: AnthropicRequest
    ): AnthropicResponse
}

object AnthropicRetrofitClient {
    private const val BASE_URL = "https://api.anthropic.com/"

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

    val service: AnthropicApiService = retrofit.create(AnthropicApiService::class.java)
}
