package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "project_files")
data class ProjectFileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val path: String, // e.g., "src/main.kt"
    val name: String, // e.g., "main.kt"
    val extension: String, // e.g., "kt"
    val content: String,
    val isDirectory: Boolean = false,
    val parentPath: String = "", // e.g., "src"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
