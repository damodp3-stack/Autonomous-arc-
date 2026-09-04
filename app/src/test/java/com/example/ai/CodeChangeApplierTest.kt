package com.example.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProjectFileDao
import com.example.data.ProjectFileRepository
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
class CodeChangeApplierTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProjectFileDao
    private lateinit var repository: ProjectFileRepository
    private lateinit var applier: CodeChangeApplier

    private val projectId = "test-project"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.projectFileDao()
        repository = ProjectFileRepository(dao)
        applier = CodeChangeApplier(repository)
    }

    @After
    fun teardown() {
        db.close()
    }

    // CREATE
    @Test
    fun `test CREATE operation valid`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Create file",
            explanation = "test",
            changes = listOf(
                FileChange(
                    filePath = "src/Main.kt",
                    operation = FileOperation.CREATE,
                    proposedContent = "Hello World"
                )
            )
        )

        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Success)

        val createdFile = repository.getFileByPath(projectId, "src/Main.kt")
        assertNotNull(createdFile)
        assertEquals("Hello World", createdFile?.content)
    }

    @Test
    fun `test CREATE operation duplicate rejected`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Old Content")

        val proposal = CodeChangeProposal(
            summary = "Create file",
            explanation = "test",
            changes = listOf(
                FileChange(
                    filePath = "src/Main.kt",
                    operation = FileOperation.CREATE,
                    proposedContent = "Hello World"
                )
            )
        )

        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Conflict)
    }

    @Test
    fun `test duplicate CREATE paths in same proposal rejected`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Create files",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Main.kt", operation = FileOperation.CREATE, proposedContent = "Content 1"),
                FileChange(filePath = "src/Main.kt", operation = FileOperation.CREATE, proposedContent = "Content 2")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError)
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertNull(file)
    }

    // MODIFY
    @Test
    fun `test MODIFY valid`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Old Content")

        val proposal = CodeChangeProposal(
            summary = "Modify",
            explanation = "test",
            changes = listOf(
                FileChange(
                    filePath = "src/Main.kt",
                    operation = FileOperation.MODIFY,
                    originalContent = "Old Content",
                    proposedContent = "New Content"
                )
            )
        )

        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Success)
        
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertEquals("New Content", file?.content)
    }

    @Test
    fun `test MODIFY missing file`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Modify",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Missing.kt", operation = FileOperation.MODIFY, originalContent = "Old Content", proposedContent = "New Content")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError)
    }

    @Test
    fun `test MODIFY mismatch conflict`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Edited Content")

        val proposal = CodeChangeProposal(
            summary = "Modify",
            explanation = "test",
            changes = listOf(
                FileChange(
                    filePath = "src/Main.kt",
                    operation = FileOperation.MODIFY,
                    originalContent = "Old Content",
                    proposedContent = "New Content"
                )
            )
        )

        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Conflict)

        // Ensure not changed
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertEquals("Edited Content", file?.content)
    }

    @Test
    fun `test duplicate conflicting paths in proposal`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Old Content")

        val proposal = CodeChangeProposal(
            summary = "Modify and Delete",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Main.kt", operation = FileOperation.MODIFY, originalContent = "Old Content", proposedContent = "New Content"),
                FileChange(filePath = "src/Main.kt", operation = FileOperation.DELETE, originalContent = "Old Content")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError)
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertEquals("Old Content", file?.content) // Unchanged
    }

    // DELETE
    @Test
    fun `test DELETE valid`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Old Content")

        val proposal = CodeChangeProposal(
            summary = "Delete",
            explanation = "test",
            changes = listOf(
                FileChange(
                    filePath = "src/Main.kt",
                    operation = FileOperation.DELETE,
                    originalContent = "Old Content"
                )
            )
        )

        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Success)
        
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertNull(file)
    }

    @Test
    fun `test DELETE missing file`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Delete",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Missing.kt", operation = FileOperation.DELETE, originalContent = "Old Content")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError)
    }

    @Test
    fun `test DELETE mismatch conflict`() = runBlocking {
        repository.createFile(projectId, "src/Main.kt", "Edited Content")
        val proposal = CodeChangeProposal(
            summary = "Delete",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Main.kt", operation = FileOperation.DELETE, originalContent = "Old Content")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.Conflict)
        val file = repository.getFileByPath(projectId, "src/Main.kt")
        assertEquals("Edited Content", file?.content)
    }

    // Security
    @Test
    fun `test Path Traversal rejected`() = runBlocking {
        val paths = listOf(
            "../secret.txt",
            "foo/../../secret.txt",
            "..\\secret.txt",
            "/etc/passwd",
            "C:/Windows/System32",
            "C:\\Windows\\System32",
            "src/\u0000/null.txt",
            ""
        )
        for (p in paths) {
            val result = applier.applyProposal(projectId, CodeChangeProposal(
                summary = "Hack",
                explanation = "test",
                changes = listOf(FileChange(filePath = p, operation = FileOperation.CREATE, proposedContent = "Hacked"))
            ))
            assertTrue("Path $p should be rejected", result is ApplyResult.ValidationError)
        }
    }

    @Test
    fun `test duplicate normalized paths rejected`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Duplicate Paths",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Main.kt", operation = FileOperation.CREATE, proposedContent = "A"),
                FileChange(filePath = "src/./Main.kt", operation = FileOperation.MODIFY, originalContent = "A", proposedContent = "B")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError)
    }

    // Rollback
    @Test
    fun `test atomic validation - valid change not applied if invalid change exists`() = runBlocking {
        val proposal = CodeChangeProposal(
            summary = "Atomic",
            explanation = "test",
            changes = listOf(
                FileChange(filePath = "src/Valid.kt", operation = FileOperation.CREATE, proposedContent = "Valid"),
                FileChange(filePath = "src/Missing.kt", operation = FileOperation.MODIFY, originalContent = "Old", proposedContent = "New")
            )
        )
        val result = applier.applyProposal(projectId, proposal)
        assertTrue(result is ApplyResult.ValidationError) // fails BEFORE anything applied
        assertNull(repository.getFileByPath(projectId, "src/Valid.kt"))
    }
}
