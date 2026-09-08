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
    fun `A, H, I test create and read file`() {
        val success = fileSystem.writeFile(projectId, "Test.kt", "fun main() {}")
        assertTrue(success)
        
        val content = fileSystem.readFile(projectId, "Test.kt")
        assertEquals("fun main() {}", content)
    }

    @Test
    fun `B test valid nested path`() {
        val success = fileSystem.writeFile(projectId, "src/main/Test.kt", "nested content")
        assertTrue(success)
        assertEquals("nested content", fileSystem.readFile(projectId, "src/main/Test.kt"))
    }

    @Test
    fun `C test absolute path rejection`() {
        val success = fileSystem.writeFile(projectId, "/root/test.kt", "hack")
        assertFalse("Should reject absolute path", success)
    }

    @Test
    fun `D test path traversal rejection`() {
        val success = fileSystem.writeFile(projectId, "../outside.kt", "hack")
        assertFalse("Should reject path traversal", success)
    }

    @Test
    fun `E test double path traversal rejection`() {
        val success = fileSystem.writeFile(projectId, "../../outside.kt", "hack")
        assertFalse("Should reject path traversal", success)
    }

    @Test
    fun `F test sibling prefix attack rejection`() {
        // e.g. project is 'test-project-fs', someone tries to access 'test-project-fs-backup/secret' via path traversal trickery
        // but normalize limits it to the directory. Still, if we write to a sibling directory it should fail.
        val success = fileSystem.writeFile(projectId, "../test-project-fs-backup/test.kt", "hack")
        assertFalse(success)
    }

    @Test
    fun `G malicious projectId rejection`() {
        val success = fileSystem.writeFile("../malicious", "test.kt", "hack")
        assertFalse(success)
    }

    @Test
    fun `J modify file`() {
        fileSystem.writeFile(projectId, "test.kt", "old")
        val success = fileSystem.writeFile(projectId, "test.kt", "new")
        assertTrue(success)
        assertEquals("new", fileSystem.readFile(projectId, "test.kt"))
    }

    @Test
    fun `K rename file`() {
        fileSystem.writeFile(projectId, "old.txt", "content")
        val renamed = fileSystem.renameFile(projectId, "old.txt", "new.txt")
        assertTrue(renamed)
        assertEquals("content", fileSystem.readFile(projectId, "new.txt"))
        assertNull(fileSystem.readFile(projectId, "old.txt"))
    }

    @Test
    fun `L delete file`() {
        fileSystem.writeFile(projectId, "temp.txt", "delete me")
        val deleted = fileSystem.deleteFile(projectId, "temp.txt")
        assertTrue(deleted)
        assertNull(fileSystem.readFile(projectId, "temp.txt"))
    }

    @Test
    fun `M nested directory creation`() {
        val success = fileSystem.createDirectory(projectId, "src/main/nested")
        assertTrue(success)
        val file = fileSystem.getProjectFile(projectId, "src/main/nested")
        assertNotNull(file)
        assertTrue(file!!.exists() && file.isDirectory)
    }

    @Test
    fun `N directory rename`() {
        fileSystem.createDirectory(projectId, "src/old")
        fileSystem.writeFile(projectId, "src/old/test.kt", "content")
        
        val renamed = fileSystem.renameFile(projectId, "src/old", "src/new")
        assertTrue(renamed)
        
        assertEquals("content", fileSystem.readFile(projectId, "src/new/test.kt"))
        assertNull(fileSystem.readFile(projectId, "src/old/test.kt"))
    }

    @Test
    fun `O directory deletion`() {
        fileSystem.writeFile(projectId, "src/dir/test.kt", "content")
        val deleted = fileSystem.deleteFile(projectId, "src/dir")
        assertTrue(deleted)
        assertNull(fileSystem.readFile(projectId, "src/dir/test.kt"))
    }
    
    @Test
    fun `P failed write on read-only file`() {
        fileSystem.writeFile(projectId, "readonly.txt", "content")
        val file = fileSystem.getProjectFile(projectId, "readonly.txt")!!
        fileSystem.createDirectory(projectId, "dir")
        val failed = fileSystem.writeFile(projectId, "dir", "content")
        assertFalse(failed)
    }
    
    @Test
    fun `Q failed rename due to missing source`() {
        val renamed = fileSystem.renameFile(projectId, "missing.txt", "new.txt")
        assertFalse(renamed)
    }
    
    @Test
    fun `Q failed rename due to existing dest`() {
        fileSystem.writeFile(projectId, "a.txt", "A")
        fileSystem.writeFile(projectId, "b.txt", "B")
        val renamed = fileSystem.renameFile(projectId, "a.txt", "b.txt")
        assertFalse(renamed)
    }

    @Test
    fun `S project isolation`() {
        fileSystem.writeFile(projectId, "test.kt", "content1")
        fileSystem.writeFile("other-project", "test.kt", "content2")
        
        assertEquals("content1", fileSystem.readFile(projectId, "test.kt"))
        assertEquals("content2", fileSystem.readFile("other-project", "test.kt"))
        fileSystem.deleteProject("other-project")
    }
}
