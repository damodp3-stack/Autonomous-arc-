package com.example.github

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GitHubConfigDao
import com.example.data.GitHubConfigEntity
import com.example.data.GitHubConfigRepository
import kotlinx.coroutines.flow.firstOrNull
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
class GitHubIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: GitHubConfigDao
    private lateinit var repository: GitHubConfigRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.githubConfigDao()
        repository = GitHubConfigRepository(dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `test GitHub config persistence and isolation`() = runBlocking {
        val proj1 = "project-1"
        val proj2 = "project-2"

        val config1 = GitHubConfigEntity(
            projectId = proj1,
            owner = "user1",
            repository = "repo1",
            branch = "main",
            isConnected = true
        )
        
        val config2 = GitHubConfigEntity(
            projectId = proj2,
            owner = "user2",
            repository = "repo2",
            branch = "develop",
            isConnected = true
        )

        repository.saveConfig(config1)
        repository.saveConfig(config2)

        val retrieved1 = repository.getConfigForProject(proj1).firstOrNull()
        assertNotNull(retrieved1)
        assertEquals("repo1", retrieved1?.repository)

        val retrieved2 = repository.getConfigForProject(proj2).firstOrNull()
        assertNotNull(retrieved2)
        assertEquals("repo2", retrieved2?.repository)
    }

    @Test
    fun `test disconnect removes config`() = runBlocking {
        val projId = "test-disconnect"
        val config = GitHubConfigEntity(
            projectId = projId,
            owner = "user",
            repository = "repo",
            branch = "main",
            isConnected = true
        )
        repository.saveConfig(config)
        
        assertNotNull(repository.getConfigForProject(projId).firstOrNull())
        
        repository.clearConfig(projId)
        
        assertNull(repository.getConfigForProject(projId).firstOrNull())
    }
}
