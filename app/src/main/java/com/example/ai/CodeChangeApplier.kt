package com.example.ai

import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository

class CodeChangeApplier(
    private val repository: ProjectFileRepository
) {
    suspend fun applyProposal(projectId: String, proposal: CodeChangeProposal): ApplyResult {
        if (proposal.changes.isEmpty()) {
            return ApplyResult.Success(emptyList())
        }

        // 1. Path Security Validation & Duplicate Check
        val normalizedChanges = mutableListOf<FileChange>()
        val seenPaths = mutableSetOf<String>()
        
        for (change in proposal.changes) {
            val normalizedPath = normalizePath(change.filePath)
            if (normalizedPath == null) {
                return ApplyResult.ValidationError("Security Error: Invalid or traversal path detected - ${change.filePath}")
            }
            if (!seenPaths.add(normalizedPath)) {
                return ApplyResult.ValidationError("Duplicate path detected in proposal: $normalizedPath")
            }
            // Replace path with normalized path for remaining execution
            normalizedChanges.add(change.copy(filePath = normalizedPath))
        }

        // 2. Create Snapshot & Validate state preconditions (BEFORE applying anything)
        val snapshot = mutableMapOf<String, ProjectFileEntity?>()
        for (change in normalizedChanges) {
            val path = change.filePath
            val existingFile = repository.getFileByPath(projectId, path)
            snapshot[path] = existingFile

            // Validate based on operation
            when (change.operation) {
                FileOperation.CREATE -> {
                    if (existingFile != null) {
                        return ApplyResult.Conflict("File already exists", path)
                    }
                }
                FileOperation.MODIFY -> {
                    if (existingFile == null) {
                        return ApplyResult.ValidationError("File not found for modification: $path")
                    }
                    if (existingFile.content != change.originalContent) {
                        return ApplyResult.Conflict("Content mismatch: File was modified after proposal.", path)
                    }
                }
                FileOperation.DELETE -> {
                    if (existingFile == null) {
                        return ApplyResult.ValidationError("File not found for deletion: $path")
                    }
                    if (change.originalContent.isNotEmpty() && existingFile.content != change.originalContent) {
                        return ApplyResult.Conflict("Content mismatch: File was modified after proposal.", path)
                    }
                }
            }
        }

        val appliedChanges = mutableListOf<FileChange>()
        val createdFileIds = mutableListOf<String>() // to track newly created files for rollback

        // 3. Apply Changes
        for (change in normalizedChanges) {
            val path = change.filePath
            val existingFile = snapshot[path]

            try {
                when (change.operation) {
                    FileOperation.CREATE -> {
                        val newFile = repository.createFile(projectId, path, change.proposedContent)
                        createdFileIds.add(newFile.id)
                    }
                    FileOperation.MODIFY -> {
                        repository.updateFileContent(existingFile!!.id, change.proposedContent)
                    }
                    FileOperation.DELETE -> {
                        repository.deleteFile(existingFile!!.id)
                    }
                }
                appliedChanges.add(change)
            } catch (e: Exception) {
                // Application error during a change
                val rollbackSuccess = try {
                    rollback(createdFileIds, snapshot)
                    true
                } catch (rollbackEx: Exception) {
                    return ApplyResult.RollbackError(
                        originalError = e.message ?: "Unknown application error",
                        rollbackError = rollbackEx.message ?: "Unknown rollback error"
                    )
                }
                return ApplyResult.ApplyError(e.message ?: "Exception during apply", change, rollbackSuccess)
            }
        }

        return ApplyResult.Success(appliedChanges)
    }

    private suspend fun rollback(
        createdFileIds: List<String>,
        snapshot: Map<String, ProjectFileEntity?>
    ) {
        // Delete any files created during the application
        for (id in createdFileIds) {
            repository.deleteFile(id)
        }

        // Restore original files from snapshot (for MODIFY and DELETE operations)
        for ((_, entity) in snapshot) {
            if (entity != null) {
                // If it exists, it was either modified or deleted.
                // We'll just restore the original entity. 
                // Using restoreFile will essentially do an INSERT with REPLACE (if OnConflictStrategy.REPLACE is used in Dao)
                repository.restoreFile(entity)
            }
        }
    }

    /**
     * Centralized path normalization and validation.
     * Returns null if the path is invalid, absolute, UNC, contains null bytes, 
     * or traverses outside the project directory.
     */
    fun normalizePath(path: String): String? {
        if (path.isBlank()) return null
        if (path.contains("\u0000")) return null

        val unixPath = path.replace("\\", "/")
        
        // Reject absolute paths and UNC
        if (unixPath.startsWith("/")) return null
        if (unixPath.contains("://")) return null // just in case
        if (Regex("^[a-zA-Z]:/").containsMatchIn(unixPath)) return null // Windows absolute e.g. C:/
        if (Regex("^[a-zA-Z]:\\\\").containsMatchIn(unixPath)) return null // Windows absolute e.g. C:\

        val segments = unixPath.split("/")
        val normalizedSegments = mutableListOf<String>()

        for (segment in segments) {
            if (segment.isEmpty() || segment == ".") {
                continue
            }
            if (segment == "..") {
                if (normalizedSegments.isEmpty()) {
                    return null // Traversal escape
                }
                normalizedSegments.removeAt(normalizedSegments.size - 1)
            } else {
                normalizedSegments.add(segment)
            }
        }

        if (normalizedSegments.isEmpty()) return null

        return normalizedSegments.joinToString("/")
    }
}
