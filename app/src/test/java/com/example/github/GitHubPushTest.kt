package com.example.github

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GitHubConfigRepository
import com.example.data.ProjectFileRepository
import com.example.data.ProjectFileSystem
import com.example.ui.GitHubPushState
import com.example.ui.GitHubViewModel
import com.example.ui.GitHubProjectState
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GitHubPushTest {
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

    private suspend fun setupClonedProject(viewModel: GitHubViewModel, repoName: String) {
        viewModel.fetchRepositories()
        ShadowLooper.idleMainLooper()
        
        val state1 = viewModel.discoveryState.value as RepositoryDiscoveryState.RepositoriesLoaded
        val repo = state1.repositories.find { it.name == repoName }!!
        
        viewModel.selectRepository(repo)
        ShadowLooper.idleMainLooper()
        
        viewModel.connectRepository(force = true)
        
        for (i in 0..500) {
            ShadowLooper.idleMainLooper()
            if (viewModel.projectState.value is GitHubProjectState.Connected) break
            Thread.sleep(10)
        }
    }

    @Test
    fun `test no local changes`() = runBlocking {
        val projId = "proj-push-no-changes"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        val state = vm.pushState.value
        if (state !is GitHubPushState.NoChanges) {
            println("State was $state")
        }
        assertTrue(state is GitHubPushState.NoChanges)
        
        vm.commitAndPush("Test commit")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        assertTrue(vm.pushState.value is GitHubPushState.Error)
        assertEquals("No changes to commit", (vm.pushState.value as GitHubPushState.Error).message)
    }

    @Test
    fun `test new text file and successful push`() = runBlocking {
        val projId = "proj-push-new-text"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "new_file.txt", "Hello World")
        
        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.ChangesReady)
        val summary = (state as GitHubPushState.ChangesReady).summary
        assertEquals(1, summary.additions)
        assertEquals(0, summary.modifications)
        assertEquals(0, summary.deletions)
        assertEquals("new_file.txt", summary.changes[0].path)

        vm.commitAndPush("Add new file")
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        assertTrue(vm.pushState.value is GitHubPushState.Success)
        
        // Verify what was sent to GitHub
        val tree = githubService.lastCreatedTree
        assertNotNull(tree)
        assertEquals("abcdef123456", tree?.baseTree)
        assertEquals(1, tree?.tree?.size)
        assertEquals("new_file.txt", tree?.tree?.get(0)?.path)
        
        val commit = githubService.lastCreatedCommit
        assertNotNull(commit)
        assertEquals("Add new file", commit?.message)
        assertEquals("new-tree-sha", commit?.tree)
        
        val ref = githubService.lastUpdatedRef
        assertNotNull(ref)
        assertEquals("new-commit-sha", ref?.sha)
        
        val config = configRepository.getConfigForProject(projId).firstOrNull()
        assertEquals("new-commit-sha", config?.lastRemoteSha)
    }

    @Test
    fun `test modified text file`() = runBlocking {
        val projId = "proj-push-mod"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        val file = fileRepository.getFileByPath(projId, "README.md")!!
        fileRepository.updateFileContent(file.id, "Modified content")
        
        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.ChangesReady)
        val summary = (state as GitHubPushState.ChangesReady).summary
        assertEquals(1, summary.modifications)
        
        vm.commitAndPush("Modify readme")
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        assertTrue(vm.pushState.value is GitHubPushState.Success)
    }

    @Test
    fun `test deleted file`() = runBlocking {
        val projId = "proj-push-del"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        val file = fileRepository.getFileByPath(projId, "README.md")!!
        fileRepository.deleteFile(file.id)
        
        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.ChangesReady)
        val summary = (state as GitHubPushState.ChangesReady).summary
        assertEquals(1, summary.deletions)
        
        vm.commitAndPush("Delete readme")
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        assertTrue(vm.pushState.value is GitHubPushState.Success)
        val tree = githubService.lastCreatedTree
        assertEquals(1, tree?.tree?.size)
        assertEquals(null, tree?.tree?.get(0)?.sha) // deletion marker
    }

    @Test
    fun `test blank commit message`() = runBlocking {
        val projId = "proj-push-blank"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "new.txt", "123")
        vm.detectChanges()
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        vm.commitAndPush("   \n  ")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Error)
        assertEquals("Commit message cannot be empty", (state as GitHubPushState.Error).message)
    }

    @Test
    fun `test blob creation failure preserves local`() = runBlocking {
        val projId = "proj-push-blob-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "fail.txt", "fail-blob")
        vm.detectChanges()
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        vm.commitAndPush("Test")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Error)
        assertEquals("Blob creation failed", (state as GitHubPushState.Error).message)
        
        // Verify file is untouched
        assertNotNull(fileRepository.getFileByPath(projId, "fail.txt"))
    }

    @Test
    fun `test tree creation failure preserves local`() = runBlocking {
        val projId = "proj-push-tree-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "fail-tree", "123")
        vm.detectChanges()
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        vm.commitAndPush("Test")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Error)
        assertEquals("Tree creation failed", (state as GitHubPushState.Error).message)
    }

    @Test
    fun `test commit creation failure`() = runBlocking {
        val projId = "proj-push-commit-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "new.txt", "123")
        vm.detectChanges()
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        vm.commitAndPush("fail-commit")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Error)
        assertEquals("Commit creation failed", (state as GitHubPushState.Error).message)
    }

    @Test
    fun `test branch update failure`() = runBlocking {
        val projId = "proj-push-branch-fail"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "new.txt", "123")
        vm.detectChanges()
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        githubService.failNextRefUpdate = true
        vm.commitAndPush("Test")
        for (i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Error)
        assertEquals("Ref update failed", (state as GitHubPushState.Error).message)
    }

    @Test
    fun `test remote branch changed conflict`() = runBlocking {
        val projId = "proj-push-conflict"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        fileRepository.createFile(projId, "new.txt", "123")
        
        githubService.currentRefSha = "someone-else-pushed-this"
        
        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.Conflict)
    }

    @Test
    fun `test multiple simultaneous changes including binary`() = runBlocking {
        val projId = "proj-push-multi"
        val vm = createViewModel(projId)
        setupClonedProject(vm, "normal-repo")

        // modify readme
        val file = fileRepository.getFileByPath(projId, "README.md")!!
        fileRepository.updateFileContent(file.id, "Modified content")
        
        // delete src/main.kt
        val srcFile = fileRepository.getFileByPath(projId, "src/main.kt")!!
        fileRepository.deleteFile(srcFile.id)
        
        // add new binary
        fileRepository.createFileWithBytes(projId, "image.png", byteArrayOf(0, 1, 2))
        
        vm.detectChanges()
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }

        val state = vm.pushState.value
        assertTrue(state is GitHubPushState.ChangesReady)
        val summary = (state as GitHubPushState.ChangesReady).summary
        assertEquals(1, summary.additions)
        assertEquals(1, summary.modifications)
        assertEquals(1, summary.deletions)
        
        vm.commitAndPush("Multi change")
        for(i in 0..100) { ShadowLooper.idleMainLooper(); Thread.sleep(10) }
        
        assertTrue(vm.pushState.value is GitHubPushState.Success)
        val tree = githubService.lastCreatedTree
        assertEquals(3, tree?.tree?.size)
    }
}
