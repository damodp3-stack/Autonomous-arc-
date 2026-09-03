package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

interface ProjectRepository {
    fun getAllProjects(): Flow<List<ProjectEntity>>
    fun getProject(projectId: String): Flow<ProjectEntity?>
    suspend fun createProject(name: String): String
    suspend fun renameProject(projectId: String, newName: String)
    suspend fun deleteProject(projectId: String)
}

class LocalProjectRepository(
    private val projectDao: ProjectDao,
    private val messageDao: MessageDao
) : ProjectRepository {

    override fun getAllProjects(): Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    override fun getProject(projectId: String): Flow<ProjectEntity?> = projectDao.getProject(projectId)

    override suspend fun createProject(name: String): String {
        val project = ProjectEntity(name = name)
        projectDao.insertProject(project)
        return project.id
    }

    override suspend fun renameProject(projectId: String, newName: String) {
        val existingProject = projectDao.getProject(projectId).firstOrNull()
        if (existingProject != null) {
            val updatedProject = existingProject.copy(name = newName, updatedAt = System.currentTimeMillis())
            projectDao.updateProject(updatedProject)
        }
    }

    override suspend fun deleteProject(projectId: String) {
        messageDao.clearMessages(projectId)
        projectDao.deleteProject(projectId)
    }
}
