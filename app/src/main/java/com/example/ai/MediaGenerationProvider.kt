package com.example.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import com.example.BuildConfig
import com.example.data.MediaEntity
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

enum class MediaCapability {
    IMAGE,
    VIDEO,
    TEXT_TO_IMAGE,
    IMAGE_TO_IMAGE,
    TEXT_TO_VIDEO,
    IMAGE_TO_VIDEO
}

data class MediaGenerationRequest(
    val prompt: String,
    val capability: MediaCapability = MediaCapability.TEXT_TO_IMAGE,
    val model: String? = null,
    val aspectRatio: String = "1:1",
    val projectAssociation: String? = null,
    val chatAssociation: String? = null
)

data class MediaGenerationResult(
    val mediaEntity: MediaEntity,
    val costOrUsage: String? = null,
    val modelUsed: String
)

interface MediaGenerationProvider {
    val providerName: String
    val supportedCapabilities: Set<MediaCapability>
    suspend fun getAvailableModels(): List<String>
    suspend fun generateMedia(request: MediaGenerationRequest): Result<MediaGenerationResult>
}

class GeminiMediaGenerationProvider(
    private val mediaRepository: MediaRepository,
    private val apiKeyManager: APIKeyManager,
    private val apiService: GeminiApiService = GeminiRetrofitClient.service
) : MediaGenerationProvider {

    override val providerName: String = "Gemini Media Provider"

    override val supportedCapabilities: Set<MediaCapability> = setOf(
        MediaCapability.IMAGE,
        MediaCapability.TEXT_TO_IMAGE
    )

    override suspend fun getAvailableModels(): List<String> {
        return listOf(
            "gemini-2.5-flash-image",
            "gemini-3.1-flash-image-preview",
            "gemini-3-pro-image-preview"
        )
    }

    override suspend fun generateMedia(request: MediaGenerationRequest): Result<MediaGenerationResult> = withContext(Dispatchers.IO) {
        if (request.capability != MediaCapability.IMAGE && request.capability != MediaCapability.TEXT_TO_IMAGE) {
            return@withContext Result.failure(
                UnsupportedOperationException("Capability ${request.capability} is not supported by GeminiMediaGenerationProvider.")
            )
        }

        if (request.prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt cannot be blank for media generation."))
        }

        val apiKey = apiKeyManager.getApiKey("GEMINI")
            ?.ifBlank { null }
            ?: BuildConfig.GEMINI_API_KEY.ifBlank { null }

        if (apiKey == null || apiKey == "MY_GEMINI_API_KEY" || apiKey == "YOUR_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is missing or unconfigured. Please configure a valid key in Settings.")
            )
        }

        val rawModel = request.model?.ifBlank { null } ?: "gemini-2.5-flash-image"
        val modelToUse = ModelIdNormalizer.normalize(rawModel)

        val generateRequest = GenerateContentRequest(
            contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(Part(text = request.prompt))
                )
            ),
            generationConfig = GenerationConfig(
                responseModalities = listOf("TEXT", "IMAGE"),
                imageConfig = GeminiImageConfig(aspectRatio = request.aspectRatio)
            )
        )

        try {
            val response = apiService.generateContent(modelToUse, apiKey, generateRequest)
            val parts = response.candidates?.firstOrNull()?.content?.parts.orEmpty()
            val imagePart = parts.firstOrNull { it.inlineData?.data != null }

            if (imagePart?.inlineData?.data == null) {
                val textMsg = parts.firstOrNull { it.text != null }?.text
                val reason = if (!textMsg.isNullOrBlank()) ": $textMsg" else ""
                return@withContext Result.failure(
                    IllegalStateException("Model responded without image data$reason")
                )
            }

            val mimeType = imagePart.inlineData.mimeType ?: "image/png"
            val extension = if (mimeType.contains("jpeg") || mimeType.contains("jpg")) "jpg" else "png"
            val base64Data = imagePart.inlineData.data
            val imageBytes = try {
                Base64.decode(base64Data, Base64.DEFAULT)
            } catch (e: Throwable) {
                java.util.Base64.getDecoder().decode(base64Data)
            }

            val safePromptSlug = request.prompt
                .take(20)
                .replace(Regex("[^a-zA-Z0-9]"), "_")
                .trim('_')
                .ifBlank { "generated" }

            val filename = "${safePromptSlug}_${System.currentTimeMillis()}.$extension"

            val saveResult = mediaRepository.saveMedia(
                filename = filename,
                mediaType = "IMAGE",
                bytes = imageBytes,
                projectAssociation = request.projectAssociation,
                chatAssociation = request.chatAssociation
            )

            if (saveResult.isSuccess) {
                val entity = saveResult.getOrThrow()
                Result.success(
                    MediaGenerationResult(
                        mediaEntity = entity,
                        costOrUsage = "Model: $modelToUse (${imageBytes.size / 1024} KB)",
                        modelUsed = modelToUse
                    )
                )
            } else {
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to persist media entity."))
            }
        } catch (e: retrofit2.HttpException) {
            val detailedMsg = when (e.code()) {
                401, 403 -> "Authentication failed (HTTP ${e.code()}): Invalid or unauthorized Gemini API key for image generation."
                404 -> "Image generation model '$modelToUse' was not found or is unsupported on the current API (HTTP 404)."
                429 -> "Quota limit reached for image generation (HTTP 429). The Gemini Free tier does not currently permit image generation on this API key. Paid billing or an upgraded project plan is required."
                in 500..599 -> "Gemini server error during media generation (HTTP ${e.code()})."
                else -> "Gemini API error (${e.code()}): ${e.message()}"
            }
            Result.failure(Exception(detailedMsg, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class MockMediaGenerationProvider(
    private val mediaRepository: MediaRepository
) : MediaGenerationProvider {
    override val providerName: String = "Mock Media Generator"

    override val supportedCapabilities: Set<MediaCapability> = setOf(
        MediaCapability.IMAGE,
        MediaCapability.TEXT_TO_IMAGE
    )

    override suspend fun getAvailableModels(): List<String> = listOf("mock-image-v1")

    override suspend fun generateMedia(request: MediaGenerationRequest): Result<MediaGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val bytes = try {
                val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint().apply {
                    color = Color.rgb(25, 45, 75)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, 400f, 300f, paint)

                paint.apply {
                    color = Color.rgb(100, 180, 255)
                    textSize = 20f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("Generated Asset (Mock)", 200f, 130f, paint)

                paint.apply {
                    color = Color.WHITE
                    textSize = 14f
                }
                canvas.drawText(request.prompt.take(30), 200f, 170f, paint)

                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            } catch (e: Throwable) {
                "MOCK_PNG_IMAGE_DATA_FOR_${request.prompt}".toByteArray(Charsets.UTF_8)
            }

            val filename = "mock_${System.currentTimeMillis()}.png"
            val saveResult = mediaRepository.saveMedia(
                filename = filename,
                mediaType = "IMAGE",
                bytes = bytes,
                projectAssociation = request.projectAssociation,
                chatAssociation = request.chatAssociation
            )

            if (saveResult.isSuccess) {
                Result.success(
                    MediaGenerationResult(
                        mediaEntity = saveResult.getOrThrow(),
                        costOrUsage = "[Mock Mode] Offline Mock Generation",
                        modelUsed = "mock-image-v1"
                    )
                )
            } else {
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save mock media"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
