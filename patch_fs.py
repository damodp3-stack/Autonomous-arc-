import re

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'r') as f:
    content = f.read()

replacement = """    fun writeFileBytes(projectId: String, path: String, content: ByteArray): Boolean {
        return try {
            val file = getProjectFile(projectId, path) ?: return false
            if (file.exists() && file.isDirectory) return false
            
            file.parentFile?.mkdirs()
            
            val tempFile = java.io.File(file.parentFile, file.name + ".tmp_" + System.nanoTime())
            tempFile.writeBytes(content)
            
            if (file.exists()) {
                if (!file.delete()) {
                    tempFile.delete()
                    return false
                }
            }
            
            val success = tempFile.renameTo(file)
            if (!success) {
                tempFile.delete()
                return false
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeFile"""

content = content.replace("    fun writeFile", replacement)

with open('app/src/main/java/com/example/data/ProjectFileSystem.kt', 'w') as f:
    f.write(content)
