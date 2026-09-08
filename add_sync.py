with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()

sync_function = """
    suspend fun syncProjectFilesToSystem(projectId: String) {
        val projectDir = fileSystem.getProjectRoot(projectId)
        if (!projectDir.exists() || projectDir.list()?.isEmpty() == true) {
            val filesFlow = fileDao.getFilesForProject(projectId)
            import kotlinx.coroutines.flow.firstOrNull
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
"""
content = content.replace('suspend fun clearFilesForProject', sync_function.replace('import kotlinx.coroutines.flow.firstOrNull\n', '') + '\n    suspend fun clearFilesForProject')

with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'w') as f:
    f.write(content)
