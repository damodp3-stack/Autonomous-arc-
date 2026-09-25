package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val createdTimestamp: Long = System.currentTimeMillis(),
    val filePath: String,
    val fileSizeBytes: Long = 0L,
    val projectAssociation: String? = null,
    val chatAssociation: String? = null
) {
    val isImage: Boolean get() = mediaType.equals("IMAGE", ignoreCase = true)
    val isVideo: Boolean get() = mediaType.equals("VIDEO", ignoreCase = true)
}
