with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()
    
# Replace constructor
content = content.replace('class ProjectFileRepository(private val fileDao: ProjectFileDao)', 'class ProjectFileRepository(private val fileDao: ProjectFileDao, private val fileSystem: ProjectFileSystem)')

# update createFile
create_file_old = """    suspend fun createFile(
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
    }"""

create_file_new = """    suspend fun createFile(
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
    }"""

content = content.replace(create_file_old, create_file_new)

# if the replacement failed because of formatting, we should probably rewrite the whole file.

