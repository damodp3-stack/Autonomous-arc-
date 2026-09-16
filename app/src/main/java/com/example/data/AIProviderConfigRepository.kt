package com.example.data

import kotlinx.coroutines.flow.Flow

class AIProviderConfigRepository(private val dao: AIProviderConfigDao) {
    fun getAllConfigs(): Flow<List<AIProviderConfigEntity>> = dao.getAllConfigs()

    fun getActiveConfig(): Flow<AIProviderConfigEntity?> = dao.getActiveConfig()

    suspend fun getConfigById(id: String): AIProviderConfigEntity? = dao.getConfigById(id)

    suspend fun saveConfig(config: AIProviderConfigEntity) {
        dao.insertConfig(config)
    }
    
    suspend fun setActiveConfig(id: String) {
        dao.deactivateAll()
        dao.setActive(id)
    }

    suspend fun deleteConfig(id: String) {
        dao.deleteConfig(id)
    }
}
