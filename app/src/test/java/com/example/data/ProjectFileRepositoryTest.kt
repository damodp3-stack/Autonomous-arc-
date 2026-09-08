package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectFileRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ProjectFileDao
    private lateinit var fileSystem: ProjectFileSystem
    private lateinit var repository: ProjectFileRepository
    private val projectId = "test-repo-project"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.projectFileDao()
        fileSystem = ProjectFileSystem(context)
        fileSystem.deleteProject(projectId)
        repository = ProjectFileRepository(dao, fileSystem)
    }

    @After
    fun teardown() {
        db.close()
        fileSystem.deleteProject(projectId)
    }

    @Test
    fun `T startup synchronization - room to fs`() = runBlocking {
        // Setup room with file that is missing in fs
        val fileEntity = ProjectFileEntity(
            projectId = projectId,
            path = "test.kt",
            name = "test.kt",
            content = "room content",
            isDirectory = false,
            parentPath = "",
            extension = "kt"
        )
        dao.insertFile(fileEntity)
        
        // Sync
        repository.syncProjectFilesToSystem(projectId)
        
        // Verify fs was populated
        assertEquals("room content", fileSystem.readFile(projectId, "test.kt"))
    }

    @Test
    fun `T startup synchronization - fs to room conflict`() = runBlocking {
        // Setup room with file
        val fileEntity = ProjectFileEntity(
            projectId = projectId,
            path = "test.kt",
            name = "test.kt",
            content = "room content",
            isDirectory = false,
            parentPath = "",
            extension = "kt"
        )
        dao.insertFile(fileEntity)
        
        // Setup fs with different content
        fileSystem.writeFile(projectId, "test.kt", "fs content")
        
        // Sync
        repository.syncProjectFilesToSystem(projectId)
        
        // Verify room was updated to match fs
        val updatedFile = dao.getFileByPath(projectId, "test.kt")
        assertEquals("fs content", updatedFile?.content)
    }
}
