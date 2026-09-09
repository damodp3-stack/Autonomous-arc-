with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()

replacement = """    suspend fun createFileWithBytes(
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

    suspend fun createFile"""

content = content.replace("    suspend fun createFile(", replacement + "(")

with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'w') as f:
    f.write(content)
