package com.example.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: SyncMetadataEntity)

    @Update
    suspend fun update(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE entityType = :entityType AND localId = :localId")
    suspend fun getMetadata(entityType: String, localId: String): SyncMetadataEntity?

    @Query("SELECT * FROM sync_metadata WHERE syncStatus = :status")
    suspend fun getByStatus(status: String): List<SyncMetadataEntity>

    @Query("SELECT * FROM sync_metadata WHERE syncStatus IN ('PENDING_UPLOAD', 'CONFLICT')")
    fun getPendingMetadataFlow(): Flow<List<SyncMetadataEntity>>

    @Query("SELECT COUNT(*) FROM sync_metadata WHERE syncStatus IN ('PENDING_UPLOAD', 'CONFLICT')")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM sync_metadata")
    fun getAllMetadataFlow(): Flow<List<SyncMetadataEntity>>

    @Query("DELETE FROM sync_metadata WHERE entityType = :entityType AND localId = :localId")
    suspend fun deleteMetadata(entityType: String, localId: String)
}
