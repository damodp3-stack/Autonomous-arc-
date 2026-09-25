package com.example.sync

import com.example.data.IdeaDao
import com.example.data.MediaDao
import com.example.data.ProjectDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface SyncRepository {
    val syncState: StateFlow<SyncState>
    fun getPendingCount(): Flow<Int>
    suspend fun markForSync(entityType: SyncEntityType, localId: String)
    suspend fun syncAll(strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LAST_WRITE_WINS): SyncSummary
    suspend fun testProviderConnection(): Result<String>
    fun setProvider(provider: CloudSyncProvider)
    fun getCurrentProviderName(): String
}

class RealSyncRepository(
    private val syncMetadataDao: SyncMetadataDao,
    private val projectDao: ProjectDao,
    private val ideaDao: IdeaDao,
    private val mediaDao: MediaDao,
    initialProvider: CloudSyncProvider = LocalOnlySyncProvider()
) : SyncRepository {

    private var currentProvider: CloudSyncProvider = initialProvider

    private val _syncState = MutableStateFlow(
        SyncState(
            isSyncing = false,
            currentProvider = initialProvider.providerName
        )
    )
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override fun getPendingCount(): Flow<Int> = syncMetadataDao.getPendingCountFlow()

    override fun setProvider(provider: CloudSyncProvider) {
        currentProvider = provider
        _syncState.value = _syncState.value.copy(
            currentProvider = provider.providerName
        )
    }

    override fun getCurrentProviderName(): String = currentProvider.providerName

    override suspend fun markForSync(entityType: SyncEntityType, localId: String) {
        val existing = syncMetadataDao.getMetadata(entityType.name, localId)
        val metadata = existing?.copy(
            localModifiedTimestamp = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPLOAD.name
        ) ?: SyncMetadataEntity(
            entityType = entityType.name,
            localId = localId,
            localModifiedTimestamp = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPLOAD.name
        )
        syncMetadataDao.insertOrUpdate(metadata)
    }

    override suspend fun testProviderConnection(): Result<String> {
        return currentProvider.testConnection()
    }

    override suspend fun syncAll(strategy: ConflictResolutionStrategy): SyncSummary =
        withContext(Dispatchers.IO) {
            _syncState.value = _syncState.value.copy(isSyncing = true, lastError = null)

            val errors = mutableListOf<String>()
            var uploadedCount = 0
            var downloadedCount = 0
            var conflictCount = 0

            try {
                if (!currentProvider.isConfigured) {
                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        lastError = "Sync skipped: offline-first provider active"
                    )
                    return@withContext SyncSummary(
                        uploaded = 0,
                        downloaded = 0,
                        conflicts = 0,
                        errors = listOf("Offline-first mode: no remote provider configured")
                    )
                }

                // 1. Collect pending local items
                val pendingMetadata = syncMetadataDao.getByStatus(SyncStatus.PENDING_UPLOAD.name)
                val itemsToPush = mutableListOf<SyncItem>()

                for (meta in pendingMetadata) {
                    when (meta.entityType) {
                        SyncEntityType.PROJECT.name -> {
                            val project = projectDao.getProjectById(meta.localId)
                            if (project != null) {
                                itemsToPush.add(
                                    SyncItem(
                                        entityType = SyncEntityType.PROJECT,
                                        localId = project.id,
                                        remoteId = meta.remoteId,
                                        payloadJson = "{\"name\":\"${project.name}\",\"updatedAt\":${project.updatedAt}}",
                                        lastModified = meta.localModifiedTimestamp
                                    )
                                )
                            }
                        }
                        SyncEntityType.IDEA.name -> {
                            val idea = ideaDao.getIdeaById(meta.localId)
                            if (idea != null) {
                                itemsToPush.add(
                                    SyncItem(
                                        entityType = SyncEntityType.IDEA,
                                        localId = idea.id,
                                        remoteId = meta.remoteId,
                                        payloadJson = "{\"title\":\"${idea.title}\",\"status\":\"${idea.status}\",\"tags\":\"${idea.tags}\"}",
                                        lastModified = meta.localModifiedTimestamp
                                    )
                                )
                            }
                        }
                        SyncEntityType.MEDIA.name -> {
                            val media = mediaDao.getMediaById(meta.localId)
                            if (media != null) {
                                itemsToPush.add(
                                    SyncItem(
                                        entityType = SyncEntityType.MEDIA,
                                        localId = media.id,
                                        remoteId = meta.remoteId,
                                        payloadJson = "{\"filename\":\"${media.filename}\",\"mediaType\":\"${media.mediaType}\"}",
                                        lastModified = meta.localModifiedTimestamp
                                    )
                                )
                            }
                        }
                    }
                }

                // 2. Push to cloud provider
                if (itemsToPush.isNotEmpty()) {
                    val pushResult = currentProvider.pushChanges(itemsToPush)
                    uploadedCount = pushResult.uploadedCount

                    for (item in itemsToPush) {
                        val failedReason = pushResult.failedIds[item.localId]
                        if (failedReason != null) {
                            conflictCount++
                            syncMetadataDao.insertOrUpdate(
                                SyncMetadataEntity(
                                    entityType = item.entityType.name,
                                    localId = item.localId,
                                    remoteId = item.remoteId,
                                    localModifiedTimestamp = item.lastModified,
                                    syncStatus = SyncStatus.CONFLICT.name,
                                    syncError = failedReason
                                )
                            )
                        } else {
                            syncMetadataDao.insertOrUpdate(
                                SyncMetadataEntity(
                                    entityType = item.entityType.name,
                                    localId = item.localId,
                                    remoteId = item.remoteId ?: "remote_${item.localId}",
                                    localModifiedTimestamp = item.lastModified,
                                    lastSyncedTimestamp = System.currentTimeMillis(),
                                    syncStatus = SyncStatus.SYNCED.name,
                                    syncError = null
                                )
                            )
                        }
                    }
                }

                // 3. Pull from cloud provider
                val lastSync = _syncState.value.lastSyncTimestamp ?: 0L
                val pullResult = currentProvider.fetchChanges(lastSync)
                downloadedCount = pullResult.items.size

                val now = System.currentTimeMillis()
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncTimestamp = now,
                    lastError = if (errors.isEmpty()) null else errors.first()
                )

                SyncSummary(
                    uploaded = uploadedCount,
                    downloaded = downloadedCount,
                    conflicts = conflictCount,
                    errors = errors,
                    completedTimestamp = now
                )
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown sync error"
                errors.add(errorMsg)
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastError = errorMsg
                )
                SyncSummary(
                    uploaded = uploadedCount,
                    downloaded = downloadedCount,
                    conflicts = conflictCount,
                    errors = errors
                )
            }
        }
}
