package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AIProviderConfigDao {
    @Query("SELECT * FROM ai_provider_config")
    fun getAllConfigs(): Flow<List<AIProviderConfigEntity>>

    @Query("SELECT * FROM ai_provider_config WHERE isActive = 1 LIMIT 1")
    fun getActiveConfig(): Flow<AIProviderConfigEntity?>

    @Query("SELECT * FROM ai_provider_config WHERE id = :id")
    suspend fun getConfigById(id: String): AIProviderConfigEntity?

    @Query("SELECT * FROM ai_provider_config WHERE UPPER(providerType) = UPPER(:providerType) LIMIT 1")
    suspend fun getConfigByProviderType(providerType: String): AIProviderConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AIProviderConfigEntity)

    @Update
    suspend fun updateConfig(config: AIProviderConfigEntity)

    @Query("DELETE FROM ai_provider_config WHERE id = :id")
    suspend fun deleteConfig(id: String)
    
    @Query("UPDATE ai_provider_config SET isActive = 0")
    suspend fun deactivateAll()
    
    @Query("UPDATE ai_provider_config SET isActive = 1 WHERE id = :id")
    suspend fun setActive(id: String)

    @Query("UPDATE ai_provider_config SET isActive = 1 WHERE UPPER(providerType) = UPPER(:providerType)")
    suspend fun setActiveByProviderType(providerType: String)
}
