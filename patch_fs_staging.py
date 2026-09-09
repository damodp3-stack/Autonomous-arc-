with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'r') as f:
    content = f.read()

replacement = """    fun getProjectRoot(projectId: String): java.io.File {
        if (!isValidProjectId(projectId)) {
            throw IllegalArgumentException("Invalid projectId: $projectId")
        }
        val rootDir = java.io.File(context.filesDir, "projects")
        val projectDir = java.io.File(rootDir, projectId)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }
    
    fun replaceProject(projectId: String, stagingProjectId: String): Boolean {
        try {
            val rootDir = java.io.File(context.filesDir, "projects")
            val targetDir = java.io.File(rootDir, projectId)
            val stagingDir = java.io.File(rootDir, stagingProjectId)
            
            if (!stagingDir.exists()) return false
            
            // Delete target dir
            if (targetDir.exists()) {
                if (!targetDir.deleteRecursively()) return false
            }
            
            // Rename staging to target
            return stagingDir.renameTo(targetDir)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
"""

content = content.replace("    fun getProjectRoot(projectId: String): File {", replacement.replace("    fun getProjectRoot", "    fun getProjectRoot_TMP_X").split("_TMP_X")[0])

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'w') as f:
    f.write(content)
