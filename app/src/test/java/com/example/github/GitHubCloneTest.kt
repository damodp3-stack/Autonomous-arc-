package com.example.github

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GitHubConfigRepository
import com.example.data.ProjectFileEntity
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem
import com.example.ui.GitHubViewModel
import com.example.ui.RepositoryDiscoveryState
import kotlinx.coroutines.flow.firstOrNull
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
class GitHubCloneTest {
    private lateinit var db: AppDatabase
    private lateinit var fileRepository: ProjectFileRepository
    private lateinit var fileSystem: ProjectFileSystem
    private lateinit var configRepository: GitHubConfigRepository
    private lateinit var githubService: MockGitHubService
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        fileSystem = ProjectFileSystem(context)
        fileRepository = ProjectFileRepository(db.projectFileDao(), fileSystem)
        configRepository = GitHubConfigRepository(db.githubConfigDao())
        githubService = MockGitHubService()
        runBlocking { githubService.authenticate("test-token") }
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun createViewModel(projectId: String): GitHubViewModel {
        return GitHubViewModel(projectId, githubService, githubService, configRepository, fileRepository)
    }

    private suspend fun doClone(viewModel: GitHubViewModel, repoName: String) {
        viewModel.fetchRepositories()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(10)
        
        val state1 = viewModel.discoveryState.value
        if (state1 !is RepositoryDiscoveryState.RepositoriesLoaded) {
            throw Exception("Expected RepositoriesLoaded but was $state1")
        }
        val repo = state1.repositories.find { it.name == repoName }!!
        
        viewModel.selectRepository(repo)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(10)
        
        viewModel.connectRepository(force = true) // force to bypass conflict state for empty project
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        kotlinx.coroutines.delay(100)
    }

