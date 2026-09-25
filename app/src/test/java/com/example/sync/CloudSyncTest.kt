package com.example.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
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
class CloudSyncTest {

    private lateinit var db: AppDatabase
    private lateinit var syncRepo: RealSyncRepository
    private lateinit var mockProvider: MockCloudSyncProvider

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        mockProvider = MockCloudSyncProvider()
        syncRepo = RealSyncRepository(
            syncMetadataDao = db.syncMetadataDao(),
            projectDao = db.projectDao(),
            ideaDao = db.ideaDao(),
            mediaDao = db.mediaDao(),
            initialProvider = mockProvider
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `test offline first local provider skips remote sync`() = runBlocking {
        val localOnlyRepo = RealSyncRepository(
            syncMetadataDao = db.syncMetadataDao(),
            projectDao = db.projectDao(),
            ideaDao = db.ideaDao(),
            mediaDao = db.mediaDao(),
            initialProvider = LocalOnlySyncProvider()
        )

        val summary = localOnlyRepo.syncAll()
        assertEquals(0, summary.uploaded)
        assertEquals(0, summary.downloaded)
        assertEquals(0, summary.conflicts)
        assertTrue(summary.errors.isNotEmpty())
        assertTrue(summary.errors[0].contains("Offline-first mode"))
    }

    @Test
    fun `test mock cloud push and pull lifecycle`() = runBlocking {
        // Create an idea and mark for sync
        val idea = IdeaEntity(
            id = "idea-123",
            title = "Cloud Sync Idea",
            description = "Test sync lifecycle"
        )
        db.ideaDao().insert(idea)
        syncRepo.markForSync(SyncEntityType.IDEA, "idea-123")

        val pendingBefore = syncRepo.getPendingCount().first()
        assertEquals(1, pendingBefore)

        // Execute sync
        val summary = syncRepo.syncAll(ConflictResolutionStrategy.LAST_WRITE_WINS)
        assertEquals(1, summary.uploaded)
        assertEquals(0, summary.conflicts)

        // Pending count should now be 0
        val pendingAfter = syncRepo.getPendingCount().first()
        assertEquals(0, pendingAfter)

        // Check metadata in Room
        val meta = db.syncMetadataDao().getMetadata(SyncEntityType.IDEA.name, "idea-123")
        assertNotNull(meta)
        assertEquals(SyncStatus.SYNCED.name, meta?.syncStatus)
        assertEquals("remote_idea-123", meta?.remoteId)
    }

    @Test
    fun `test conflict detection in sync`() = runBlocking {
        val project = ProjectEntity(
            id = "proj-conflicted",
            name = "Conflicted Project",
            updatedAt = System.currentTimeMillis()
        )
        db.projectDao().insertProject(project)
        syncRepo.markForSync(SyncEntityType.PROJECT, "proj-conflicted")

        // Configure mock provider to simulate conflict on this project
        mockProvider.simulatedConflictKey = "${SyncEntityType.PROJECT}:proj-conflicted"

        val summary = syncRepo.syncAll()
        assertEquals(0, summary.uploaded)
        assertEquals(1, summary.conflicts)

        val meta = db.syncMetadataDao().getMetadata(SyncEntityType.PROJECT.name, "proj-conflicted")
        assertNotNull(meta)
        assertEquals(SyncStatus.CONFLICT.name, meta?.syncStatus)
        assertNotNull(meta?.syncError)
        assertTrue(meta?.syncError?.contains("conflict") == true)
    }

    @Test
    fun `test connection check on mock and local providers`() = runBlocking {
        val mockResult = mockProvider.testConnection()
        assertTrue(mockResult.isSuccess)
        assertTrue(mockResult.getOrNull()?.contains("successful") == true)

        mockProvider.shouldFailConnection = true
        val failedResult = mockProvider.testConnection()
        assertFalse(failedResult.isSuccess)

        val localOnly = LocalOnlySyncProvider()
        val localResult = localOnly.testConnection()
        assertTrue(localResult.isSuccess)
        assertTrue(localResult.getOrNull()?.contains("Offline-first") == true)
    }
}
