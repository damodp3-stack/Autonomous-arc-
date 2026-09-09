with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()

replacement = """    suspend fun clearFilesForProject(projectId: String) {
        fileSystem.deleteProject(projectId)
        fileDao.clearFilesForProject(projectId)
    }

    suspend fun replaceProjectWorkspace(projectId: String, stagingProjectId: String, newFiles: List<ProjectFileEntity>): Boolean {
        // Atomic-ish replacement
        val success = fileSystem.replaceProject(projectId, stagingProjectId)
        if (success) {
            fileDao.clearFilesForProject(projectId)
            fileDao.insertFiles(newFiles)
            return true
        }
        return false
    }
}"""

content = content.replace("    suspend fun clearFilesForProject(projectId: String) {\n        fileSystem.deleteProject(projectId)\n        fileDao.clearFilesForProject(projectId)\n    }\n}", replacement)

with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'w') as f:
    f.write(content)
