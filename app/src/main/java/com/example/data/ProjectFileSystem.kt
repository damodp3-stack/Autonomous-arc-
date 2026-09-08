package com.example.data

import android.content.Context
import java.io.File
import java.io.IOException

class ProjectFileSystem(private val context: Context) {

    fun getProjectRoot(projectId: String): File {
        val rootDir = File(context.filesDir, "projects")
        val projectDir = File(rootDir, projectId)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }
    
    fun getProjectFile(projectId: String, path: String): File? {
        if (path.startsWith("/")) return null // Reject absolute paths explicitly
        val projectRoot = getProjectRoot(projectId)
        val normalizedPath = File(projectRoot, path).normalize().absolutePath
        if (!normalizedPath.startsWith(projectRoot.absolutePath)) {
            return null // Path traversal protection
        }
        return File(normalizedPath)
    }

    fun writeFile(projectId: String, path: String, content: String): Boolean {
        return try {
            val file = getProjectFile(projectId, path) ?: return false
            file.parentFile?.mkdirs()
            file.writeText(content)
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
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun renameFile(projectId: String, oldPath: String, newPath: String): Boolean {
        return try {
            val oldFile = getProjectFile(projectId, oldPath) ?: return false
            val newFile = getProjectFile(projectId, newPath) ?: return false
            
            if (oldFile.exists()) {
                newFile.parentFile?.mkdirs()
                oldFile.renameTo(newFile)
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteProject(projectId: String): Boolean {
        return try {
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
