with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()

replacement = """    suspend fun writeStagingFileBytes(stagingProjectId: String, path: String, content: ByteArray): Boolean {
        return fileSystem.writeFileBytes(stagingProjectId, path, content)
    }

    suspend fun createStagingDirectory(stagingProjectId: String, path: String): Boolean {
        return fileSystem.createDirectory(stagingProjectId, path)
    }

    suspend fun clearStagingProject(stagingProjectId: String) {
        fileSystem.deleteProject(stagingProjectId)
    }
"""

content = content.replace("    suspend fun clearFilesForProject", replacement + "\n    suspend fun clearFilesForProject")

with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'w') as f:
    f.write(content)
