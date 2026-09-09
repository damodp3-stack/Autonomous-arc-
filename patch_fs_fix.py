with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'r') as f:
    content = f.read()

content = content.replace("fun getProjectRoot        if", "fun getProjectRoot(projectId: String): java.io.File {\n        if")

replacement = """    fun deleteProject(projectId: String): Boolean {
        return try {
            if (!isValidProjectId(projectId)) return false
            val rootDir = java.io.File(context.filesDir, "projects")
            val projectDir = java.io.File(rootDir, projectId)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            } else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun replaceProject(projectId: String, stagingProjectId: String): Boolean {
        try {
            val rootDir = java.io.File(context.filesDir, "projects")
            val targetDir = java.io.File(rootDir, projectId)
            val stagingDir = java.io.File(rootDir, stagingProjectId)
            
            if (!stagingDir.exists()) return false
            
            if (targetDir.exists()) {
                if (!targetDir.deleteRecursively()) return false
            }
            
            return stagingDir.renameTo(targetDir)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}"""

content = content.replace("    fun deleteProject(projectId: String): Boolean {", replacement.split("    fun deleteProject(projectId: String): Boolean {")[0] + "    fun deleteProject(projectId: String): Boolean {")
content = content.rsplit("}", 1)[0] + "    fun replaceProject(projectId: String, stagingProjectId: String): Boolean {\n        try {\n            val rootDir = java.io.File(context.filesDir, \"projects\")\n            val targetDir = java.io.File(rootDir, projectId)\n            val stagingDir = java.io.File(rootDir, stagingProjectId)\n            if (!stagingDir.exists()) return false\n            if (targetDir.exists()) { if (!targetDir.deleteRecursively()) return false }\n            return stagingDir.renameTo(targetDir)\n        } catch (e: Exception) { e.printStackTrace(); return false }\n    }\n}"

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'w') as f:
    f.write(content)
