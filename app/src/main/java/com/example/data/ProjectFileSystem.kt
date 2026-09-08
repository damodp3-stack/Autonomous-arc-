package com.example.data

import android.content.Context
import java.io.File

class ProjectFileSystem(private val context: Context) {

    private fun isValidProjectId(projectId: String): Boolean {
        return projectId.isNotBlank() && projectId.matches(Regex("^[a-zA-Z0-9\\-]+$"))
    }

    fun getProjectRoot(projectId: String): File {
        if (!isValidProjectId(projectId)) {
            throw IllegalArgumentException("Invalid projectId: $projectId")
        }
        val rootDir = File(context.filesDir, "projects")
        val projectDir = File(rootDir, projectId)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }
    
    fun getProjectFile(projectId: String, path: String): File? {
        if (path.startsWith("/")) return null
        if (path.contains("..")) return null
        
        return try {
            val projectRoot = getProjectRoot(projectId)
            val file = File(projectRoot, path).canonicalFile
            val rootCanonical = projectRoot.canonicalFile
            
            val rootPathWithSlash = if (rootCanonical.path.endsWith(File.separator)) rootCanonical.path else "${rootCanonical.path}${File.separator}"
            
            if (file.path != rootCanonical.path && !file.path.startsWith(rootPathWithSlash)) {
                return null
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createDirectory(projectId: String, path: String): Boolean {
        return try {
            val file = getProjectFile(projectId, path) ?: return false
            if (file.exists()) return file.isDirectory
            file.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun writeFile(projectId: String, path: String, content: String): Boolean {
        return try {
            val file = getProjectFile(projectId, path) ?: return false
            if (file.exists() && file.isDirectory) return false
            
            file.parentFile?.mkdirs()
            
            val tempFile = File(file.parentFile, file.name + ".tmp_" + System.nanoTime())
            tempFile.writeText(content)
            
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

    fun readFile(projectId: String, path: String): String? {
        return try {
            val file = getProjectFile(projectId, path) ?: return null
            if (file.exists() && file.isFile) file.readText() else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteFile(projectId: String, path: String): Boolean {
        return try {
            val file = getProjectFile(projectId, path) ?: return false
            if (file.exists()) {
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            } else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renameFile(projectId: String, oldPath: String, newPath: String): Boolean {
        return try {
            val oldFile = getProjectFile(projectId, oldPath) ?: return false
            val newFile = getProjectFile(projectId, newPath) ?: return false
            
            if (!oldFile.exists()) return false
            if (newFile.exists()) return false // Do not accidentally overwrite
            
            newFile.parentFile?.mkdirs()
            val success = oldFile.renameTo(newFile)
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteProject(projectId: String): Boolean {
        return try {
            if (!isValidProjectId(projectId)) return false
            val rootDir = File(context.filesDir, "projects")
            val projectDir = File(rootDir, projectId)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            } else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
