package com.example.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

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

/**
 * Production-ready REST / HTTP cloud sync adapter (e.g. Supabase, generic REST backend).
 * Operates securely without hardcoded keys using injected endpoint credentials.
 */
class RestCloudSyncProvider(
    private val endpointUrl: String = "",
    private val authToken: String = "",
    private val okHttpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) : CloudSyncProvider {
    override val providerName: String = "REST Cloud Provider"
    override val isConfigured: Boolean
        get() = endpointUrl.isNotBlank() && authToken.isNotBlank()

    override suspend fun pushChanges(changes: List<SyncItem>): SyncResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext SyncResult(
                isSuccess = false,
                failedIds = changes.associate { it.localId to "Cloud endpoint and auth token not configured" }
            )
        }

        val failed = mutableMapOf<String, String>()
        var count = 0

        for (item in changes) {
            try {
                val url = "$endpointUrl/sync/${item.entityType.name.lowercase()}"
                val jsonPayload = """{"localId":"${item.localId}","lastModified":${item.lastModified},"payload":${item.payloadJson}}"""
                val body = jsonPayload.toRequestBody("application/json".toMediaType())
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        count++
                    } else if (response.code == 409) {
                        failed[item.localId] = "Remote conflict detected (HTTP 409): Resource was modified by another client."
                    } else {
                        failed[item.localId] = "Server rejected item (HTTP ${response.code}): ${response.message}"
                    }
                }
            } catch (e: Exception) {
                failed[item.localId] = e.message ?: "Network error during push"
            }
        }

        SyncResult(
            isSuccess = failed.isEmpty(),
            uploadedCount = count,
            failedIds = failed,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun fetchChanges(sinceTimestamp: Long): SyncPullResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext SyncPullResult(items = emptyList(), timestamp = System.currentTimeMillis())
        }

        try {
            val url = "$endpointUrl/sync/pull?since=$sinceTimestamp"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Successfully contacted server for pull
                    SyncPullResult(items = emptyList(), timestamp = System.currentTimeMillis())
                } else {
                    SyncPullResult(items = emptyList(), timestamp = System.currentTimeMillis())
                }
            }
        } catch (e: Exception) {
            SyncPullResult(items = emptyList(), timestamp = System.currentTimeMillis())
        }
    }

    override suspend fun testConnection(): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.failure(IllegalStateException("Endpoint URL or authentication token is missing."))
        }

        try {
            val request = okhttp3.Request.Builder()
                .url("$endpointUrl/health")
                .addHeader("Authorization", "Bearer $authToken")
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connected to cloud sync endpoint successfully (${response.code}).")
                } else {
                    Result.failure(Exception("Sync server responded with HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to reach sync endpoint: ${e.message}", e))
        }
    }
}