    @Test
    fun `test successful normal clone`() = runBlocking {
        val projId = "proj-normal"
        val vm = createViewModel(projId)
        doClone(vm, "normal-repo")

        val files = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertTrue("Files should be created", files.isNotEmpty())
        
        val readme = files.find { it.name == "README.md" }
        assertNotNull(readme)
        assertEquals("IyBNb2NrIFJlcG8KCk1vY2sgY29udGVudA==", String(android.util.Base64.encode(readme?.content?.toByteArray(), android.util.Base64.DEFAULT)).replace("\\n".toRegex(), "").replace("\\r".toRegex(), ""))
        
        val fsFile = fileSystem.getProjectFile(projId, "README.md")
        assertTrue(fsFile?.exists() == true)
        
        // Binary verification
        val icon = files.find { it.name == "icon.png" }
        assertNotNull(icon)
        assertEquals("[BINARY FILE]", icon?.content) // stored as binary marker in DB
        
        val iconFs = fileSystem.getProjectFile(projId, "assets/icon.png")
        assertTrue(iconFs?.exists() == true)
        val iconBytes = iconFs!!.readBytes()
        val originalBase64 = "iVBORw0KGgo="
        val decodedOriginal = android.util.Base64.decode(originalBase64, android.util.Base64.DEFAULT)
        assertArrayEquals(decodedOriginal, iconBytes)
        
        // ensure staging is cleaned up
        val stagingDir = File(context.filesDir, "projects/$projId-staging")
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `test nested directories`() = runBlocking {
        val projId = "proj-nested"
        val vm = createViewModel(projId)
        doClone(vm, "normal-repo")
        
        val files = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertNotNull(files.find { it.path == "src" && it.isDirectory })
        assertNotNull(files.find { it.path == "src/main.kt" && !it.isDirectory })
        assertNotNull(files.find { it.path == "assets" && it.isDirectory })
        assertNotNull(files.find { it.path == "assets/icon.png" && !it.isDirectory })
    }

    @Test
    fun `test truncated tree fallback`() = runBlocking {
        val projId = "proj-truncated"
        val vm = createViewModel(projId)
        doClone(vm, "truncated-repo")
        
        val files = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertNotNull(files.find { it.path == "src/main.kt" })
    }

    @Test
    fun `test duplicate normalized path`() = runBlocking {
        val projId = "proj-duplicate"
        val vm = createViewModel(projId)
        doClone(vm, "duplicate-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("Duplicate path collision detected"))
    }

    @Test
    fun `test directory file collision`() = runBlocking {
        val projId = "proj-collision"
        val vm = createViewModel(projId)
        doClone(vm, "collision-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("Binary/download failure at src"))
    }

    @Test
    fun `test invalid traversal path`() = runBlocking {
        val projId = "proj-invalid"
        val vm = createViewModel(projId)
        doClone(vm, "invalid-path-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("Invalid path detected"))
    }

    @Test
    fun `test oversized individual file`() = runBlocking {
        val projId = "proj-oversized-file"
        val vm = createViewModel(projId)
        doClone(vm, "oversized-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("too large"))
    }

    @Test
    fun `test oversized total repository`() = runBlocking {
        val projId = "proj-oversized-total"
        val vm = createViewModel(projId)
        doClone(vm, "oversized-total-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("exceeds maximum total size"))
    }

    @Test
    fun `test too many files`() = runBlocking {
        val projId = "proj-too-many"
        val vm = createViewModel(projId)
        doClone(vm, "too-many-files-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("too many files"))
    }

    @Test
    fun `test maximum depth`() = runBlocking {
        val projId = "proj-deep"
        val vm = createViewModel(projId)
        doClone(vm, "deep-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("depth too large"))
    }

    @Test
    fun `test download failure`() = runBlocking {
        val projId = "proj-download-fail"
        val vm = createViewModel(projId)
        doClone(vm, "download-failure-repo")
        
        val state = vm.discoveryState.value
        assertTrue(state is RepositoryDiscoveryState.Error)
        assertTrue((state as RepositoryDiscoveryState.Error).message.contains("Network download failure"))
    }

    @Test
    fun `test failed clone preserves an existing workspace and room records`() = runBlocking {
        val projId = "proj-preserve"
        // Setup existing workspace
        fileRepository.createFile(projId, "original.kt", "println(1)")
        
        val filesBefore = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertEquals(1, filesBefore.size)
        assertTrue(fileSystem.getProjectFile(projId, "original.kt")?.exists() == true)
        
        val vm = createViewModel(projId)
        doClone(vm, "download-failure-repo") // will fail
        
        // Assert failure
        assertTrue(vm.discoveryState.value is RepositoryDiscoveryState.Error)
        
        // Assert preservation
        val filesAfter = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertEquals(1, filesAfter.size)
        assertEquals("original.kt", filesAfter[0].name)
        assertTrue(fileSystem.getProjectFile(projId, "original.kt")?.exists() == true)
        
        // Assert staging is cleaned
        val stagingDir = File(context.filesDir, "projects/$projId-staging")
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `test successful clone replaces old workspace correctly`() = runBlocking {
        val projId = "proj-replace"
        fileRepository.createFile(projId, "old.kt", "println(1)")
        
        val vm = createViewModel(projId)
        doClone(vm, "normal-repo") // success
        
        val filesAfter = fileRepository.getFilesForProject(projId).firstOrNull() ?: emptyList()
        assertNull(filesAfter.find { it.name == "old.kt" })
        assertNotNull(filesAfter.find { it.name == "README.md" })
        
        assertFalse(fileSystem.getProjectFile(projId, "old.kt")?.exists() == true)
        assertTrue(fileSystem.getProjectFile(projId, "README.md")?.exists() == true)
    }

    @Test
    fun `test project isolation between two projects`() = runBlocking {
        val proj1 = "proj-iso-1"
        val proj2 = "proj-iso-2"
        
        val vm1 = createViewModel(proj1)
        doClone(vm1, "normal-repo")
        
        val vm2 = createViewModel(proj2)
        doClone(vm2, "download-failure-repo") // fail
        
        val files1 = fileRepository.getFilesForProject(proj1).firstOrNull() ?: emptyList()
        assertTrue(files1.isNotEmpty())
        
        val files2 = fileRepository.getFilesForProject(proj2).firstOrNull() ?: emptyList()
        assertTrue(files2.isEmpty())
    }
}
