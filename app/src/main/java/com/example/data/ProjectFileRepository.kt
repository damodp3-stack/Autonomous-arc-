package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ProjectFileRepository(private val fileDao: ProjectFileDao, private val fileSystem: ProjectFileSystem) {
    
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
    ): ProjectFileEntity? {
        if (!isDirectory) {
            val success = fileSystem.writeFile(projectId, path, content)
            if (!success) return null
        }
        
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

    suspend fun restoreFile(file: ProjectFileEntity) {
        if (!file.isDirectory) {
            fileSystem.writeFile(file.projectId, file.path, file.content)
        }
        fileDao.insertFile(file)
    }

    suspend fun updateFileContent(fileId: String, newContent: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.writeFile(file.projectId, file.path, newContent)
            if (success) {
                val updatedFile = file.copy(content = newContent, updatedAt = System.currentTimeMillis())
                fileDao.updateFile(updatedFile)
            }
        }
    }

    suspend fun renameFile(fileId: String, newPath: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.renameFile(file.projectId, file.path, newPath)
            if (success) {
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
    }

    suspend fun deleteFile(fileId: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.deleteFile(file.projectId, file.path)
            val fsFile = fileSystem.getProjectFile(file.projectId, file.path)
            if (success || (fsFile != null && !fsFile.exists())) {
                fileDao.deleteFile(fileId)
            }
        }
    }

    
    suspend fun syncProjectFilesToSystem(projectId: String) {
        val projectDir = fileSystem.getProjectRoot(projectId)
        if (!projectDir.exists() || projectDir.list()?.isEmpty() == true) {
            val filesFlow = fileDao.getFilesForProject(projectId)
                        val files = filesFlow.firstOrNull() ?: emptyList()
            for (file in files) {
                if (!file.isDirectory) {
                    fileSystem.writeFile(file.projectId, file.path, file.content)
                } else {
                    fileSystem.getProjectFile(file.projectId, file.path)?.mkdirs()
                }
            }
        }
    }

    suspend fun clearFilesForProject(projectId: String) {
        fileSystem.deleteProject(projectId)
        fileDao.clearFilesForProject(projectId)
    }
}
