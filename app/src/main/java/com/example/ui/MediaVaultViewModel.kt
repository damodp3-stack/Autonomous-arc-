package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.MediaEntity
import com.example.data.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

class MediaVaultViewModel(
    private val mediaRepository: MediaRepository,
    val mediaGenerationProvider: com.example.ai.MediaGenerationProvider? = null
) : ViewModel() {

    private val _filterType = MutableStateFlow("ALL")
    val filterType: StateFlow<String> = _filterType.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isGeneratingMedia = MutableStateFlow(false)
    val isGeneratingMedia: StateFlow<Boolean> = _isGeneratingMedia.asStateFlow()

    private val _generationStatus = MutableStateFlow<String?>(null)
    val generationStatus: StateFlow<String?> = _generationStatus.asStateFlow()

    private val allMedia = mediaRepository.getAllMedia()

    val mediaItems: StateFlow<List<MediaEntity>> = combine(
        allMedia,
        _filterType,
        _searchQuery
    ) { list, filter, query ->
        list.filter { item ->
            val matchesFilter = when (filter) {
                "IMAGE" -> item.isImage
                "VIDEO" -> item.isVideo
                else -> true
            }
            val matchesQuery = query.isBlank() ||
                    item.filename.contains(query, ignoreCase = true) ||
                    (item.projectAssociation?.contains(query, ignoreCase = true) == true)
            matchesFilter && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setFilter(filter: String) {
        _filterType.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteMedia(id: String) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(id)
        }
    }

    fun renameMedia(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            mediaRepository.renameMedia(id, newName)
        }
    }

    fun isMediaFileValid(media: MediaEntity): Boolean {
        return mediaRepository.isMediaFileValid(media)
    }

    fun getMediaFile(media: MediaEntity): File? {
        return mediaRepository.getMediaFile(media)
    }

    fun generateMediaAsset(prompt: String, model: String? = null, projectAssociation: String? = null) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _isGeneratingMedia.value = true
            _generationStatus.value = "Contacting AI media provider..."
            val provider = mediaGenerationProvider ?: com.example.ai.MockMediaGenerationProvider(mediaRepository)
            val result = provider.generateMedia(
                com.example.ai.MediaGenerationRequest(
                    prompt = prompt,
                    capability = com.example.ai.MediaCapability.TEXT_TO_IMAGE,
                    model = model,
                    projectAssociation = projectAssociation
                )
            )
            if (result.isSuccess) {
                val res = result.getOrNull()
                val isMock = res?.costOrUsage?.contains("Mock", ignoreCase = true) == true || res?.modelUsed?.startsWith("mock") == true
                _generationStatus.value = if (isMock) {
                    "[Mock/Test Mode] Mock image generated via ${res?.modelUsed} and saved to Vault."
                } else {
                    "Success: Image generated via ${res?.modelUsed ?: "AI"} and saved to Vault."
                }
            } else {
                _generationStatus.value = "Generation failed: ${result.exceptionOrNull()?.message ?: "Unknown provider failure"}"
            }
            _isGeneratingMedia.value = false
        }
    }

    fun clearGenerationStatus() {
        _generationStatus.value = null
    }

    /**
     * Creates a sample image asset in the vault for immediate local testing & use.
     */
    fun createSampleMedia(title: String, isVideo: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val filename = if (isVideo) "$title.mp4" else "$title.png"
                val type = if (isVideo) "VIDEO" else "IMAGE"

                val bytes = if (isVideo) {
                    // Minimal valid container bytes for a placeholder video file
                    "AutonomousArc_Video_Placeholder_Payload".toByteArray(Charsets.UTF_8)
                } else {
                    // Generate a valid PNG image bitmap
                    val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val paint = Paint().apply {
                        color = Color.rgb(20, 35, 60)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, 400f, 300f, paint)

                    paint.apply {
                        color = Color.rgb(75, 140, 245)
                        textSize = 24f
                        isAntiAlias = true
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("Autonomous Arc", 200f, 130f, paint)

                    paint.apply {
                        color = Color.WHITE
                        textSize = 18f
                    }
                    canvas.drawText(title, 200f, 170f, paint)

                    val stream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }

                mediaRepository.saveMedia(
                    filename = filename,
                    mediaType = type,
                    bytes = bytes
                )
            }
        }
    }
}

class MediaVaultViewModelFactory(
    private val mediaRepository: MediaRepository,
    private val mediaGenerationProvider: com.example.ai.MediaGenerationProvider? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaVaultViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaVaultViewModel(mediaRepository, mediaGenerationProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
