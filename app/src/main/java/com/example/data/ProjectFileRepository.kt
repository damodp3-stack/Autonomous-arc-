package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectFileRepository(private val fileDao: ProjectFileDao) {

    fun getFilesForProject(projectId: String): Flow<List<ProjectFileEntity>> =
        fileDao.getFilesForProject(projectId)

    suspend fun getFile(fileId: String): ProjectFileEntity? =
        fileDao.getFile(fileId)

    suspend fun getFileByPath(projectId: String, path: String): ProjectFileEntity? =
        fileDao.getFileByPath(projectId, path)

    suspend fun createFile(
        projectId: String,
        path: String,
        content: String = "",
        isDirectory: Boolean = false
    ): ProjectFileEntity {
        val name = path.substringAfterLast('/')
        val extension = if (name.contains(".")) name.substringAfterLast('.') else ""
        val parentPath = if (path.contains('/')) path.substringBeforeLast('/') else ""

        val newFile = ProjectFileEntity(
            projectId = projectId,
            path = path,
            name = name,
            extension = extension,
            content = content,
            isDirectory = isDirectory,
            parentPath = parentPath
        )
        fileDao.insertFile(newFile)
        return newFile
    }

    suspend fun updateFileContent(fileId: String, newContent: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val updatedFile = file.copy(content = newContent, updatedAt = System.currentTimeMillis())
            fileDao.updateFile(updatedFile)
        }
    }

    suspend fun renameFile(fileId: String, newPath: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val name = newPath.substringAfterLast('/')
            val extension = if (name.contains(".")) name.substringAfterLast('.') else ""
            val parentPath = if (newPath.contains('/')) newPath.substringBeforeLast('/') else ""

            val updatedFile = file.copy(
                path = newPath,
                name = name,
                extension = extension,
                parentPath = parentPath,
                updatedAt = System.currentTimeMillis()
            )
            fileDao.updateFile(updatedFile)
        }
    }

    suspend fun deleteFile(fileId: String) = fileDao.deleteFile(fileId)

    suspend fun clearFilesForProject(projectId: String) = fileDao.clearFilesForProject(projectId)
}
