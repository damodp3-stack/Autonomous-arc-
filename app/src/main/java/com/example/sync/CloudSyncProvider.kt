package com.example.sync

interface CloudSyncProvider {
    val providerName: String
    val isConfigured: Boolean

    suspend fun pushChanges(changes: List<SyncItem>): SyncResult
    suspend fun fetchChanges(sinceTimestamp: Long): SyncPullResult
    suspend fun testConnection(): Result<String>
}

/**
 * Default offline provider when no external cloud sync credentials are configured.
 * Preserves local Room database as complete single source of truth.
 */
class LocalOnlySyncProvider : CloudSyncProvider {
    override val providerName: String = "Local Only (Offline)"
    override val isConfigured: Boolean = false

    override suspend fun pushChanges(changes: List<SyncItem>): SyncResult {
        return SyncResult(
            isSuccess = true,
            uploadedCount = 0,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun fetchChanges(sinceTimestamp: Long): SyncPullResult {
        return SyncPullResult(
            items = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun testConnection(): Result<String> {
        return Result.success("Offline-first mode active. Local database is fully functional.")
    }
}

/**
 * In-memory cloud sync provider for testing sync logic, conflict resolution,
 * and offline-to-online transitions without external cloud credentials.
 */
class MockCloudSyncProvider : CloudSyncProvider {
    override val providerName: String = "Mock Cloud Provider"
    override val isConfigured: Boolean = true

    private val remoteStorage = mutableMapOf<String, SyncItem>()
    var shouldFailConnection: Boolean = false
    var simulatedConflictKey: String? = null

    override suspend fun pushChanges(changes: List<SyncItem>): SyncResult {
        val failed = mutableMapOf<String, String>()
        var count = 0

        for (item in changes) {
            val key = "${item.entityType}:${item.localId}"
            if (key == simulatedConflictKey) {
                failed[item.localId] = "Simulated remote conflict: item modified on server"
            } else {
                remoteStorage[key] = item.copy(remoteId = "remote_${item.localId}")
                count++
            }
        }

        return SyncResult(
            isSuccess = failed.isEmpty(),
            uploadedCount = count,
            failedIds = failed,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun fetchChanges(sinceTimestamp: Long): SyncPullResult {
        val filtered = remoteStorage.values.filter { it.lastModified >= sinceTimestamp }
        return SyncPullResult(
            items = filtered,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun testConnection(): Result<String> {
        return if (shouldFailConnection) {
            Result.failure(IllegalStateException("Simulated connection failure to cloud sync endpoint"))
        } else {
            Result.success("Connection successful (Mock Cloud Endpoint: OK)")
        }
    }

    fun seedRemoteItem(item: SyncItem) {
        val key = "${item.entityType}:${item.localId}"
        remoteStorage[key] = item
    }

    fun clearRemote() {
        remoteStorage.clear()
    }
}
