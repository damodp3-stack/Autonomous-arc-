package com.example.ai

import com.example.data.ProjectFileEntity

sealed class ApplyResult {
    data class Success(
        val appliedChanges: List<FileChange>,
        val createdFileIds: List<String> = emptyList(),
        val snapshot: Map<String, ProjectFileEntity?> = emptyMap()
    ) : ApplyResult()
    data class ValidationError(val message: String) : ApplyResult()
    data class Conflict(val message: String, val filePath: String) : ApplyResult()
    data class ApplyError(val message: String, val failedChange: FileChange?, val rollbackSucceeded: Boolean) : ApplyResult()
    data class RollbackError(val originalError: String, val rollbackError: String) : ApplyResult()
}

data class ProjectSnapshot(
    val files: List<SnapshotFile>
)

data class SnapshotFile(
    val id: String,
    val projectId: String,
    val path: String,
    val name: String,
    val extension: String,
    val content: String,
    val isDirectory: Boolean,
    val parentPath: String
)

fun ProjectFileEntity.toSnapshot() = SnapshotFile(
    id = id,
    projectId = projectId,
    path = path,
    name = name,
    extension = extension,
    content = content,
    isDirectory = isDirectory,
    parentPath = parentPath
)
