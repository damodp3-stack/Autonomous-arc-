package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MediaVaultTest {

    private lateinit var db: AppDatabase
    private lateinit var mediaDao: MediaDao
    private lateinit var repository: LocalMediaRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mediaDao = db.mediaDao()
        repository = LocalMediaRepository(mediaDao, context)
    }

    @After
    fun teardown() {
        db.close()
        val dir = File(context.filesDir, "media_vault")
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `test media metadata persistence`() = runBlocking {
        val sampleBytes = "test image data content".toByteArray()
        val result = repository.saveMedia(
            filename = "banner.png",
            mediaType = "IMAGE",
            bytes = sampleBytes,
            projectAssociation = "proj-1"
        )

        assertTrue(result.isSuccess)
        val media = result.getOrNull()!!
        assertEquals("banner.png", media.filename)
        assertEquals("IMAGE", media.mediaType)
        assertEquals("proj-1", media.projectAssociation)
        assertEquals(sampleBytes.size.toLong(), media.fileSizeBytes)

        val file = File(media.filePath)
        assertTrue(file.exists())
        assertEquals(sampleBytes.size.toLong(), file.length())

        val stored = repository.getAllMedia().first()
        assertEquals(1, stored.size)
        assertEquals(media.id, stored[0].id)
    }

    @Test
    fun `test media rename`() = runBlocking {
        val bytes = "banner content".toByteArray()
        val initial = repository.saveMedia("old_name.png", "IMAGE", bytes).getOrNull()!!

        val renameResult = repository.renameMedia(initial.id, "new_name.png")
        assertTrue(renameResult.isSuccess)

        val updated = renameResult.getOrNull()!!
        assertEquals("new_name.png", updated.filename)

        val updatedFile = File(updated.filePath)
        assertTrue(updatedFile.exists())
        assertTrue(updatedFile.name.endsWith("new_name.png"))

        val fromDb = repository.getMediaById(initial.id)
        assertNotNull(fromDb)
        assertEquals("new_name.png", fromDb?.filename)
    }

    @Test
    fun `test media delete`() = runBlocking {
        val bytes = "to delete".toByteArray()
        val media = repository.saveMedia("delete_me.png", "IMAGE", bytes).getOrNull()!!
        val file = File(media.filePath)
        assertTrue(file.exists())

        val deleted = repository.deleteMedia(media.id)
        assertTrue(deleted)

        assertFalse(file.exists())
        val all = repository.getAllMedia().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `test missing file handling`() = runBlocking {
        val bytes = "transient content".toByteArray()
        val media = repository.saveMedia("transient.png", "IMAGE", bytes).getOrNull()!!

        assertTrue(repository.isMediaFileValid(media))

        // Delete physical file from disk behind Room's back
        val file = File(media.filePath)
        file.delete()

        assertFalse(repository.isMediaFileValid(media))
        assertNull(repository.getMediaFile(media))
    }

    @Test
    fun `test path traversal defense`() = runBlocking {
        val bytes = "payload".toByteArray()
        val evilResult = repository.saveMedia("../../../etc_passwd.png", "IMAGE", bytes)

        assertTrue(evilResult.isSuccess)
        val media = evilResult.getOrNull()!!
        // Sanitizer strips leading slashes and ../ to prevent traversal
        assertFalse(media.filePath.contains(".."))
        assertTrue(media.filePath.startsWith(context.filesDir.path))
    }
}
