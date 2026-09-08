with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'r') as f:
    content = f.read()

get_old = """    fun getProjectFile(projectId: String, path: String): File? {
        val projectRoot = getProjectRoot(projectId)
        val normalizedPath = File(projectRoot, path).normalize().absolutePath
        if (!normalizedPath.startsWith(projectRoot.absolutePath)) {
            return null // Path traversal protection
        }
        return File(normalizedPath)
    }"""

get_new = """    fun getProjectFile(projectId: String, path: String): File? {
        if (path.startsWith("/")) return null // Reject absolute paths explicitly
        val projectRoot = getProjectRoot(projectId)
        val normalizedPath = File(projectRoot, path).normalize().absolutePath
        if (!normalizedPath.startsWith(projectRoot.absolutePath)) {
            return null // Path traversal protection
        }
        return File(normalizedPath)
    }"""

content = content.replace(get_old, get_new)

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'w') as f:
    f.write(content)
