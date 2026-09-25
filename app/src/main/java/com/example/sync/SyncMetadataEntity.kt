package com.example.sync

import androidx.room.Entity

@Entity(
    tableName = "sync_metadata",
    primaryKeys = ["entityType", "localId"]
)
data class SyncMetadataEntity(
    val entityType: String, // "PROJECT", "CHAT", "IDEA", "MEDIA"
    val localId: String,
    val remoteId: String? = null,
    val localModifiedTimestamp: Long = System.currentTimeMillis(),
    val lastSyncedTimestamp: Long? = null,
    val syncStatus: String = SyncStatus.LOCAL_ONLY.name,
    val syncError: String? = null
)
