package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
class ProjectFileSystemTest {
    private lateinit var context: Context
    private lateinit var fileSystem: ProjectFileSystem
    private val projectId = "test-project-fs"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fileSystem = ProjectFileSystem(context)
        fileSystem.deleteProject(projectId)
    }

    @After
    fun teardown() {
        fileSystem.deleteProject(projectId)
    }

    @Test
    fun `test create and read file`() {
        val success = fileSystem.writeFile(projectId, "src/main/Test.kt", "fun main() {}")
        assertTrue(success)
        
        val content = fileSystem.readFile(projectId, "src/main/Test.kt")
        assertEquals("fun main() {}", content)
    }

    @Test
    fun `test path traversal rejection`() {
        val success = fileSystem.writeFile(projectId, "../outside.kt", "hack")
        assertFalse("Should reject path traversal", success)
    }

    @Test
    fun `test absolute path rejection`() {
        val success = fileSystem.writeFile(projectId, "/root/test.kt", "hack")
        assertFalse("Should reject absolute path", success)
    }

    @Test
    fun `test delete file`() {
        fileSystem.writeFile(projectId, "temp.txt", "delete me")
        val deleted = fileSystem.deleteFile(projectId, "temp.txt")
        assertTrue(deleted)
        assertNull(fileSystem.readFile(projectId, "temp.txt"))
    }

    @Test
    fun `test rename file`() {
        fileSystem.writeFile(projectId, "old.txt", "content")
        val renamed = fileSystem.renameFile(projectId, "old.txt", "new.txt")
        assertTrue(renamed)
        assertEquals("content", fileSystem.readFile(projectId, "new.txt"))
        assertNull(fileSystem.readFile(projectId, "old.txt"))
    }
}
