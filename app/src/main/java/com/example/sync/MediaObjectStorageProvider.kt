package com.example.sync

import java.io.File

/**
 * Abstraction for binary media object storage (e.g. S3, Cloud Storage, Supabase Storage).
 * Allows binary media files to be uploaded or downloaded independently of metadata synchronization.
 */
interface MediaObjectStorageProvider {
    val providerName: String
    val isConfigured: Boolean

    suspend fun uploadMedia(localFile: File, remoteKey: String, mimeType: String): Result<String>
    suspend fun downloadMedia(remoteKey: String, destinationFile: File): Result<File>
    suspend fun deleteMedia(remoteKey: String): Result<Unit>
    suspend fun getMediaUrl(remoteKey: String): String?
}

/**
 * Default local-only media object storage implementation.
 */
class LocalOnlyMediaStorageProvider : MediaObjectStorageProvider {
    override val providerName: String = "Local Only Storage"
    override val isConfigured: Boolean = false

    override suspend fun uploadMedia(localFile: File, remoteKey: String, mimeType: String): Result<String> {
        return Result.failure(IllegalStateException("Cloud media storage provider is not configured. Media is stored in app-private local storage."))
    }

    override suspend fun downloadMedia(remoteKey: String, destinationFile: File): Result<File> {
        return Result.failure(IllegalStateException("Cloud media storage provider is not configured."))
    }

    override suspend fun deleteMedia(remoteKey: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getMediaUrl(remoteKey: String): String? = null
}
