package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ProjectFileRepository(private val fileDao: ProjectFileDao, val fileSystem: ProjectFileSystem) {
    
    fun getFilesForProject(projectId: String): Flow<List<ProjectFileEntity>> =
        fileDao.getFilesForProject(projectId)

    suspend fun getFile(fileId: String): ProjectFileEntity? =
        fileDao.getFile(fileId)

    suspend fun getFileByPath(projectId: String, path: String): ProjectFileEntity? =
        fileDao.getFileByPath(projectId, path)

    suspend fun createFileWithBytes(
        projectId: String,
        path: String,
        content: ByteArray,
        isDirectory: Boolean = false
    ): ProjectFileEntity? {
        if (!isDirectory) {
            val success = fileSystem.writeFileBytes(projectId, path, content)
            if (!success) return null
        } else {
            val success = fileSystem.createDirectory(projectId, path)
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
            content = "[BINARY FILE]", // Do not store binary in Room
            isDirectory = isDirectory,
            parentPath = parentPath
        )
        fileDao.insertFile(newFile)
        return newFile
    }

    suspend fun createFile(
        projectId: String,
        path: String,
        content: String = "",
        isDirectory: Boolean = false
    ): ProjectFileEntity? {
        if (!isDirectory) {
            val success = fileSystem.writeFile(projectId, path, content)
            if (!success) return null
        } else {
            val success = fileSystem.createDirectory(projectId, path)
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

    suspend fun restoreFile(file: ProjectFileEntity): Boolean {
        if (!file.isDirectory) {
            val success = fileSystem.writeFile(file.projectId, file.path, file.content)
            if (!success) return false
        } else {
            val success = fileSystem.createDirectory(file.projectId, file.path)
            if (!success) return false
        }
        fileDao.insertFile(file)
        return true
    }

    suspend fun updateFileContent(fileId: String, newContent: String): Boolean {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.writeFile(file.projectId, file.path, newContent)
            if (success) {
                val updatedFile = file.copy(content = newContent, updatedAt = System.currentTimeMillis())
                fileDao.updateFile(updatedFile)
                return true
            }
        }
        return false
    }

    suspend fun renameFile(fileId: String, newPath: String): Boolean {
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
                return true
            }
        }
        return false
    }

    suspend fun deleteFile(fileId: String): Boolean {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.deleteFile(file.projectId, file.path)
            val fsFile = fileSystem.getProjectFile(file.projectId, file.path)
            if (success || (fsFile != null && !fsFile.exists())) {
                fileDao.deleteFile(fileId)
                return true
            }
        }
        return false
    }

    suspend fun syncProjectFilesToSystem(projectId: String) {
        try {
            val filesFlow = fileDao.getFilesForProject(projectId)
            val roomFiles = filesFlow.firstOrNull() ?: emptyList()
            
            for (roomFile in roomFiles) {
                val fsFile = fileSystem.getProjectFile(projectId, roomFile.path)
                if (fsFile == null) continue
                
                if (!fsFile.exists()) {
                    if (roomFile.isDirectory) {
                        fileSystem.createDirectory(projectId, roomFile.path)
                    } else {
                        fileSystem.writeFile(projectId, roomFile.path, roomFile.content)
                    }
                } else {
                    if (!roomFile.isDirectory && fsFile.isFile) {
                        val fsContent = fileSystem.readFile(projectId, roomFile.path)
                        if (fsContent != null && fsContent != roomFile.content) {
                            val updatedFile = roomFile.copy(content = fsContent, updatedAt = System.currentTimeMillis())
                            fileDao.updateFile(updatedFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun writeStagingFileBytes(stagingProjectId: String, path: String, content: ByteArray): Boolean {
        return fileSystem.writeFileBytes(stagingProjectId, path, content)
    }

    suspend fun createStagingDirectory(stagingProjectId: String, path: String): Boolean {
        return fileSystem.createDirectory(stagingProjectId, path)
    }

    suspend fun clearStagingProject(stagingProjectId: String) {
        fileSystem.deleteProject(stagingProjectId)
    }

    suspend fun clearFilesForProject(projectId: String) {
        fileSystem.deleteProject(projectId)
        fileDao.clearFilesForProject(projectId)
    }

    suspend fun replaceProjectWorkspace(projectId: String, stagingProjectId: String, newFiles: List<ProjectFileEntity>): Boolean {
        // Keep backup of room entities
        val oldFiles = fileDao.getFilesForProject(projectId).firstOrNull() ?: emptyList()
        
        val fsSuccess = fileSystem.replaceProject(projectId, stagingProjectId)
        if (!fsSuccess) {
            return false
        }
        
        try {
            fileDao.clearFilesForProject(projectId)
            fileDao.insertFiles(newFiles)
            fileSystem.cleanupBackupProject(projectId)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            // Rollback Room
            try {
                fileDao.clearFilesForProject(projectId)
                fileDao.insertFiles(oldFiles)
            } catch (inner: Exception) {
                inner.printStackTrace()
            }
            // Rollback FileSystem
            fileSystem.restoreBackupProject(projectId)
            return false
        }
    }
}