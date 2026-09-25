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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IdeasVaultTest {

    private lateinit var db: AppDatabase
    private lateinit var ideaDao: IdeaDao
    private lateinit var repository: LocalIdeaRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ideaDao = db.ideaDao()
        repository = LocalIdeaRepository(ideaDao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `test create idea`() = runBlocking {
        val idea = repository.createIdea(
            title = "Build Offline Sync",
            description = "Add conflict resolution and Room synchronization",
            tags = listOf("sync", "room", "offline"),
            status = "DRAFT"
        )

        assertNotNull(idea.id)
        assertEquals("Build Offline Sync", idea.title)
        assertEquals("Add conflict resolution and Room synchronization", idea.description)
        assertEquals("DRAFT", idea.status)
        assertEquals(3, idea.tagList.size)
        assertTrue(idea.tagList.contains("sync"))

        val all = repository.getAllIdeas().first()
        assertEquals(1, all.size)
        assertEquals(idea.id, all[0].id)
    }

    @Test
    fun `test update idea`() = runBlocking {
        val initial = repository.createIdea("Initial Title", "Initial Desc", status = "DRAFT")

        val updated = repository.updateIdea(
            initial.copy(
                title = "Updated Title",
                description = "Updated Desc",
                status = "IN_PROGRESS"
            )
        )

        assertEquals("Updated Title", updated.title)
        assertEquals("IN_PROGRESS", updated.status)

        val fromDb = repository.getIdeaById(initial.id)
        assertEquals("Updated Title", fromDb?.title)
        assertEquals("IN_PROGRESS", fromDb?.status)
    }

    @Test
    fun `test delete idea`() = runBlocking {
        val idea = repository.createIdea("Temp Idea", "To delete")
        assertEquals(1, repository.getAllIdeas().first().size)

        val deleted = repository.deleteIdea(idea.id)
        assertTrue(deleted)

        assertEquals(0, repository.getAllIdeas().first().size)
    }

    @Test
    fun `test search and filter ideas`() = runBlocking {
        repository.createIdea("Compose Navigation Refactor", "Migrate to type safe routing", listOf("compose", "nav"), "COMPLETED")
        repository.createIdea("Token Usage Tracker", "Track prompt and completion tokens in Room", listOf("analytics", "tokens"), "IN_PROGRESS")
        repository.createIdea("Audio Recording Feature", "Media player integration", listOf("media"), "DRAFT")

        // Search by query
        val searchNav = repository.searchIdeas("Navigation").first()
        assertEquals(1, searchNav.size)
        assertEquals("Compose Navigation Refactor", searchNav[0].title)

        // Search by tag
        val searchTag = repository.searchIdeas("tokens").first()
        assertEquals(1, searchTag.size)
        assertEquals("Token Usage Tracker", searchTag[0].title)

        // Filter by status
        val drafts = repository.getIdeasByStatus("DRAFT").first()
        assertEquals(1, drafts.size)
        assertEquals("Audio Recording Feature", drafts[0].title)

        val inProgress = repository.getIdeasByStatus("IN_PROGRESS").first()
        assertEquals(1, inProgress.size)
        assertEquals("Token Usage Tracker", inProgress[0].title)
    }

    @Test
    fun `test convert idea to project`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileSystem = ProjectFileSystem(context)
        val fileRepo = ProjectFileRepository(db.projectFileDao(), fileSystem)
        val projectRepo = LocalProjectRepository(db.projectDao(), db.messageDao(), fileRepo)

        val idea = repository.createIdea("New AI App", "Build full-stack Android AI assistant")

        val newProjectId = repository.convertToProject(idea.id, projectRepo)
        assertNotNull(newProjectId)

        val project = projectRepo.getProject(newProjectId!!).first()
        assertNotNull(project)
        assertEquals("New AI App", project?.name)

        val updatedIdea = repository.getIdeaById(idea.id)
        assertEquals(newProjectId, updatedIdea?.projectAssociation)
        assertEquals("IN_PROGRESS", updatedIdea?.status)
    }
}
