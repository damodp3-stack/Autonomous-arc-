package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

interface MediaRepository {
    fun getAllMedia(): Flow<List<MediaEntity>>
    fun getMediaByType(type: String): Flow<List<MediaEntity>>
    fun getMediaForProject(projectId: String): Flow<List<MediaEntity>>
    suspend fun getMediaById(id: String): MediaEntity?
    suspend fun saveMedia(
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        projectAssociation: String? = null,
        chatAssociation: String? = null
    ): Result<MediaEntity>
    suspend fun renameMedia(id: String, newFilename: String): Result<MediaEntity>
    suspend fun deleteMedia(id: String): Boolean
    suspend fun updateProjectAssociation(id: String, projectId: String?): Boolean
    fun getMediaFile(media: MediaEntity): File?
    fun isMediaFileValid(media: MediaEntity): Boolean
}

class LocalMediaRepository(
    private val mediaDao: MediaDao,
    private val context: Context
) : MediaRepository {

    private val mediaDir: File by lazy {
        val dir = File(context.filesDir, "media_vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    override fun getAllMedia(): Flow<List<MediaEntity>> = mediaDao.getAllMedia()

    override fun getMediaByType(type: String): Flow<List<MediaEntity>> =
        mediaDao.getMediaByType(type)

    override fun getMediaForProject(projectId: String): Flow<List<MediaEntity>> =
        mediaDao.getMediaForProject(projectId)

    override suspend fun getMediaById(id: String): MediaEntity? =
        mediaDao.getMediaById(id)

    override suspend fun saveMedia(
        filename: String,
        mediaType: String,
        bytes: ByteArray,
        projectAssociation: String?,
        chatAssociation: String?
    ): Result<MediaEntity> = withContext(Dispatchers.IO) {
        try {
            val sanitizedFilename = sanitizeFilename(filename)
            val uniqueId = UUID.randomUUID().toString()
            val targetFile = File(mediaDir, "${uniqueId}_$sanitizedFilename")

            // Security: Path traversal validation
            val canonicalTarget = targetFile.canonicalFile
            val canonicalDir = mediaDir.canonicalFile
            if (!canonicalTarget.path.startsWith(canonicalDir.path)) {
                return@withContext Result.failure(SecurityException("Path traversal attempt detected in media filename"))
            }

            FileOutputStream(canonicalTarget).use { output ->
                output.write(bytes)
                output.flush()
            }

            val entity = MediaEntity(
                id = uniqueId,
                filename = sanitizedFilename,
                mediaType = mediaType.uppercase(),
                createdTimestamp = System.currentTimeMillis(),
                filePath = canonicalTarget.absolutePath,
                fileSizeBytes = canonicalTarget.length(),
                projectAssociation = projectAssociation,
                chatAssociation = chatAssociation
            )
            mediaDao.insert(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameMedia(id: String, newFilename: String): Result<MediaEntity> =
        withContext(Dispatchers.IO) {
            try {
                val existing = mediaDao.getMediaById(id)
                    ?: return@withContext Result.failure(IllegalArgumentException("Media not found: $id"))

                val sanitizedNewName = sanitizeFilename(newFilename)
                val oldFile = File(existing.filePath)
                val newFile = File(mediaDir, "${existing.id}_$sanitizedNewName")

                // Security: Path traversal validation
                val canonicalNew = newFile.canonicalFile
                val canonicalDir = mediaDir.canonicalFile
                if (!canonicalNew.path.startsWith(canonicalDir.path)) {
                    return@withContext Result.failure(SecurityException("Invalid media target path"))
                }

                if (oldFile.exists()) {
                    oldFile.renameTo(canonicalNew)
                }

                val updatedEntity = existing.copy(
                    filename = sanitizedNewName,
                    filePath = canonicalNew.absolutePath
                )
                mediaDao.update(updatedEntity)
                Result.success(updatedEntity)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteMedia(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val existing = mediaDao.getMediaById(id)
            if (existing != null) {
                val file = File(existing.filePath)
                if (file.exists()) {
                    val canonicalFile = file.canonicalFile
                    val canonicalDir = mediaDir.canonicalFile
                    if (canonicalFile.path.startsWith(canonicalDir.path)) {
                        canonicalFile.delete()
                    }
                }
                mediaDao.deleteById(id)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun updateProjectAssociation(id: String, projectId: String?): Boolean =
        withContext(Dispatchers.IO) {
            val existing = mediaDao.getMediaById(id) ?: return@withContext false
            mediaDao.update(existing.copy(projectAssociation = projectId))
            true
        }

    override fun getMediaFile(media: MediaEntity): File? {
        val file = File(media.filePath)
        return if (file.exists() && file.isFile) file else null
    }

    override fun isMediaFileValid(media: MediaEntity): Boolean {
        val file = File(media.filePath)
        return file.exists() && file.length() > 0L
    }

    private fun sanitizeFilename(name: String): String {
        val cleaned = name.replace("..", "_")
            .replace(Regex("[/\\\\?%*:|\"<>]"), "_")
            .trim()
        return if (cleaned.isBlank()) "media_${System.currentTimeMillis()}" else cleaned
    }
}
