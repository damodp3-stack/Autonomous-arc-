package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC")
    fun getAllUsage(): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUsage(limit: Int): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getUsageSince(sinceTimestamp: Long): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getUsageForProject(projectId: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT COUNT(*) FROM usage_records")
    suspend fun getTotalCount(): Int

    @Query("SELECT COALESCE(SUM(totalTokens), 0) FROM usage_records")
    suspend fun getTotalTokensSum(): Int

    @Query("DELETE FROM usage_records")
    suspend fun clearAll()
}
