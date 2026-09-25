package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ideas")
data class IdeaEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val status: String = "DRAFT", // "DRAFT", "IN_PROGRESS", "COMPLETED", "ARCHIVED"
    val tags: String = "", // comma-separated tags e.g. "ui,ai,database"
    val projectAssociation: String? = null,
    val chatAssociation: String? = null
) {
    val tagList: List<String>
        get() = tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
