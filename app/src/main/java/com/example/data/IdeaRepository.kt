package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface IdeaRepository {
    fun getAllIdeas(): Flow<List<IdeaEntity>>
    fun searchIdeas(query: String): Flow<List<IdeaEntity>>
    fun getIdeasByStatus(status: String): Flow<List<IdeaEntity>>
    suspend fun getIdeaById(id: String): IdeaEntity?
    suspend fun createIdea(
        title: String,
        description: String,
        tags: List<String> = emptyList(),
        status: String = "DRAFT",
        projectAssociation: String? = null,
        chatAssociation: String? = null
    ): IdeaEntity
    suspend fun updateIdea(idea: IdeaEntity): IdeaEntity
    suspend fun deleteIdea(id: String): Boolean
    suspend fun convertToProject(ideaId: String, projectRepository: ProjectRepository): String?
}

class LocalIdeaRepository(
    private val ideaDao: IdeaDao
) : IdeaRepository {

    override fun getAllIdeas(): Flow<List<IdeaEntity>> = ideaDao.getAllIdeas()

    override fun searchIdeas(query: String): Flow<List<IdeaEntity>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            ideaDao.getAllIdeas()
        } else {
            ideaDao.searchIdeas(trimmed)
        }
    }

    override fun getIdeasByStatus(status: String): Flow<List<IdeaEntity>> =
        ideaDao.getIdeasByStatus(status)

    override suspend fun getIdeaById(id: String): IdeaEntity? =
        ideaDao.getIdeaById(id)

    override suspend fun createIdea(
        title: String,
        description: String,
        tags: List<String>,
        status: String,
        projectAssociation: String?,
        chatAssociation: String?
    ): IdeaEntity {
        val now = System.currentTimeMillis()
        val formattedTags = tags.map { it.trim() }.filter { it.isNotBlank() }.joinToString(",")
        val entity = IdeaEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            createdTimestamp = now,
            updatedTimestamp = now,
            status = status,
            tags = formattedTags,
            projectAssociation = projectAssociation,
            chatAssociation = chatAssociation
        )
        ideaDao.insert(entity)
        return entity
    }

    override suspend fun updateIdea(idea: IdeaEntity): IdeaEntity {
        val updated = idea.copy(
            title = idea.title.trim(),
            description = idea.description.trim(),
            updatedTimestamp = System.currentTimeMillis()
        )
        ideaDao.update(updated)
        return updated
    }

    override suspend fun deleteIdea(id: String): Boolean {
        ideaDao.deleteById(id)
        return true
    }

    override suspend fun convertToProject(ideaId: String, projectRepository: ProjectRepository): String? {
        val idea = ideaDao.getIdeaById(ideaId) ?: return null
        val projectName = idea.title.ifBlank { "Idea Project" }
        val newProjectId = projectRepository.createProject(projectName)
        val updated = idea.copy(
            projectAssociation = newProjectId,
            status = "IN_PROGRESS",
            updatedTimestamp = System.currentTimeMillis()
        )
        ideaDao.update(updated)
        return newProjectId
    }
}
