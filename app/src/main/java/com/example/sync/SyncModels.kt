package com.example.sync

enum class SyncStatus {
    SYNCED,
    PENDING_UPLOAD,
    PENDING_DOWNLOAD,
    CONFLICT,
    LOCAL_ONLY,
    ERROR
}

enum class SyncEntityType {
    PROJECT,
    CHAT,
    IDEA,
    MEDIA
}

enum class ConflictResolutionStrategy {
    SERVER_WINS,
    CLIENT_WINS,
    LAST_WRITE_WINS,
    MANUAL
}

data class SyncItem(
    val entityType: SyncEntityType,
    val localId: String,
    val remoteId: String? = null,
    val payloadJson: String,
    val lastModified: Long = System.currentTimeMillis()
)

data class SyncResult(
    val isSuccess: Boolean,
    val uploadedCount: Int = 0,
    val failedIds: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

data class SyncPullResult(
    val items: List<SyncItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class SyncSummary(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflicts: Int = 0,
    val errors: List<String> = emptyList(),
    val completedTimestamp: Long = System.currentTimeMillis()
)

data class SyncState(
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val pendingChangesCount: Int = 0,
    val lastError: String? = null,
    val currentProvider: String = "Local Only"
)
