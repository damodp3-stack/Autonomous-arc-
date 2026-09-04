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

        // 1. Path Security Validation
        for (change in proposal.changes) {
            val validPath = isValidPath(change.filePath)
            if (!validPath) {
                return ApplyResult.ValidationError("Security Error: Invalid or traversal path detected - ${change.filePath}")
            }
        }

        // 2. Create Snapshot
        // We will keep a map of filePath to its original ProjectFileEntity.
        // For new files (CREATE), the entity will be null in the map initially.
        val snapshot = mutableMapOf<String, ProjectFileEntity?>()
        for (change in proposal.changes) {
            val path = normalizePath(change.filePath)
            val existingFile = repository.getFileByPath(projectId, path)
            snapshot[path] = existingFile
        }

        val appliedChanges = mutableListOf<FileChange>()
        val createdFileIds = mutableListOf<String>() // to track newly created files for rollback

        // 3. Apply Changes
        for (change in proposal.changes) {
            val path = normalizePath(change.filePath)
            val existingFile = snapshot[path]

            try {
                when (change.operation) {
                    FileOperation.CREATE -> {
                        if (existingFile != null) {
                            rollback(createdFileIds, snapshot)
                            return ApplyResult.Conflict("File already exists", path)
                        }
                        val newFile = repository.createFile(projectId, path, change.proposedContent)
                        createdFileIds.add(newFile.id)
                    }
                    FileOperation.MODIFY -> {
                        if (existingFile == null) {
                            rollback(createdFileIds, snapshot)
                            return ApplyResult.ValidationError("File not found for modification: $path")
                        }
                        if (existingFile.content != change.originalContent) {
                            rollback(createdFileIds, snapshot)
                            return ApplyResult.Conflict("Content mismatch: File was modified after proposal.", path)
                        }
                        repository.updateFileContent(existingFile.id, change.proposedContent)
                    }
                    FileOperation.DELETE -> {
                        if (existingFile == null) {
                            rollback(createdFileIds, snapshot)
                            return ApplyResult.ValidationError("File not found for deletion: $path")
                        }
                        // For delete, verify content if provided
                        if (change.originalContent.isNotEmpty() && existingFile.content != change.originalContent) {
                            rollback(createdFileIds, snapshot)
                            return ApplyResult.Conflict("Content mismatch: File was modified after proposal.", path)
                        }
                        repository.deleteFile(existingFile.id)
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

    private fun isValidPath(path: String): Boolean {
        if (path.isBlank()) return false
        val normalized = normalizePath(path)
        if (normalized.startsWith("/") || normalized.contains("../") || normalized.contains("..\\")) {
            return false
        }
        return true
    }

    private fun normalizePath(path: String): String {
        return path.replace("\\", "/").trim()
    }
}
