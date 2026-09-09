package com.example.data

import kotlinx.coroutines.flow.Flow

class GitHubConfigRepository(private val dao: GitHubConfigDao) {
    fun getConfigForProject(projectId: String): Flow<GitHubConfigEntity?> = dao.getConfigForProject(projectId)

    suspend fun getConfigSync(projectId: String): GitHubConfigEntity? = dao.getConfigSync(projectId)

    suspend fun saveConfig(config: GitHubConfigEntity) {
        dao.insertConfig(config)
    }

    suspend fun clearConfig(projectId: String) {
        dao.deleteConfig(projectId)
    }
}
