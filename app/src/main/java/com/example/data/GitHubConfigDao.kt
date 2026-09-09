package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubConfigDao {
    @Query("SELECT * FROM github_configs WHERE projectId = :projectId")
    fun getConfigForProject(projectId: String): Flow<GitHubConfigEntity?>

    @Query("SELECT * FROM github_configs WHERE projectId = :projectId")
    suspend fun getConfigSync(projectId: String): GitHubConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: GitHubConfigEntity)

    @Update
    suspend fun updateConfig(config: GitHubConfigEntity)

    @Query("DELETE FROM github_configs WHERE projectId = :projectId")
    suspend fun deleteConfig(projectId: String)
}
