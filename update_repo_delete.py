with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'r') as f:
    content = f.read()

delete_old = """    suspend fun deleteFile(fileId: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.deleteFile(file.projectId, file.path)
            if (success || !fileSystem.getProjectFile(file.projectId, file.path)!!.exists()) {
                fileDao.deleteFile(fileId)
            }
        }
    }"""

delete_new = """    suspend fun deleteFile(fileId: String) {
        val file = fileDao.getFile(fileId)
        if (file != null) {
            val success = fileSystem.deleteFile(file.projectId, file.path)
            val fsFile = fileSystem.getProjectFile(file.projectId, file.path)
            if (success || (fsFile != null && !fsFile.exists())) {
                fileDao.deleteFile(fileId)
            }
        }
    }"""

content = content.replace(delete_old, delete_new)

with open('app/src/main/java/com/example/data/ProjectFileRepository.kt', 'w') as f:
    f.write(content)
