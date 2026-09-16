package com.example.ai


import com.squareup.moshi.JsonClass


import java.util.UUID

enum class FileOperation {
    CREATE, MODIFY, DELETE, RENAME
}

@JsonClass(generateAdapter = true)
data class FileChange(
    val filePath: String,
    val operation: FileOperation,
    val newFilePath: String? = null,
    val originalContent: String = "",
    val proposedContent: String = ""
)

@JsonClass(generateAdapter = true)
data class CodeChangeProposal(
    val id: String = UUID.randomUUID().toString(),
    val summary: String,
    val explanation: String,
    val changes: List<FileChange>,
    val createdAt: Long = System.currentTimeMillis()
)
