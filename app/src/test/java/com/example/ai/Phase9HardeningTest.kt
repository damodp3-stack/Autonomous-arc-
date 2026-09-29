package com.example.ai

import com.example.data.*
import com.example.sync.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class Phase9HardeningTest {

    // --- Mock / Fake Implementations for Testing ---

    private class FakeGeminiApiService(
        private val listModelsResponse: GeminiListModelsResponse? = null,
        private val listModelsException: Exception? = null,
        private val generateContentResponse: GenerateContentResponse? = null,
        private val generateContentException: Exception? = null
    ) : GeminiApiService {
        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GenerateContentRequest
        ): GenerateContentResponse {
            if (generateContentException != null) throw generateContentException
            return generateContentResponse ?: GenerateContentResponse(
                candidates = listOf(Candidate(content = Content(parts = listOf(Part(text = "pong")))))
            )
        }

        override suspend fun listModels(
            apiKey: String,
            pageSize: Int,
            pageToken: String?
        ): GeminiListModelsResponse {
            if (listModelsException != null) throw listModelsException
            return listModelsResponse ?: GeminiListModelsResponse(models = emptyList())
        }
    }

    private class FakeMediaDao : MediaDao {
        val mediaMap = mutableMapOf<String, MediaEntity>()
        override fun getAllMedia(): Flow<List<MediaEntity>> = flowOf(mediaMap.values.toList())
        override fun getMediaByType(type: String): Flow<List<MediaEntity>> =
            flowOf(mediaMap.values.filter { it.mediaType.equals(type, ignoreCase = true) })
        override fun getMediaForProject(projectId: String): Flow<List<MediaEntity>> =
            flowOf(mediaMap.values.filter { it.projectAssociation == projectId })
        override suspend fun getMediaById(id: String): MediaEntity? = mediaMap[id]
        override suspend fun insert(media: MediaEntity) { mediaMap[media.id] = media }
        override suspend fun update(media: MediaEntity) { mediaMap[media.id] = media }
        override suspend fun delete(media: MediaEntity) { mediaMap.remove(media.id) }
        override suspend fun deleteById(id: String) { mediaMap.remove(id) }
        override suspend fun getMediaCount(): Int = mediaMap.size
    }

    private class FakeMediaRepo(private val dao: FakeMediaDao) : MediaRepository {
        override fun getAllMedia(): Flow<List<MediaEntity>> = dao.getAllMedia()
        override fun getMediaByType(type: String): Flow<List<MediaEntity>> = dao.getMediaByType(type)
        override fun getMediaForProject(projectId: String): Flow<List<MediaEntity>> = dao.getMediaForProject(projectId)
        override suspend fun getMediaById(id: String): MediaEntity? = dao.getMediaById(id)
        override suspend fun saveMedia(
            filename: String,
            mediaType: String,
            bytes: ByteArray,
            projectAssociation: String?,
            chatAssociation: String?
        ): Result<MediaEntity> {
            val entity = MediaEntity(
                id = "media_${System.currentTimeMillis()}_${(1..1000).random()}",
                filename = filename,
                mediaType = mediaType,
                createdTimestamp = System.currentTimeMillis(),
                filePath = "/tmp/$filename",
                fileSizeBytes = bytes.size.toLong(),
                projectAssociation = projectAssociation,
                chatAssociation = chatAssociation
            )
            dao.insert(entity)
            return Result.success(entity)
        }
        override suspend fun renameMedia(id: String, newFilename: String): Result<MediaEntity> {
            val existing = dao.getMediaById(id) ?: return Result.failure(Exception("Not found"))
            val updated = existing.copy(filename = newFilename)
            dao.update(updated)
            return Result.success(updated)
        }
        override suspend fun deleteMedia(id: String): Boolean {
            dao.deleteById(id)
            return true
        }
        override suspend fun updateProjectAssociation(id: String, projectId: String?): Boolean {
            val existing = dao.getMediaById(id) ?: return false
            dao.update(existing.copy(projectAssociation = projectId))
            return true
        }
        override fun getMediaFile(media: MediaEntity) = null
        override fun isMediaFileValid(media: MediaEntity) = true
    }

    private class InMemorySyncMetadataDao : SyncMetadataDao {
        val data = mutableMapOf<String, SyncMetadataEntity>()
        override fun getPendingCountFlow(): Flow<Int> =
            flowOf(data.values.count { it.syncStatus == SyncStatus.PENDING_UPLOAD.name || it.syncStatus == SyncStatus.CONFLICT.name })
        override fun getPendingMetadataFlow(): Flow<List<SyncMetadataEntity>> =
            flowOf(data.values.filter { it.syncStatus == SyncStatus.PENDING_UPLOAD.name || it.syncStatus == SyncStatus.CONFLICT.name })
        override fun getAllMetadataFlow(): Flow<List<SyncMetadataEntity>> =
            flowOf(data.values.toList())
        override suspend fun getMetadata(entityType: String, localId: String): SyncMetadataEntity? =
            data["$entityType:$localId"]
        override suspend fun getByStatus(status: String): List<SyncMetadataEntity> =
            data.values.filter { it.syncStatus == status }
        override suspend fun insertOrUpdate(metadata: SyncMetadataEntity) {
            data["${metadata.entityType}:${metadata.localId}"] = metadata
        }
        override suspend fun update(metadata: SyncMetadataEntity) {
            data["${metadata.entityType}:${metadata.localId}"] = metadata
        }
        override suspend fun deleteMetadata(entityType: String, localId: String) {
            data.remove("$entityType:$localId")
        }
    }

    private class FakeProjectDao : ProjectDao {
        val projects = mutableMapOf<String, ProjectEntity>()
        override fun getAllProjects(): Flow<List<ProjectEntity>> = flowOf(projects.values.toList())
        override fun getProject(projectId: String): Flow<ProjectEntity?> = flowOf(projects[projectId])
        override suspend fun getProjectById(projectId: String): ProjectEntity? = projects[projectId]
        override suspend fun insertProject(project: ProjectEntity) { projects[project.id] = project }
        override suspend fun updateProject(project: ProjectEntity) { projects[project.id] = project }
        override suspend fun deleteProject(projectId: String) { projects.remove(projectId) }
    }

    private class FakeIdeaDao : IdeaDao {
        val ideas = mutableMapOf<String, IdeaEntity>()
        override fun getAllIdeas(): Flow<List<IdeaEntity>> = flowOf(ideas.values.toList())
        override fun getIdeasByStatus(status: String): Flow<List<IdeaEntity>> =
            flowOf(ideas.values.filter { it.status == status })
        override fun getIdeasForProject(projectId: String): Flow<List<IdeaEntity>> =
            flowOf(ideas.values.filter { it.projectAssociation == projectId })
        override suspend fun getIdeaById(id: String): IdeaEntity? = ideas[id]
        override fun searchIdeas(query: String): Flow<List<IdeaEntity>> =
            flowOf(ideas.values.filter { it.title.contains(query, ignoreCase = true) })
        override suspend fun insert(idea: IdeaEntity) { ideas[idea.id] = idea }
        override suspend fun update(idea: IdeaEntity) { ideas[idea.id] = idea }
        override suspend fun delete(idea: IdeaEntity) { ideas.remove(idea.id) }
        override suspend fun deleteById(id: String) { ideas.remove(id) }
        override suspend fun getIdeaCount(): Int = ideas.size
    }

    // --- 1. MODEL DISCOVERY TESTS ---

    @Test
    fun testGeminiModelDiscoveryFiltering() = runBlocking {
        val rawModels = listOf(
            GeminiModelDto(
                name = "models/gemini-3.1-flash-lite-preview",
                displayName = "Gemini 3.1 Flash Lite",
                description = "Ultra-fast preview",
                supportedGenerationMethods = listOf("generateContent", "countTokens")
            ),
            GeminiModelDto(
                name = "models/gemini-2.0-flash", // Should be filtered out (prohibited/obsolete)
                displayName = "Obsolete 2.0 Flash",
                supportedGenerationMethods = listOf("generateContent")
            ),
            GeminiModelDto(
                name = "models/gemini-1.5-pro", // Should be filtered out (prohibited)
                displayName = "Obsolete 1.5 Pro",
                supportedGenerationMethods = listOf("generateContent")
            ),
            GeminiModelDto(
                name = "models/text-embedding-004", // Should be filtered out (embedding)
                displayName = "Embedding Model",
                supportedGenerationMethods = listOf("embedContent")
            ),
            GeminiModelDto(
                name = "models/gemini-2.5-flash-image", // Should be kept and marked as image gen
                displayName = "Gemini 2.5 Flash Image",
                supportedGenerationMethods = listOf("generateContent")
            )
        )

        val apiService = FakeGeminiApiService(listModelsResponse = GeminiListModelsResponse(models = rawModels))
        val provider = GeminiModelDiscoveryProvider(apiService)

        val result = provider.discoverModels("test-key")
        assertTrue(result.isSuccess)
        val models = result.getOrNull()!!

        // Assert obsolete models are filtered out
        assertFalse(models.any { it.id == "gemini-2.0-flash" })
        assertFalse(models.any { it.id == "gemini-1.5-pro" })
        assertFalse(models.any { it.id.contains("embedding") })

        // Assert valid models are present
        assertTrue(models.any { it.id == "gemini-3.1-flash-lite-preview" })
        val imageModel = models.firstOrNull { it.id == "gemini-2.5-flash-image" }
        assertNotNull(imageModel)
        assertTrue(imageModel!!.isImageGeneration)
    }

    @Test
    fun testGeminiModelDiscoveryEmptyKey() = runBlocking {
        val apiService = FakeGeminiApiService()
        val provider = GeminiModelDiscoveryProvider(apiService)
        val result = provider.discoverModels("")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("missing or invalid") == true)
    }

    @Test
    fun testModelDiscoveryNetworkFailureCachedFallback() = runBlocking {
        val repo = RealModelSelectionRepository(
            providers = mapOf(
                "GEMINI" to object : ModelDiscoveryProvider {
                    override val providerType = "GEMINI"
                    var count = 0
                    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> {
                        count++
                        return if (count == 1) {
                            Result.success(listOf(
                                DiscoveredModel(id = "gemini-live-test", displayName = "Live Test", providerType = "GEMINI")
                            ))
                        } else {
                            Result.failure(Exception("Network unreachable"))
                        }
                    }
                }
            )
        )

        // First call succeeds and caches
        val res1 = repo.refreshModels("GEMINI", "key1")
        assertTrue(res1.isSuccess)
        assertEquals("gemini-live-test", res1.getOrNull()!!.first().id)

        // Second call fails over network, but repository returns cached models
        val res2 = repo.refreshModels("GEMINI", "key1")
        assertTrue(res2.isFailure)
        val state = repo.getModelsState("GEMINI").value
        assertTrue(state.isFromCache)
        assertEquals(1, state.models.size)
        assertEquals("gemini-live-test", state.models.first().id)
        assertTrue(state.errorMessage?.contains("Using last cached models") == true)
    }

    // --- 2. CONNECTION DIAGNOSTIC ERROR CATEGORIZATION ---

    @Test
    fun testConnectionDiagnostic404ModelError() = runBlocking {
        val notFoundResponse = Response.error<GenerateContentResponse>(
            404,
            "{\"error\":{\"code\":404,\"message\":\"models/gemini-2.0-flash is not found\"}}".toResponseBody("application/json".toMediaType())
        )
        val apiService = FakeGeminiApiService(generateContentException = HttpException(notFoundResponse))

        // When GeminiAIProvider encounters 404, message is clearly not found/unsupported
        val provider = GeminiAIProvider("TestProj", "dummy-key", "gemini-2.0-flash", apiService = apiService)
        val response = provider.generateResponse("ping", emptyList(), null)
        assertTrue(response.contains("not found or is unsupported"))
        assertTrue(response.contains("gemini-2.0-flash"))
    }

    @Test
    fun testConnectionDiagnostic401AuthError() = runBlocking {
        val unauthResponse = Response.error<GenerateContentResponse>(
            401,
            "{\"error\":{\"code\":401,\"message\":\"API_KEY_INVALID\"}}".toResponseBody("application/json".toMediaType())
        )
        val apiService = FakeGeminiApiService(generateContentException = HttpException(unauthResponse))
        val provider = GeminiAIProvider("TestProj", "invalid-key", "gemini-3.1-flash-lite-preview", apiService = apiService)
        // We test with an invalid key response
        val response = provider.generateResponse("ping", emptyList(), null)
        assertTrue(response.contains("API authentication failed") || response.contains("API key"))
    }

    // --- 3. MODEL SELECTION & FALLBACK TESTS ---

    @Test
    fun testCompatibleFallbackSelection() {
        val repo = RealModelSelectionRepository()
        val available = listOf(
            DiscoveredModel(id = "gemini-3.1-flash-lite-preview", displayName = "Flash Lite", providerType = "GEMINI"),
            DiscoveredModel(id = "gemini-flash-latest", displayName = "Flash Latest", providerType = "GEMINI"),
            DiscoveredModel(id = "gemini-2.5-flash-image", displayName = "Flash Image", isImageGeneration = true, providerType = "GEMINI")
        )

        // Unavailable text model "gemini-2.0-flash" falls back to compatible text model
        val fallbackText = repo.getCompatibleFallback("GEMINI", "gemini-2.0-flash", available)
        assertNotNull(fallbackText)
        assertEquals("gemini-3.1-flash-lite-preview", fallbackText!!.id)
        assertFalse(fallbackText.isImageGeneration)

        // Unavailable image model falls back to compatible image model
        val fallbackImage = repo.getCompatibleFallback("GEMINI", "gemini-2.0-flash-image", available)
        assertNotNull(fallbackImage)
        assertEquals("gemini-2.5-flash-image", fallbackImage!!.id)
        assertTrue(fallbackImage.isImageGeneration)
    }

    // --- 4. MEDIA GENERATION FOUNDATION TESTS ---

    @Test
    fun testMockMediaGenerationProviderSuccess() = runBlocking {
        val dao = FakeMediaDao()
        val repo = FakeMediaRepo(dao)
        val provider = MockMediaGenerationProvider(repo)

        assertEquals("Mock Media Generator", provider.providerName)
        assertTrue(provider.supportedCapabilities.contains(MediaCapability.IMAGE))
        assertTrue(provider.supportedCapabilities.contains(MediaCapability.TEXT_TO_IMAGE))

        val request = MediaGenerationRequest(
            prompt = "A glowing blue crystal of coding intelligence",
            capability = MediaCapability.TEXT_TO_IMAGE
        )

        val result = provider.generateMedia(request)
        assertTrue(result.isSuccess)
        val genResult = result.getOrNull()!!
        assertEquals("mock-image-v1", genResult.modelUsed)
        assertTrue(genResult.mediaEntity.filename.startsWith("mock_"))

        // Persisted in media repository
        val inDao = dao.getMediaById(genResult.mediaEntity.id)
        assertNotNull(inDao)
        assertEquals("IMAGE", inDao!!.mediaType)
    }

    @Test
    fun testGeminiMediaGenerationUnsupportedCapability() = runBlocking {
        val dao = FakeMediaDao()
        val repo = FakeMediaRepo(dao)
        val keyManager = object : APIKeyManager {
            override fun getApiKey(providerId: String) = "key"
            override fun saveApiKey(providerId: String, apiKey: String) {}
            override fun clearApiKey(providerId: String) {}
            override fun hasApiKey(providerId: String) = true
        }
        val provider = GeminiMediaGenerationProvider(repo, keyManager)

        val request = MediaGenerationRequest(
            prompt = "A video of a drone",
            capability = MediaCapability.TEXT_TO_VIDEO
        )
        val result = provider.generateMedia(request)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    // --- 5. CLOUD SYNC & CONFLICT TESTS ---

    @Test
    fun testCloudSyncOfflineMode() = runBlocking {
        val metaDao = InMemorySyncMetadataDao()
        val projectDao = FakeProjectDao()
        val ideaDao = FakeIdeaDao()
        val mediaDao = FakeMediaDao()

        val repo = RealSyncRepository(metaDao, projectDao, ideaDao, mediaDao, LocalOnlySyncProvider())
        val summary = repo.syncAll()
        assertEquals(0, summary.uploaded)
        assertEquals(0, summary.downloaded)
        assertTrue(summary.errors.first().contains("Offline-first"))
    }

    @Test
    fun testCloudSyncConflictStrategies() = runBlocking {
        val metaDao = InMemorySyncMetadataDao()
        val projectDao = FakeProjectDao()
        val ideaDao = FakeIdeaDao()
        val mediaDao = FakeMediaDao()

        projectDao.insertProject(ProjectEntity(id = "p1", name = "Local Project", createdAt = 1000L, updatedAt = 2000L))
        metaDao.insertOrUpdate(SyncMetadataEntity(entityType = "PROJECT", localId = "p1", localModifiedTimestamp = 2000L, syncStatus = SyncStatus.PENDING_UPLOAD.name))

        val mockCloud = MockCloudSyncProvider()
        mockCloud.simulatedConflictKey = "PROJECT:p1"

        val repo = RealSyncRepository(metaDao, projectDao, ideaDao, mediaDao, mockCloud)

        // Case 1: MANUAL strategy leaves it in CONFLICT status
        val summaryManual = repo.syncAll(strategy = ConflictResolutionStrategy.MANUAL)
        assertEquals(1, summaryManual.conflicts)
        val metaManual = metaDao.getMetadata("PROJECT", "p1")
        assertEquals(SyncStatus.CONFLICT.name, metaManual?.syncStatus)

        // Reset to pending
        metaDao.insertOrUpdate(metaManual!!.copy(syncStatus = SyncStatus.PENDING_UPLOAD.name))

        // Case 2: CLIENT_WINS strategy marks synced
        val summaryClient = repo.syncAll(strategy = ConflictResolutionStrategy.CLIENT_WINS)
        val metaClient = metaDao.getMetadata("PROJECT", "p1")
        assertEquals(SyncStatus.SYNCED.name, metaClient?.syncStatus)
    }

    // --- 6. MODEL ID NORMALIZATION TESTS ---

    @Test
    fun testModelIdNormalization() {
        assertEquals("gemini-3.5-flash", ModelIdNormalizer.normalize("gemini-3.5-flash"))
        assertEquals("gemini-3.5-flash", ModelIdNormalizer.normalize("models/gemini-3.5-flash"))
        assertEquals("gemini-3.5-flash", ModelIdNormalizer.normalize("models/models/gemini-3.5-flash"))
        assertEquals("gemini-flash-latest", ModelIdNormalizer.normalize("models/models/models/gemini-flash-latest"))
        assertEquals("gemini-flash-latest", ModelIdNormalizer.normalize("  models/gemini-flash-latest  "))
        assertEquals("gpt-4o", ModelIdNormalizer.normalize("  gpt-4o  "))
    }

    // --- 7. DYNAMIC DISCOVERY ERROR & SAFETY TESTS ---

    @Test
    fun testFirstDiscoveryFailureNeverPretendsStaticCatalogIsLive() = runBlocking {
        val repo = RealModelSelectionRepository(
            providers = mapOf(
                "GEMINI" to object : ModelDiscoveryProvider {
                    override val providerType = "GEMINI"
                    override suspend fun discoverModels(apiKey: String): Result<List<DiscoveredModel>> {
                        return Result.failure(Exception("HTTP 500: Internal server error"))
                    }
                }
            )
        )

        val result = repo.refreshModels("GEMINI", "any-key")
        assertTrue(result.isFailure)

        val state = repo.getModelsState("GEMINI").value
        assertTrue(state.models.isEmpty())
        assertFalse(state.isFromCache)
        assertEquals("Model availability not verified", state.statusMessage)
        assertTrue(state.errorMessage?.contains("500") == true)

        val cached = repo.getCachedModels("GEMINI")
        assertTrue(cached.isEmpty())
    }

    @Test
    fun testFallbackStrictCapabilityIsolation() {
        val repo = RealModelSelectionRepository()

        // 1. Text-only discovered models
        val textOnlyModels = listOf(
            DiscoveredModel(id = "model-text-a", displayName = "Text A", isImageGeneration = false, providerType = "GEMINI"),
            DiscoveredModel(id = "model-text-b", displayName = "Text B", isImageGeneration = false, providerType = "GEMINI")
        )

        // Requesting an image model when only text models are discovered MUST return null (fail safely)
        val imageFallback = repo.getCompatibleFallback("GEMINI", "gemini-flash-image", textOnlyModels)
        assertNull(imageFallback)

        // 2. Image-only discovered models
        val imageOnlyModels = listOf(
            DiscoveredModel(id = "model-image-a", displayName = "Image A", isImageGeneration = true, providerType = "GEMINI")
        )

        // Requesting a text/coding model when only image models are discovered MUST return null (fail safely)
        val textFallback = repo.getCompatibleFallback("GEMINI", "gemini-flash-latest", imageOnlyModels)
        assertNull(textFallback)
    }

    @Test
    fun testGeminiDiscoveryHttpErrorCodes() = runBlocking {
        fun makeErrorService(code: Int, message: String): GeminiApiService {
            val response = Response.error<GeminiListModelsResponse>(
                code,
                "{\"error\":{\"code\":$code,\"message\":\"$message\"}}".toResponseBody("application/json".toMediaType())
            )
            return FakeGeminiApiService(listModelsException = HttpException(response))
        }

        val p401 = GeminiModelDiscoveryProvider(makeErrorService(401, "API_KEY_INVALID"))
        val r401 = p401.discoverModels("bad-key")
        assertTrue(r401.isFailure)
        assertTrue(r401.exceptionOrNull()?.message?.contains("Authentication failed") == true)

        val p403 = GeminiModelDiscoveryProvider(makeErrorService(403, "PERMISSION_DENIED"))
        val r403 = p403.discoverModels("forbidden-key")
        assertTrue(r403.isFailure)
        assertTrue(r403.exceptionOrNull()?.message?.contains("Authentication failed") == true)

        val p404 = GeminiModelDiscoveryProvider(makeErrorService(404, "NOT_FOUND"))
        val r404 = p404.discoverModels("key")
        assertTrue(r404.isFailure)
        assertTrue(r404.exceptionOrNull()?.message?.contains("404 Not Found") == true)

        val p429 = GeminiModelDiscoveryProvider(makeErrorService(429, "RESOURCE_EXHAUSTED"))
        val r429 = p429.discoverModels("key")
        assertTrue(r429.isFailure)
        assertTrue(r429.exceptionOrNull()?.message?.contains("rate limit or quota") == true)

        val p503 = GeminiModelDiscoveryProvider(makeErrorService(503, "UNAVAILABLE"))
        val r503 = p503.discoverModels("key")
        assertTrue(r503.isFailure)
        assertTrue(r503.exceptionOrNull()?.message?.contains("temporarily unavailable") == true)

        val p500 = GeminiModelDiscoveryProvider(makeErrorService(500, "INTERNAL"))
        val r500 = p500.discoverModels("key")
        assertTrue(r500.isFailure)
        assertTrue(r500.exceptionOrNull()?.message?.contains("server error") == true)
    }

    @Test
    fun testGeminiDiscoveryEmptyAndMalformedModels() = runBlocking {
        // Empty response
        val emptyService = FakeGeminiApiService(listModelsResponse = GeminiListModelsResponse(models = emptyList()))
        val providerEmpty = GeminiModelDiscoveryProvider(emptyService)
        val rEmpty = providerEmpty.discoverModels("valid-key")
        assertTrue(rEmpty.isSuccess)
        assertTrue(rEmpty.getOrNull()!!.isEmpty())

        // Models with missing generateContent method
        val nonContentModels = listOf(
            GeminiModelDto(name = "models/model-embed", supportedGenerationMethods = listOf("embedContent")),
            GeminiModelDto(name = "models/model-count", supportedGenerationMethods = listOf("countTokens"))
        )
        val filterService = FakeGeminiApiService(listModelsResponse = GeminiListModelsResponse(models = nonContentModels))
        val providerFiltered = GeminiModelDiscoveryProvider(filterService)
        val rFiltered = providerFiltered.discoverModels("valid-key")
        assertTrue(rFiltered.isSuccess)
        assertTrue(rFiltered.getOrNull()!!.isEmpty())
    }

    @Test
    fun testGeminiConnectionDiagnosticMeaningfulMessages() = runBlocking {
        val keyManager = object : APIKeyManager {
            override fun getApiKey(providerId: String) = "valid-key"
            override fun saveApiKey(providerId: String, apiKey: String) {}
            override fun clearApiKey(providerId: String) {}
            override fun hasApiKey(providerId: String) = true
        }

        // Test 404 does NOT blame API key
        val notFoundResponse = Response.error<GenerateContentResponse>(
            404,
            "{\"error\":{\"code\":404,\"message\":\"Model not found\"}}".toResponseBody("application/json".toMediaType())
        )
        val apiService404 = FakeGeminiApiService(generateContentException = HttpException(notFoundResponse))
        val provider404 = GeminiAIProvider("TestProj", "valid-key", "obsolete-model", apiService = apiService404)
        val res404 = provider404.generateResponse("hi", emptyList(), null)
        assertTrue(res404.contains("not found or is unsupported"))
        assertFalse(res404.contains("Invalid or expired API key"))

        // Test 401 DOES blame authentication
        val unauthResponse = Response.error<GenerateContentResponse>(
            401,
            "{\"error\":{\"code\":401,\"message\":\"API_KEY_INVALID\"}}".toResponseBody("application/json".toMediaType())
        )
        val apiService401 = FakeGeminiApiService(generateContentException = HttpException(unauthResponse))
        val provider401 = GeminiAIProvider("TestProj", "bad-key", "gemini-3.5-flash", apiService = apiService401)
        val res401 = provider401.generateResponse("hi", emptyList(), null)
        assertTrue(res401.contains("authentication failed") || res401.contains("Invalid or expired API key"))
    }

    private class FakeAIProviderConfigDao : AIProviderConfigDao {
        val configs = mutableMapOf<String, AIProviderConfigEntity>()
        override fun getAllConfigs(): Flow<List<AIProviderConfigEntity>> = flowOf(configs.values.toList())
        override fun getActiveConfig(): Flow<AIProviderConfigEntity?> = flowOf(configs.values.firstOrNull { it.isActive })
        override suspend fun getConfigById(id: String) = configs[id]
        override suspend fun getConfigByProviderType(providerType: String) =
            configs.values.firstOrNull { it.providerType.equals(providerType, ignoreCase = true) }
        override suspend fun insertConfig(config: AIProviderConfigEntity) { configs[config.id] = config }
        override suspend fun updateConfig(config: AIProviderConfigEntity) { configs[config.id] = config }
        override suspend fun deleteConfig(id: String) { configs.remove(id) }
        override suspend fun deactivateAll() {
            configs.replaceAll { _, v -> v.copy(isActive = false) }
        }
        override suspend fun setActive(id: String) {
            configs[id]?.let { configs[id] = it.copy(isActive = true) }
        }
        override suspend fun setActiveByProviderType(providerType: String) {
            val found = configs.values.firstOrNull { it.providerType.equals(providerType, ignoreCase = true) }
            if (found != null) {
                configs[found.id] = found.copy(isActive = true)
            }
        }
    }

    // --- 8. DIAGNOSTIC ERROR CODE CLASSIFICATION TESTS ---

    @Test
    fun testDiagnosticErrorCodeClassification() {
        val dummyRepo = AIProviderConfigRepository(FakeAIProviderConfigDao())
        val dummyKeyManager = object : APIKeyManager {
            override fun getApiKey(providerId: String) = "key"
            override fun saveApiKey(providerId: String, apiKey: String) {}
            override fun clearApiKey(providerId: String) {}
            override fun hasApiKey(providerId: String) = true
        }
        val factory = AIFactory(dummyRepo, dummyKeyManager)

        assertEquals(DiagnosticErrorCode.AUTHENTICATION_FAILED, factory.classifyHttpError(401))
        assertEquals(DiagnosticErrorCode.FORBIDDEN, factory.classifyHttpError(403))
        assertEquals(DiagnosticErrorCode.MODEL_NOT_FOUND, factory.classifyHttpError(404))
        assertEquals(DiagnosticErrorCode.QUOTA_EXCEEDED, factory.classifyHttpError(429, "{\"error\": \"RESOURCE_EXHAUSTED: quota exceeded\"}"))
        assertEquals(DiagnosticErrorCode.RATE_LIMITED, factory.classifyHttpError(429, "{\"error\": \"Too many requests\"}"))
        assertEquals(DiagnosticErrorCode.SERVER_ERROR, factory.classifyHttpError(500))
        assertEquals(DiagnosticErrorCode.SERVER_ERROR, factory.classifyHttpError(503))
        assertEquals(DiagnosticErrorCode.UNKNOWN_ERROR, factory.classifyHttpError(418))
    }

    // --- 9. NO-KEY DISCOVERY & SECURITY TESTS ---

    @Test
    fun testDiscoveryWithoutApiKeyRequiresConfiguration() = runBlocking {
        val repo = RealModelSelectionRepository()
        val result = repo.refreshModels("GEMINI", "   ")
        assertTrue(result.isFailure)
        val state = repo.getModelsState("GEMINI").value
        assertTrue(state.models.isEmpty())
        assertTrue(state.errorMessage?.contains("API key required") == true)
        assertEquals("API key required — model availability not verified.", state.statusMessage)
    }

    @Test
    fun testGeminiMediaGenerationQuotaFailurePreservedAndNoEntityCreated() = runBlocking {
        val dao = FakeMediaDao()
        val repo = FakeMediaRepo(dao)
        val quotaErrorResponse = Response.error<GenerateContentResponse>(
            429,
            "{\"error\":{\"code\":429,\"message\":\"RESOURCE_EXHAUSTED\"}}".toResponseBody("application/json".toMediaType())
        )
        val fakeService = FakeGeminiApiService(generateContentException = HttpException(quotaErrorResponse))
        val keyManager = object : APIKeyManager {
            override fun getApiKey(providerId: String) = "valid-test-key-12345"
            override fun saveApiKey(providerId: String, apiKey: String) {}
            override fun clearApiKey(providerId: String) {}
            override fun hasApiKey(providerId: String) = true
        }

        val provider = GeminiMediaGenerationProvider(repo, keyManager, apiService = fakeService)
        val result = provider.generateMedia(
            MediaGenerationRequest(
                prompt = "A high-tech digital painting",
                capability = MediaCapability.TEXT_TO_IMAGE,
                model = "gemini-2.5-flash-image"
            )
        )

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex?.message?.contains("Quota limit reached for image generation (HTTP 429)") == true)
        // Verify no MediaEntity was created or saved in the database
        assertEquals(0, dao.getMediaCount())
    }

    @Test
    fun testSecurityNoApiKeyLeakageInErrors() = runBlocking {
        val secretKey = "AIzaSySecretTestKey9876543210"
        val errorResponse = Response.error<GenerateContentResponse>(
            401,
            "{\"error\":{\"code\":401,\"message\":\"Invalid API key provided\"}}".toResponseBody("application/json".toMediaType())
        )
        val fakeService = FakeGeminiApiService(generateContentException = HttpException(errorResponse))
        val provider = GeminiAIProvider("SecretTestProj", secretKey, "gemini-3.5-flash", apiService = fakeService)
        val response = provider.generateResponse("test", emptyList(), null)

        // The error output must never reveal the secret API key value
        assertFalse("Error message must not leak API key", response.contains(secretKey))
        assertTrue(response.contains("Invalid or expired API key") || response.contains("authentication failed"))
    }
}
